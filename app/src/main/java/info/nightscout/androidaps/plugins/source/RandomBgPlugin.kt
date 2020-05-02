package info.nightscout.androidaps.plugins.source

import android.content.Intent
import android.os.Handler
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
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.extensions.isRunningTest
import io.reactivex.rxkotlin.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.disposables.CompositeDisposable
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin

@Singleton
class RandomBgPlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val virtualPumpPlugin: VirtualPumpPlugin,
    private val buildHelper: BuildHelper,
    private val repository: AppRepository
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginName(R.string.randombg)
    .shortName(R.string.randombg_short)
    .description(R.string.description_source_randombg),
    aapsLogger, resourceHelper, injector
), BgSourceInterface {

    private val disposable = CompositeDisposable()
    private val loopHandler = Handler()
    private lateinit var refreshLoop: Runnable

    companion object {
        const val interval = 5L // minutes
    }

    init {
        refreshLoop = Runnable {
            handleNewData(Intent())
            loopHandler.postDelayed(refreshLoop, T.mins(interval).msecs())
        }
    }

    override fun advancedFilteringSupported(): Boolean {
        return false
    }

    override fun onStart() {
        super.onStart()
        loopHandler.postDelayed(refreshLoop, T.mins(interval).msecs())
    }

    override fun onStop() {
        super.onStop()
        disposable.clear()
        loopHandler.removeCallbacks(refreshLoop)
    }

    override fun specialEnableCondition(): Boolean {
        return isRunningTest() || virtualPumpPlugin.isEnabled(PluginType.PUMP) && buildHelper.isEngineeringMode()
    }

    override fun handleNewData(intent: Intent) {
        if (!isEnabled(PluginType.BGSOURCE)) return
        val min = 70
        val max = 190

        val cal = GregorianCalendar()
        val currentMinute = cal.get(Calendar.MINUTE) + (cal.get(Calendar.HOUR_OF_DAY) % 2) * 60
        val bgMgdl = min + ((max - min) + (max - min) * sin(currentMinute / 120.0 * 2 * PI))/2

        val glucoseValue = CgmSourceTransaction.TransactionGlucoseValue(
            timestamp = DateUtil.now(),
            value = bgMgdl,
            raw = bgMgdl,
            noise = 0.0,
            trendArrow = GlucoseValue.TrendArrow.NONE,
            sourceSensor = GlucoseValue.SourceSensor.RANDOM
        )
        disposable += repository.runTransactionForResult(CgmSourceTransaction(mutableListOf(glucoseValue), emptyList(), null)).subscribe()
    }
}
