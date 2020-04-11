package info.nightscout.androidaps.plugins.general.nsclient2

import android.content.Context
import android.text.Spanned
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.networking.nightscout.NightscoutService
import info.nightscout.androidaps.networking.nightscout.data.NightscoutCollection
import info.nightscout.androidaps.networking.nightscout.data.SetupState
import info.nightscout.androidaps.networking.nightscout.responses.PostEntryResponseType
import info.nightscout.androidaps.networking.nightscout.responses.id
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientNewLog
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NSClient2Plugin @Inject constructor(
    aapsLogger: AAPSLogger,
    resourceHelper: ResourceHelper,
    injector: HasAndroidInjector,
    private val context: Context,
    private val rxBus: RxBusWrapper,
    private val sp: SP,
    private val nightscoutService: NightscoutService
) : PluginBase(PluginDescription()
    .mainType(PluginType.GENERAL)
    .fragmentClass(NSClient2Fragment::class.java.name)
    .pluginName(R.string.nsclientinternal2)
    .shortName(R.string.nsclientinternal2_shortname)
    .preferencesId(R.xml.pref_nsclient2)
    .description(R.string.description_ns_client)
    , aapsLogger, resourceHelper, injector
) {

    private val listLog: MutableList<EventNSClientNewLog> = ArrayList()
    var paused = false

    private val _logLiveData: MutableLiveData<Spanned> = MutableLiveData(HtmlHelper.fromHtml(""))
    val logLiveData: LiveData<Spanned> = _logLiveData // Expose non-mutable form (avoid post from other classes)
    private val _statusLiveData: MutableLiveData<String> = MutableLiveData("")
    val statusLiveData: LiveData<String> = _statusLiveData // Expose non-mutable form (avoid post from other classes)

    private val compositeDisposable = CompositeDisposable() //TODO: once transformed to VM, clear! (atm plugins live forever)

    override fun onStart() {
        super.onStart()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {
        super.preprocessPreferences(preferenceFragment)
        if (Config.NSCLIENT) {
            val screenAdvancedSettings: PreferenceScreen? = preferenceFragment.findPreference(resourceHelper.gs(R.string.key_advancedsettings))
            screenAdvancedSettings?.removePreference(preferenceFragment.findPreference(resourceHelper.gs(R.string.key_statuslights_res_warning)))
            screenAdvancedSettings?.removePreference(preferenceFragment.findPreference(resourceHelper.gs(R.string.key_statuslights_res_critical)))
            screenAdvancedSettings?.removePreference(preferenceFragment.findPreference(resourceHelper.gs(R.string.key_statuslights_bat_warning)))
            screenAdvancedSettings?.removePreference(preferenceFragment.findPreference(resourceHelper.gs(R.string.key_statuslights_bat_critical)))
            screenAdvancedSettings?.removePreference(preferenceFragment.findPreference(resourceHelper.gs(R.string.key_show_statuslights)))
            screenAdvancedSettings?.removePreference(preferenceFragment.findPreference(resourceHelper.gs(R.string.key_show_statuslights_extended)))
        }
    }

    fun testConnection() = compositeDisposable.add(
        nightscoutService.testSetup().subscribeBy(
            onSuccess = {
                addToLog(EventNSClientNewLog("RESULT",
                    when (it) {
                        SetupState.Success  -> "SUCCESS!"
                        is SetupState.Error -> it.message
                    }
                ))
            },
            onError = { addToLog(EventNSClientNewLog("RESULT", "failure: ${it.message}")) })
    )

    fun exampleStatusCall() = compositeDisposable.add(
        nightscoutService.status().subscribeBy(
            onSuccess = { addToLog(EventNSClientNewLog("RESULT", "success: $it")) },
            onError = { addToLog(EventNSClientNewLog("RESULT", "failure: ${it.message}")) })
    )

    fun lastModifiedCall() = compositeDisposable.add(
        nightscoutService.lastModified().subscribeBy(
            onSuccess = { addToLog(EventNSClientNewLog("RESULT", "success: $it")) },
            onError = { addToLog(EventNSClientNewLog("RESULT", "failure: ${it.message}")) })
    )

    fun postGlucoseValueCall() {
        val glucoseValue = GlucoseValue()
        glucoseValue.timestamp = DateUtil.now()
        glucoseValue.value = Math.random() * 200 + 40
        compositeDisposable.add(
            nightscoutService.postGlucoseStatus(glucoseValue).subscribeBy(
                onSuccess = {
                    addToLog(EventNSClientNewLog("RESULT",
                        when (it) {
                            is PostEntryResponseType.Success -> "success: ${it.location?.id}"
                            is PostEntryResponseType.Failure -> "success: ${it.reason}"
                        }))
                },
                onError = { addToLog(EventNSClientNewLog("RESULT", "failure: ${it.message}")) })
        )
    }

    fun getEntriesCall() {
        compositeDisposable.add(
            nightscoutService.getByDate(NightscoutCollection.ENTRIES, DateUtil.now() - T.mins(20).msecs()).subscribeBy(
                onSuccess = { addToLog(EventNSClientNewLog("RESULT", "success: ${it.body()}")) },
                onError = { addToLog(EventNSClientNewLog("RESULT", "failure: ${it.message}")) })
        )
    }

    @Synchronized
    fun clearLog() {
        listLog.clear()
        _logLiveData.postValue(HtmlHelper.fromHtml(""))
    }

    @Synchronized
    private fun addToLog(ev: EventNSClientNewLog) {
        listLog.add(ev)
        // remove the first line if log is too large
        if (listLog.size >= Constants.MAX_LOG_LINES) {
            listLog.removeAt(0)
        }
        try {
            val newTextLog = StringBuilder()
            for (log in listLog) newTextLog.append(log.toPreparedHtml())
            _logLiveData.postValue(HtmlHelper.fromHtml(newTextLog.toString()))
        } catch (e: OutOfMemoryError) {
            ToastUtils.errorToast(context, "Out of memory!\nStop using this phone !!!")
        }
    }

    fun sync(s: String) {

    }

    fun fullSync(s: String) {

    }

    fun pause(newState: Boolean) {
        sp.putBoolean(R.string.key_nsclient_paused, newState)
        paused = newState
        rxBus.send(EventPreferenceChange(resourceHelper, R.string.key_nsclient_paused))
    }
}