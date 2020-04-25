package info.nightscout.androidaps.plugins.source

import android.content.Intent
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
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
import info.nightscout.androidaps.plugins.general.nsclient2.data.TrendArrowNightScout
import info.nightscout.androidaps.plugins.general.nsclient2.data.toEntity
import info.nightscout.androidaps.utils.XDripBroadcast
import io.reactivex.rxkotlin.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.disposables.CompositeDisposable
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PoctechPlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val repository: AppRepository,
    private val broadcastToXDrip: XDripBroadcast
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginName(R.string.poctech)
    .preferencesId(R.xml.pref_bgsource)
    .description(R.string.description_source_poctech),
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
        val data = bundle.getString("data")
        aapsLogger.debug(LTag.BGSOURCE, "Received Poctech Data $data")
        val jsonArray = JSONArray(data)
        aapsLogger.debug(LTag.BGSOURCE, "Received Poctech Data size:" + jsonArray.length())
        val glucoseValues = mutableListOf<CgmSourceTransaction.TransactionGlucoseValue>()
        for (i in 0 until jsonArray.length()) {
            val json = jsonArray.getJSONObject(i)
            glucoseValues += CgmSourceTransaction.TransactionGlucoseValue(
                timestamp = json.getLong("date"),
                value = json.getDouble("current") * (if (json.getString("units") == "mmol/L") Constants.MMOLL_TO_MGDL else 1.0),
                raw = json.getDouble("raw"),
                noise = null,
                trendArrow = TrendArrowNightScout.fromString(json.getString("direction")).toEntity(),
                sourceSensor = GlucoseValue.SourceSensor.POCTECH_NATIVE
            )
        }
        disposable += repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, emptyList(), null)).subscribe({
            it.forEach {
                broadcastToXDrip(it)
            }
        }, {
            aapsLogger.error(LTag.BGSOURCE, "Error while saving values from Tomato App", it)
        })
    }
}