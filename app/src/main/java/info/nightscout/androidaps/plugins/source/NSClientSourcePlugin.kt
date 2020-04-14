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
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSgv
import info.nightscout.androidaps.services.DataExchangeStore
import io.reactivex.rxkotlin.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NSClientSourcePlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val sp: SP,
    private val repository: AppRepository
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginName(R.string.nsclientbg)
    .description(R.string.description_source_ns_client),
    aapsLogger, resourceHelper, injector
), BgSourceInterface {

    private val disposable = CompositeDisposable()

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    private var lastBGTimeStamp: Long = 0
    private var isAdvancedFilteringEnabled = false

    override fun advancedFilteringSupported(): Boolean {
        return isAdvancedFilteringEnabled
    }

    // Not used, data goes through DataExchangeStore
    override fun handleNewData(intent: Intent) {}

    fun handleNewData() {
        if (!isEnabled(PluginType.BGSOURCE) && !sp.getBoolean(R.string.key_ns_autobackfill, true)) return
        val glucoseValues = mutableListOf<CgmSourceTransaction.TransactionGlucoseValue?>()
        DataExchangeStore.nsclientSgvs?.let {
            aapsLogger.debug(LTag.BGSOURCE, "Received NS Data: $it")
            for (i in 0 until it.length()) {
                glucoseValues += it.getJSONObject(i).toGlucoseValue()
            }
        }
        disposable += repository.runTransaction(CgmSourceTransaction(glucoseValues.filterNotNull(), emptyList(), null)).subscribe({}, {
            aapsLogger.error(LTag.BGSOURCE, "Error while saving values from Nightscout App", it)
        })
        // Objectives 0
        sp.putBoolean(R.string.key_ObjectivesbgIsAvailableInNS, true)
    }

    private fun JSONObject.toGlucoseValue() = NSSgv(this).run {
        val source = getString("device")
        val timestamp = mills
        val value = mgdl?.toDouble()
        val raw = unfiltered?.toDouble()
        val noise = noise?.toDouble()
        val trendArrow = GlucoseValue.TrendArrow.fromString(direction)
        val nightScout = id
        val sourceSensor = GlucoseValue.SourceSensor.fromString(source)
        detectSource(source, mills)
        if (timestamp != null && value != null) {
            CgmSourceTransaction.TransactionGlucoseValue(
                timestamp = timestamp,
                value = value,
                raw = raw,
                noise = noise,
                trendArrow = trendArrow,
                nightscoutId = nightScout,
                sourceSensor = sourceSensor
            )
        } else null
    }

    private fun detectSource(source: String, timeStamp: Long) {
        if (timeStamp > lastBGTimeStamp) {
            isAdvancedFilteringEnabled = source.contains("G5 Native") || source.contains("G6 Native") || source.contains("AndroidAPS-Dexcom")
            lastBGTimeStamp = timeStamp
        }
    }
}