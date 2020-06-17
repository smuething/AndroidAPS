package info.nightscout.androidaps.insight

import android.content.Context
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.insight.dagger.InsightPumpComponent
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.utils.resources.ResourceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Singleton
class InsightPlugin @Inject internal constructor(
    injector: HasAndroidInjector,
    logger: AAPSLogger,
    resourceHelper: ResourceHelper,
    commandQueue: CommandQueueProvider,
    private val context: Context,
    private val createInsightPumpComponent: InsightPumpComponent.Factory,
    private val pairingDataStorage: PairingDataStorage
) : PumpPluginBase(pluginDescription, injector, logger, resourceHelper, commandQueue), CoroutineScope, PumpInterface by DummyPumpInterface(injector) {

    override lateinit var coroutineContext: CoroutineContext
        private set
    private var insightPump: InsightPump? = null

    override fun onStart() {
        super.onStart()
        coroutineContext = SupervisorJob() + Dispatchers.Main
        if (pairingDataStorage.isPaired) {
            insightPump = createInsightPumpComponent(coroutineContext, pairingDataStorage.macAddress, pairingDataStorage.pairingDataParameter).insightPump().apply {
                launch { setPairingDataCallback(pairingDataStorage, false) }
            }
        }
    }

    override fun onStop() {
        cancel()
        insightPump = null
        super.onStop()
    }

    internal fun takeOverPump(insightPump: InsightPump) {
        this.insightPump?.close()
        this.insightPump = insightPump
        launch {
            insightPump.setPairingDataCallback(pairingDataStorage, true)
            pairingDataStorage.macAddress = insightPump.macAddress
            pairingDataStorage.isPaired = true
        }
    }

    companion object {
        private val pluginDescription = PluginDescription()
            .pluginName(R.string.accu_chek_insight)
            .shortName(R.string.sight)
            .mainType(PluginType.PUMP)
            .description(R.string.accu_chek_insight_description)
        private val pumpDescriptionBacking = PumpDescription(PumpType.AccuChekInsightBluetooth)
    }
}