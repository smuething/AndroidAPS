package info.nightscout.androidaps.plugins.source

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.RequestDexcomPermissionActivity
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.entities.GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE
import info.nightscout.androidaps.database.entities.GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE
import info.nightscout.androidaps.database.entities.GlucoseValue.SourceSensor.DEXCOM_NATIVE_UNKNOWN
import info.nightscout.androidaps.database.transactions.CgmSourceTransaction
import info.nightscout.androidaps.interfaces.BgSourceInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.nsclient2.data.TrendArrowNightScout
import info.nightscout.androidaps.plugins.general.nsclient2.data.toEntity
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.XDripBroadcast
import io.reactivex.rxkotlin.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DexcomPlugin @Inject constructor(
    injector: HasAndroidInjector,
    private val sp: SP,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val repository: AppRepository,
    private val dexcomMediator: DexcomMediator,
    private val broadcastToXDrip: XDripBroadcast
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginName(R.string.dexcom_app_patched)
    .shortName(R.string.dexcom_short)
    .preferencesId(R.xml.pref_bgsourcedexcom)
    .description(R.string.description_source_dexcom)
    .setDefault(),
    aapsLogger, resourceHelper, injector
), BgSourceInterface {

    private val disposable = CompositeDisposable()

    override fun advancedFilteringSupported(): Boolean {
        return true
    }

    override fun onStart() {
        super.onStart()
        dexcomMediator.requestPermissionIfNeeded()
    }

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    override fun handleNewData(intent: Intent) {
        if (!isEnabled(PluginType.BGSOURCE)) return
        val sensorType = intent.getStringExtra("sensorType") ?: ""
        val sourceSensor = when (sensorType) {
            "G6" -> DEXCOM_G6_NATIVE
            "G5" -> DEXCOM_G5_NATIVE
            else -> DEXCOM_NATIVE_UNKNOWN
        }
        val glucoseValuesBundle = intent.getBundleExtra("glucoseValues")!!
        val glucoseValues = mutableListOf<CgmSourceTransaction.TransactionGlucoseValue>()
        for (i in 0 until glucoseValuesBundle.size()) {
            val glucoseValueBundle = glucoseValuesBundle.getBundle(i.toString())!!
            glucoseValues += CgmSourceTransaction.TransactionGlucoseValue(
                timestamp = glucoseValueBundle.getLong("timestamp") * 1000,
                value = glucoseValueBundle.getInt("glucoseValue").toDouble(),
                noise = null,
                raw = null,
                trendArrow = TrendArrowNightScout.fromString(glucoseValueBundle.getString("trendArrow")!!).toEntity(),
                sourceSensor = sourceSensor
            )
        }
        val meters = intent.getBundleExtra("meters")
        val calibrations = mutableListOf<CgmSourceTransaction.Calibration>()
        for (i in 0 until meters.size()) {
            meters.getBundle(i.toString())?.let {
                val timestamp = it.getLong("timestamp") * 1000
                val now = DateUtil.now()
                if (timestamp > now - T.months(1).msecs() && timestamp < now) {
                    calibrations.add(CgmSourceTransaction.Calibration(it.getLong("timestamp") * 1000,
                        it.getInt("meterValue").toDouble()))
                }
            }
        }
        val sensorStartTime = if (sp.getBoolean(R.string.key_dexcom_lognssensorchange, false) && intent.hasExtra("sensorInsertionTime")) {
            intent.getLongExtra("sensorInsertionTime", 0) * 1000
        } else {
            null
        }
        disposable += repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, calibrations, sensorStartTime)).subscribe({ savedValues ->
            savedValues.forEach {
                broadcastToXDrip(it)
            }
        }, {
            aapsLogger.error(LTag.BGSOURCE, "Error while saving values from Dexcom App", it)
        })
    }

    companion object {
        private val PACKAGE_NAMES = arrayOf("com.dexcom.cgm.region1.mgdl", "com.dexcom.cgm.region1.mmol",
            "com.dexcom.cgm.region2.mgdl", "com.dexcom.cgm.region2.mmol",
            "com.dexcom.g6.region1.mmol", "com.dexcom.g6.region2.mgdl",
            "com.dexcom.g6.region3.mgdl", "com.dexcom.g6.region3.mmol")
        const val PERMISSION = "com.dexcom.cgm.EXTERNAL_PERMISSION"
    }

    class DexcomMediator @Inject constructor(val context: Context) {
        fun requestPermissionIfNeeded() {
            if (ContextCompat.checkSelfPermission(context, PERMISSION) != PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(context, RequestDexcomPermissionActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }

        fun findDexcomPackageName(): String? {
            val packageManager = context.packageManager
            for (packageInfo in packageManager.getInstalledPackages(0)) {
                if (PACKAGE_NAMES.contains(packageInfo.packageName)) return packageInfo.packageName
            }
            return null
        }
    }
}