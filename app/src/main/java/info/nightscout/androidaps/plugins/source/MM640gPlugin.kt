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
import info.nightscout.androidaps.plugins.general.nsclient2.data.TrendArrowNightScout
import info.nightscout.androidaps.plugins.general.nsclient2.data.toEntity
import info.nightscout.androidaps.utils.XDripBroadcast
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MM640gPlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val repository: AppRepository,
    private val broadcastToXDrip: XDripBroadcast
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginName(R.string.MM640g)
    .description(R.string.description_source_mm640g),
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
        val collection = bundle.getString("collection") ?: return
        if (collection == "entries") {
            val data = bundle.getString("data")
            aapsLogger.debug(LTag.BGSOURCE, "Received MM640g Data: $data")
            if (data != null && data.isNotEmpty()) {
                val glucoseValues = mutableListOf<CgmSourceTransaction.TransactionGlucoseValue>()
                val jsonArray = JSONArray(data)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    when (val type = jsonObject.getString("type")) {
                        "sgv" -> {
                            glucoseValues += CgmSourceTransaction.TransactionGlucoseValue(
                                timestamp = jsonObject.getLong("date"),
                                value = jsonObject.getDouble("sgv"),
                                raw = jsonObject.getDouble("sgv"),
                                noise = null,
                                trendArrow = TrendArrowNightScout.fromString(jsonObject.getString("direction")).toEntity(),
                                sourceSensor = GlucoseValue.SourceSensor.MM_600_SERIES
                            )
                        }

                        else  -> aapsLogger.debug(LTag.BGSOURCE, "Unknown entries type: $type")
                    }
                }

                disposable += repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, emptyList(), null)).subscribe({
                    it.forEach {
                        broadcastToXDrip(it)
                    }
                }, {
                    aapsLogger.error(LTag.BGSOURCE, "Error while saving values from MM640G (alike) App", it)
                })
            }
        }
    }
}