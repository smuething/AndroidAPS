package info.nightscout.androidaps.plugins.general.nsclient2

import android.content.Context
import android.text.Spanned
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.events.EventChargingState
import info.nightscout.androidaps.events.EventNetworkChange
import info.nightscout.androidaps.events.EventNewBG
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.networking.nightscout.NightscoutService
import info.nightscout.androidaps.networking.nightscout.data.NightscoutCollection
import info.nightscout.androidaps.networking.nightscout.data.SetupState
import info.nightscout.androidaps.networking.nightscout.responses.PostEntryResponseType
import info.nightscout.androidaps.networking.nightscout.responses.StatusResponse
import info.nightscout.androidaps.networking.nightscout.responses.id
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientNewLog
import info.nightscout.androidaps.receivers.ReceiverStatusStore
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.alertDialogs.ErrorDialog
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
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
    private val receiverStatusStore: ReceiverStatusStore,
    private val nightscoutService: NightscoutService,
    private val fabricPrivacy: FabricPrivacy
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

    var permissions: StatusResponse? = null // grabbed permissions

    private val _logLiveData: MutableLiveData<Spanned> = MutableLiveData(HtmlHelper.fromHtml(""))
    val logLiveData: LiveData<Spanned> = _logLiveData // Expose non-mutable form (avoid post from other classes)
    private val _statusLiveData: MutableLiveData<String> = MutableLiveData("")
    val statusLiveData: LiveData<String> = _statusLiveData // Expose non-mutable form (avoid post from other classes)

    private val disposable = CompositeDisposable() //TODO: once transformed to VM, clear! (atm plugins live forever)

    override fun onStart() {
        super.onStart()
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ event ->
                if (event.isChanged(resourceHelper, R.string.key_ns_wifionly) ||
                    event.isChanged(resourceHelper, R.string.key_ns_wifi_ssids) ||
                    event.isChanged(resourceHelper, R.string.key_ns_allowroaming)) {
                    receiverStatusStore.updateNetworkStatus()
                    commAllowed()
                } else if (event.isChanged(resourceHelper, R.string.key_ns_chargingonly)) {
                    receiverStatusStore.broadcastChargingState()
                    commAllowed()
                }
                if (event.isChanged(resourceHelper, R.string.key_ns_cgm)) {
                    // TODO
                }
            }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventChargingState::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ sync("EventChargingState") }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventNetworkChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ sync("EventNetworkChange") }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventNewBG::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ sync("EventNewBG") }, { fabricPrivacy.logException(it) })

        receiverStatusStore.updateNetworkStatus() // broadcast status to be catched for initial sync
    }

    override fun onStop() {
        super.onStop()
        disposable.clear()
    }

    override fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {
        super.preprocessPreferences(preferenceFragment)
        if (Config.NSCLIENT) { // TODO needed?
            val screenAdvancedSettings: PreferenceScreen? = preferenceFragment.findPreference(resourceHelper.gs(R.string.key_advancedsettings))
            screenAdvancedSettings?.removePreference(preferenceFragment.findPreference(resourceHelper.gs(R.string.key_statuslights_res_warning)))
            screenAdvancedSettings?.removePreference(preferenceFragment.findPreference(resourceHelper.gs(R.string.key_statuslights_res_critical)))
            screenAdvancedSettings?.removePreference(preferenceFragment.findPreference(resourceHelper.gs(R.string.key_statuslights_bat_warning)))
            screenAdvancedSettings?.removePreference(preferenceFragment.findPreference(resourceHelper.gs(R.string.key_statuslights_bat_critical)))
            screenAdvancedSettings?.removePreference(preferenceFragment.findPreference(resourceHelper.gs(R.string.key_show_statuslights)))
            screenAdvancedSettings?.removePreference(preferenceFragment.findPreference(resourceHelper.gs(R.string.key_show_statuslights_extended)))

            val cgmData = preferenceFragment.findPreference(resourceHelper.gs(R.string.key_ns_cgm)) as ListPreference?
            cgmData?.value = "PULL"
            cgmData?.isEnabled = false
        }
        // test connection from preferences
        val testLogin: Preference? = preferenceFragment.findPreference(resourceHelper.gs(R.string.key_nsclient_test_login))
        testLogin?.setOnPreferenceClickListener {
            preferenceFragment.context?.let { context -> testConnection(context) }
            false
        }
    }

    private fun testConnection(context: Context) = disposable.add(
        nightscoutService
            .testConnection()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onSuccess = {
                    when (it) {
                        SetupState.Success  -> OKDialog.show(context, "", resourceHelper.gs(R.string.connection_verified), null)
                        is SetupState.Error -> ErrorDialog.showError(context, resourceHelper.gs(R.string.error), it.message)
                    }
                },
                onError = {
                    it.message?.let { message -> ErrorDialog.showError(context, resourceHelper.gs(R.string.error), message) }
                })
    )

    fun lastModifiedCall() = disposable.add(
        nightscoutService.lastModified().subscribeBy(
            onSuccess = { addToLog(EventNSClientNewLog("RESULT", "success: $it")) },
            onError = { addToLog(EventNSClientNewLog("RESULT", "failure: ${it.message}")) })
    )

    fun postGlucoseValueCall() {
        val glucoseValue = GlucoseValue()
        glucoseValue.timestamp = DateUtil.now()
        glucoseValue.value = Math.random() * 200 + 40
        disposable.add(
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
        disposable.add(
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
        if (!commAllowed()) return
    }

    fun fullSync(s: String) {
        if (!commAllowed()) return

    }

    fun pause(newState: Boolean) {
        sp.putBoolean(R.string.key_nsclient_paused, newState)
        paused = newState
        rxBus.send(EventPreferenceChange(resourceHelper, R.string.key_nsclient_paused))
    }

    fun commAllowed(): Boolean {
        val eventNetworkChange: EventNetworkChange = receiverStatusStore.lastNetworkEvent
            ?: return false

        val chargingOnly = sp.getBoolean(R.string.key_ns_chargingonly, false)
        if (!receiverStatusStore.isCharging && chargingOnly) {
            _statusLiveData.postValue(resourceHelper.gs(R.string.notcharging))
            return false
        }

        if (!receiverStatusStore.isConnected) {
            _statusLiveData.postValue(resourceHelper.gs(R.string.disconnected))
            return false
        }
        val wifiOnly = sp.getBoolean(R.string.key_ns_wifionly, false)
        val allowedSSIDs = sp.getString(R.string.key_ns_wifi_ssids, "")
        val allowRoaming = sp.getBoolean(R.string.key_ns_allowroaming, true)
        if (wifiOnly && !receiverStatusStore.isWifiConnected) {
            _statusLiveData.postValue(resourceHelper.gs(R.string.wifinotconnected))
            return false
        }
        if (wifiOnly && allowedSSIDs.trim { it <= ' ' }.isNotEmpty()) {
            if (!allowedSSIDs.contains(eventNetworkChange.connectedSsid()) && !allowedSSIDs.contains(eventNetworkChange.ssid)) {
                _statusLiveData.postValue(resourceHelper.gs(R.string.ssidnotmatch))
                return false
            }
        }
        if (wifiOnly && receiverStatusStore.isWifiConnected) {
            _statusLiveData.postValue(resourceHelper.gs(R.string.connected))
            return true
        }
        if (!allowRoaming && eventNetworkChange.roaming) {
            _statusLiveData.postValue(resourceHelper.gs(R.string.roamingnotallowed))
            return false
        }

        _statusLiveData.postValue(resourceHelper.gs(R.string.connected))
        return true
    }
}