package info.nightscout.androidaps.plugins.general.nsclient2

import android.content.Context
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
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.events.EventChargingState
import info.nightscout.androidaps.events.EventNetworkChange
import info.nightscout.androidaps.events.EventNewBG
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.networking.nightscout.NightscoutService
import info.nightscout.androidaps.networking.nightscout.data.NightscoutCollection
import info.nightscout.androidaps.networking.nightscout.data.SetupState
import info.nightscout.androidaps.networking.nightscout.responses.ApiPermissions
import info.nightscout.androidaps.networking.nightscout.responses.PostEntryResponseType
import info.nightscout.androidaps.networking.nightscout.responses.ResponseCode
import info.nightscout.androidaps.networking.nightscout.responses.id
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientNewLog
import info.nightscout.androidaps.plugins.general.nsclient2.events.EventNSClientFullSync
import info.nightscout.androidaps.plugins.general.nsclient2.events.EventNSClientSync
import info.nightscout.androidaps.receivers.ReceiverStatusStore
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.alertDialogs.ErrorDialog
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.PreferenceBoolean
import info.nightscout.androidaps.utils.sharedPreferences.PreferenceLong
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
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
    private val receiverStatusStore: ReceiverStatusStore,
    private val nightscoutService: NightscoutService,
    private val fabricPrivacy: FabricPrivacy,
    private val aapsSchedulers: AapsSchedulers,
    private val repository: AppRepository
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
    private val keyNSClientPaused = PreferenceBoolean(R.string.key_nsclient_paused, false, sp, resourceHelper, rxBus)

    val lastProcessedId = Array(NightscoutCollection.values().size) { i -> PreferenceLong("lastProcessedId_" + NightscoutCollection.values()[i].collection, 0L, sp, rxBus) }
    val receiveTimestamp = Array(NightscoutCollection.values().size) { i -> PreferenceLong("receiveTimestamp_" + NightscoutCollection.values()[i].collection, 0L, sp, rxBus) }

    var permissions: ApiPermissions? = null // grabbed permissions

    private val _liveData: MutableLiveData<NSClient2LiveData> = MutableLiveData(NSClient2LiveData.Log(HtmlHelper.fromHtml("")))
    val liveData: LiveData<NSClient2LiveData> = _liveData // Expose non-mutable form (avoid post from other classes)

    private val disposable = CompositeDisposable() //TODO: once transformed to VM, clear! (atm plugins live forever)

    override fun onStart() {
        super.onStart()
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
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
                for (c in arrayOf(R.string.key_ns_cgm,
                    R.string.key_ns_food,
                    R.string.key_ns_profile,
                    R.string.key_ns_insulin,
                    R.string.key_ns_carbs,
                    R.string.key_ns_careportal,
                    R.string.key_ns_settings)) {
                    if (event.isChanged(resourceHelper, c))
                        permissions = null // force new permissions read
                }
            }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventChargingState::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sync("EventChargingState") }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventNetworkChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sync("EventNetworkChange") }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventNewBG::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sync("EventNewBG") }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventNSClientSync::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sync("EventNSClientSync") }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventNSClientFullSync::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ fullSync("EventNSClientFullSync") }, { fabricPrivacy.logException(it) })

        receiverStatusStore.updateNetworkStatus() // broadcast status to be caught for initial sync
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

    private fun readPermissions(ok: () -> Unit) = disposable.add(
        nightscoutService
            .testConnection()
            .observeOn(aapsSchedulers.io)
            .subscribeBy(
                onSuccess = {
                    when (it) {
                        is SetupState.Success -> {
                            permissions = it.permissions
                            ok()
                        }

                        is SetupState.Error   -> permissions = null
                    }
                },
                onError = { permissions = null })
    )

    private fun testConnection(context: Context) = disposable.add(
        nightscoutService
            .testConnection()
            .observeOn(aapsSchedulers.main)
            .subscribeBy(
                onSuccess = {
                    when (it) {
                        is SetupState.Success -> {
                            permissions = it.permissions
                            OKDialog.show(context, "", resourceHelper.gs(R.string.connection_verified), null)
                        }

                        is SetupState.Error   -> ErrorDialog.showError(context, resourceHelper.gs(R.string.error), it.message)
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
        _liveData.postValue(NSClient2LiveData.Log(HtmlHelper.fromHtml("")))
    }

    @Synchronized
    private fun addToLog(ev: EventNSClientNewLog) {
        aapsLogger.debug(LTag.NSCLIENT, ev.toString())
        listLog.add(ev)
        // remove the first line if log is too large
        if (listLog.size >= Constants.MAX_LOG_LINES) {
            listLog.removeAt(0)
        }
        try {
            val newTextLog = StringBuilder()
            for (log in listLog) newTextLog.append(log.toPreparedHtml())
            _liveData.postValue(NSClient2LiveData.Log(HtmlHelper.fromHtml(newTextLog.toString())))
        } catch (e: OutOfMemoryError) {
            ToastUtils.errorToast(context, "Out of memory!\nStop using this phone !!!")
        }
    }

    fun sync(from: String) {
        aapsLogger.debug(LTag.NSCLIENT, "Running sync from $from")
        if (keyNSClientPaused.get()) {
            _liveData.postValue(NSClient2LiveData.State(resourceHelper.gs(R.string.paused)))
            return
        }
        if (!commAllowed()) return
        if (permissions == null) readPermissions(::doSync)
        else doSync()
    }

    @Synchronized
    private fun doSync() {
        val list = repository
            .getDataFromId(lastProcessedId[NightscoutCollection.ENTRIES.ordinal].get())
            .blockingGet()

        for (gv in list) {
            aapsLogger.debug(LTag.NSCLIENT, "Uploading $gv")

            val httpResult = nightscoutService.postGlucoseStatus(gv).blockingGet()
            gv.interfaceIDs.nightscoutId = httpResult.location?.id
            when (httpResult.code) {
                ResponseCode.RECORD_CREATED -> {
                    lastProcessedId[NightscoutCollection.ENTRIES.ordinal].store(gv.id)
                    repository.updateGlucoseValue(gv)
                    addToLog(EventNSClientNewLog("RESULT", "added: ${httpResult.location?.id}"))
                }

                ResponseCode.RECORD_EXISTS  -> {
                    lastProcessedId[NightscoutCollection.ENTRIES.ordinal].store(gv.id)
                    addToLog(EventNSClientNewLog("RESULT", "existing: ${httpResult.location?.id}"))
                }
                else                        -> {
                    addToLog(EventNSClientNewLog("ERROR", "${httpResult.code}"))
                    return
                }
            }
        }
    }

    private fun fullSync(from: String) {
        aapsLogger.debug(LTag.NSCLIENT, "Running FULL sync from $from")
        // reset upload timestamp
        for (c in NightscoutCollection.values())
            lastProcessedId[c.ordinal].store(0)
        sync(from)
    }

    fun pause(newState: Boolean) {
        keyNSClientPaused.store(newState)
        if (newState) _liveData.postValue(NSClient2LiveData.State(resourceHelper.gs(R.string.paused)))
        else commAllowed()
    }

    private fun commAllowed(): Boolean {
        val eventNetworkChange: EventNetworkChange = receiverStatusStore.lastNetworkEvent
            ?: return false

        val chargingOnly = sp.getBoolean(R.string.key_ns_chargingonly, false)
        if (!receiverStatusStore.isCharging && chargingOnly) {
            _liveData.postValue(NSClient2LiveData.State(resourceHelper.gs(R.string.notcharging)))
            return false
        }

        if (!receiverStatusStore.isConnected) {
            _liveData.postValue(NSClient2LiveData.State(resourceHelper.gs(R.string.disconnected)))
            return false
        }
        val wifiOnly = sp.getBoolean(R.string.key_ns_wifionly, false)
        val allowedSSIDs = sp.getString(R.string.key_ns_wifi_ssids, "")
        val allowRoaming = sp.getBoolean(R.string.key_ns_allowroaming, true)
        if (wifiOnly && !receiverStatusStore.isWifiConnected) {
            _liveData.postValue(NSClient2LiveData.State(resourceHelper.gs(R.string.wifinotconnected)))
            return false
        }
        if (wifiOnly && allowedSSIDs.trim { it <= ' ' }.isNotEmpty()) {
            if (!allowedSSIDs.contains(eventNetworkChange.connectedSsid()) && !allowedSSIDs.contains(eventNetworkChange.ssid)) {
                _liveData.postValue(NSClient2LiveData.State(resourceHelper.gs(R.string.ssidnotmatch)))
                return false
            }
        }
        if (wifiOnly && receiverStatusStore.isWifiConnected) {
            _liveData.postValue(NSClient2LiveData.State(resourceHelper.gs(R.string.ready)))
            return true
        }
        if (!allowRoaming && eventNetworkChange.roaming) {
            _liveData.postValue(NSClient2LiveData.State(resourceHelper.gs(R.string.roamingnotallowed)))
            return false
        }

        _liveData.postValue(NSClient2LiveData.State(resourceHelper.gs(R.string.ready)))
        return true
    }
}