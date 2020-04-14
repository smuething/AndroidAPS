package info.nightscout.androidaps.plugins.source

import android.content.Intent
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.transactions.CgmSourceTransaction
import info.nightscout.androidaps.interfaces.BgSourceInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.GlucoseValueUploader
import info.nightscout.androidaps.utils.XDripBroadcast
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TomatoPlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val repository: AppRepository,
    private val broadcastToXDrip: XDripBroadcast,
    private val uploadtoNS: GlucoseValueUploader
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginName(R.string.tomato)
    .preferencesId(R.xml.pref_bgsource)
    .shortName(R.string.tomato_short)
    .description(R.string.description_source_tomato),
    aapsLogger, resourceHelper, injector
), BgSourceInterface {

    private val disposable = CompositeDisposable()

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    override fun advancedFilteringSupported(): Boolean {
        return false
    }

    override fun handleNewData(intent: Intent) {
        if (!isEnabled(PluginType.BGSOURCE)) return
        val bundle = intent.extras ?: return
        val glucoseValue = CgmSourceTransaction.TransactionGlucoseValue(
            timestamp = bundle.getLong("com.fanqies.tomatofn.Extras.Time"),
            value = bundle.getDouble("com.fanqies.tomatofn.Extras.BgEstimate"),
            raw = null,
            noise = null,
            trendArrow = GlucoseValue.TrendArrow.NONE,
            sourceSensor = GlucoseValue.SourceSensor.LIBRE_1_TOMATO
        )
        disposable += repository.runTransactionForResult(CgmSourceTransaction(listOf(glucoseValue), emptyList(), null)).subscribe({
            it.forEach {
                broadcastToXDrip(it)
                uploadtoNS(it, "AndroidAPS-Tomato")
            }
        }, {
            aapsLogger.error(LTag.BGSOURCE, "Error while saving values from Tomato App", it)
        })
    }
}