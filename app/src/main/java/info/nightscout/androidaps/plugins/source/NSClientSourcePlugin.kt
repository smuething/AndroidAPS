package info.nightscout.androidaps.plugins.source

import android.content.Intent
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.interfaces.BgSourceInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
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

    // Not used, data goes through NSClient2Plugin
    override fun handleNewData(intent: Intent) {}

    fun detectSource(glucoseValue: GlucoseValue) {
        if (glucoseValue.timestamp > lastBGTimeStamp) {
            isAdvancedFilteringEnabled = arrayOf(
                GlucoseValue.SourceSensor.DEXCOM_NATIVE_UNKNOWN,
                GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE,
                GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE,
                GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE_XDRIP,
                GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE_XDRIP
            ).any { it == glucoseValue.sourceSensor }
            lastBGTimeStamp = glucoseValue.timestamp
        }
    }
}