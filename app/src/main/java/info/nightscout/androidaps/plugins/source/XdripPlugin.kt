package info.nightscout.androidaps.plugins.source

import android.content.Intent
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.transactions.CgmSourceTransaction
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.interfaces.BgSourceInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.BundleLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.services.Intents
import info.nightscout.androidaps.utils.determineSourceSensor
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.toTrendArrow
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XdripPlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val repository: AppRepository
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginName(R.string.xdrip)
    .description(R.string.description_source_xdrip),
    aapsLogger, resourceHelper, injector
), BgSourceInterface {

    private val disposable = CompositeDisposable()
    private var advancedFiltering = false

    override fun advancedFilteringSupported(): Boolean {
        return advancedFiltering
    }

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    override fun handleNewData(intent: Intent) {
        if (!isEnabled(PluginType.BGSOURCE)) return
        try {
            val bundle = intent.extras ?: return
            aapsLogger.debug(LTag.BGSOURCE, "Received xDrip data: " + BundleLogger.log(intent.extras))
            val source = bundle.getString(Intents.XDRIP_DATA_SOURCE_DESCRIPTION, "no Source specified")
            setSource(source)
            val glucoseValue = CgmSourceTransaction.GlucoseValue(
                timestamp = bundle.getLong(Intents.EXTRA_TIMESTAMP),
                value = bundle.getDouble(Intents.EXTRA_BG_ESTIMATE),
                raw = bundle.getDouble(Intents.EXTRA_RAW),
                noise = null,
                trendArrow = bundle.getString(Intents.EXTRA_BG_SLOPE_NAME)!!.toTrendArrow(),
                sourceSensor = source.determineSourceSensor()
            )
            disposable += repository.runTransaction(CgmSourceTransaction(listOf(glucoseValue), emptyList(), null)).subscribe({}, {
                aapsLogger.error(LTag.BGSOURCE, "Error while saving values from xDrip", it)
            })
        } catch (e: Throwable) {
            aapsLogger.error(LTag.BGSOURCE, "Error while processing intent from xDrip App", e)
        }
    }

    private fun setSource(source: String) {
        advancedFiltering = source.contains("G5 Native") || source.contains("G6 Native")
    }
}