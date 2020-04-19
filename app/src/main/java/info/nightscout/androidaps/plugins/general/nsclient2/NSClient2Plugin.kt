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
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.database.transactions.InsertGlucoseValueTransaction
import info.nightscout.androidaps.database.transactions.InsertTemporaryTargetAndCancelCurrentTransaction
import info.nightscout.androidaps.database.transactions.UpdateGlucoseValueTransaction
import info.nightscout.androidaps.database.transactions.UpdateTemporaryTargetTransaction
import info.nightscout.androidaps.events.EventChargingState
import info.nightscout.androidaps.events.EventNetworkChange
import info.nightscout.androidaps.events.EventNewBG
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.networking.nightscout.NightscoutServiceWrapper
import info.nightscout.androidaps.networking.nightscout.data.NightscoutCollection
import info.nightscout.androidaps.networking.nightscout.data.SetupState
import info.nightscout.androidaps.networking.nightscout.exceptions.BadInputDataException
import info.nightscout.androidaps.networking.nightscout.requests.EntryResponseBody
import info.nightscout.androidaps.networking.nightscout.requests.toGlucoseValue
import info.nightscout.androidaps.networking.nightscout.requests.toTemporaryTarget
import info.nightscout.androidaps.networking.nightscout.responses.ApiPermissions
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientNewLog
import info.nightscout.androidaps.plugins.general.nsclient2.events.EventNSClientFullSync
import info.nightscout.androidaps.plugins.general.nsclient2.events.EventNSClientSync
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.source.NSClientSourcePlugin
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
import info.nightscout.androidaps.utils.sharedPreferences.PreferenceString
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.combineLatest
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class NSClient2Plugin @Inject constructor(
    aapsLogger: AAPSLogger,
    resourceHelper: ResourceHelper,
    injector: HasAndroidInjector,
    private val context: Context,
    protected val rxBus: RxBusWrapper,
    protected val sp: SP,
    private val receiverStatusStore: ReceiverStatusStore,
    protected val nightscoutServiceWrapper: NightscoutServiceWrapper,
    private val fabricPrivacy: FabricPrivacy,
    private val aapsSchedulers: AapsSchedulers,
    protected val repository: AppRepository,
    private val nsClientSourcePlugin: NSClientSourcePlugin
) : PluginBase(PluginDescription()
    .mainType(PluginType.GENERAL)
    .fragmentClass(NSClient2Fragment::class.java.name)
    .pluginName(R.string.nsclientinternal2)
    .shortName(R.string.nsclientinternal2_shortname)
    .preferencesId(R.xml.pref_nsclient2)
    .description(R.string.description_ns_client),
    aapsLogger, resourceHelper, injector
) {

    private val listLog: MutableList<EventNSClientNewLog> = ArrayList()
    private val keyNSClientPaused = PreferenceBoolean(R.string.key_nsclient_paused, false, sp, resourceHelper, rxBus)

    protected val lastProcessedId = NightscoutCollection.values().map { it to PreferenceLong("lastProcessedId_${it.name}", 0L, sp, rxBus) }.toMap()
    protected val receiveTimestamp = NightscoutCollection.values().map { it to PreferenceLong("receiveTimestamp_${it.name}", 0L, sp, rxBus) }.toMap()

    var permissions: ApiPermissions? = null // grabbed permissions

    private val _liveData: MutableLiveData<NSClient2LiveData> = MutableLiveData(NSClient2LiveData.Log(HtmlHelper.fromHtml("")))
    val liveData: LiveData<NSClient2LiveData> = _liveData // Expose non-mutable form (avoid post from other classes)

    protected val compositeDisposable = CompositeDisposable() //TODO: once transformed to VM, clear! (atm plugins live forever)

    override fun onStart() {
        super.onStart()


        compositeDisposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .subscribeBy(
                onNext = ::handlePreferenceChange,
                onError = fabricPrivacy::logException
            )

        compositeDisposable += rxBus
            .toObservable(EventChargingState::class.java).map { "ChargingState" }
            .mergeWith(rxBus.toObservable(EventNetworkChange::class.java).map { "NetworkChange" })
            .mergeWith(rxBus.toObservable(EventNewBG::class.java).map { "NewBG" })
            .throttleLast(RATE_SEC.toLong(), TimeUnit.SECONDS)
            .subscribeBy(
                onNext = ::sync,
                onError = fabricPrivacy::logException
            )

        compositeDisposable += rxBus
            .toObservable(EventNSClientSync::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sync("GUI") }, { fabricPrivacy.logException(it) })

        compositeDisposable += rxBus
            .toObservable(EventNSClientFullSync::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ fullSync("GUI FULL") }, { fabricPrivacy.logException(it) })

        receiverStatusStore.updateNetworkStatus() // broadcast status to be caught for initial sync
    }

    override fun onStop() {
        super.onStop()
        compositeDisposable.clear()
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

    private fun handlePreferenceChange(event: EventPreferenceChange) {

        val nsFeatureKeys: Array<Int> = arrayOf(R.string.key_ns_cgm,
            R.string.key_ns_food,
            R.string.key_ns_profile,
            R.string.key_ns_insulin,
            R.string.key_ns_carbs,
            R.string.key_ns_careportal,
            R.string.key_ns_settings)

        if (nsFeatureKeys.any { event.isChanged(resourceHelper, it) }) {
            permissions = null // force new permissions read
        }

        val nsWifiKeys = listOf(R.string.key_ns_wifionly,
            R.string.key_ns_wifi_ssids,
            R.string.key_ns_allowroaming)

        if (nsWifiKeys.any { event.isChanged(resourceHelper, it) } ||
            event.isChanged(resourceHelper, R.string.key_ns_chargingonly)) {
            commAllowed()
        }
    }

    private fun readPermissions(ok: Runnable) = compositeDisposable.add(
        nightscoutServiceWrapper
            .testConnection()
            .observeOn(aapsSchedulers.io)
            .subscribeBy(
                onSuccess = {
                    when (it) {
                        is SetupState.Success -> {
                            permissions = it.permissions
                            ok.run()
                        }

                        is SetupState.Error   -> permissions = null
                    }
                },
                onError = { permissions = null })
    )

    private fun testConnection(context: Context) = compositeDisposable.add(
        nightscoutServiceWrapper
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

    fun lastModifiedCall() = compositeDisposable.add(
        nightscoutServiceWrapper.lastModified().subscribeBy(
            onSuccess = { addToLog(EventNSClientNewLog("RESULT", "success: $it", EventNSClientNewLog.Direction.IN)) },
            onError = { addToLog(EventNSClientNewLog("RESULT", "failure: ${it.message}", EventNSClientNewLog.Direction.IN)) })
    )

    fun getEntriesCall() {
        compositeDisposable.add(
            nightscoutServiceWrapper.getByDate(NightscoutCollection.ENTRIES, DateUtil.now() - T.mins(20).msecs()).subscribeBy(
                onSuccess = { addToLog(EventNSClientNewLog("RESULT", "success: ${it.body()}", EventNSClientNewLog.Direction.IN)) },
                onError = { addToLog(EventNSClientNewLog("RESULT", "failure: ${it.message}", EventNSClientNewLog.Direction.IN)) })
        )
    }

    @Synchronized
    fun clearLog() {
        listLog.clear()
        _liveData.postValue(NSClient2LiveData.Log(HtmlHelper.fromHtml("")))
    }

    @Synchronized protected fun addToLog(ev: EventNSClientNewLog) {
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
        if (permissions == null) {
            _liveData.postValue(NSClient2LiveData.State(resourceHelper.gs(R.string.missingpermissions)))
            readPermissions(Runnable { doSync(from) })
        } else doSync(from)
    }

    val PreferenceString.download: Boolean
        get() = get() == "PULL" || get() == "SYNC"
    val PreferenceString.upload: Boolean
        get() = get() == "PUSH" || get() == "SYNC"

    @Synchronized
    private fun doSync(from: String){
        addToLog(EventNSClientNewLog("SYNC START", "From: $from"))
        _liveData.postValue(NSClient2LiveData.State(resourceHelper.gs(R.string.combo_pump_state_running)))

        val cgmSync = PreferenceString(R.string.key_ns_cgm, "PUSH", sp, resourceHelper, rxBus)
        val ttSync = PreferenceString(R.string.key_ns_temptargets, "PUSH", sp, resourceHelper, rxBus)

        val tasks: MutableList<Single<Unit>> = mutableListOf()

        if (cgmSync.download || nsClientSourcePlugin.isEnabled()) {
            //CGM download
            tasks +=
                nightscoutServiceWrapper.getByLastModified(NightscoutCollection.ENTRIES, receiveTimestamp[NightscoutCollection.ENTRIES]!!.get())
                    //.doFinally {}
                    .flatMapObservable { Observable.fromIterable(it.body()) }
                    .doOnNext(::handleNewGlucoseValuesSideEffects)
                    .flatMapSingle { entryResponseBody ->
                        repository.findBgReadingByNSIdSingle(entryResponseBody.identifier!!)
                            .map { Pair(it, entryResponseBody) }
                    }
                    .flatMapSingle { (glucoseValueWrapper: ValueWrapper<GlucoseValue>, entryResponseBody: EntryResponseBody) ->
                        handleCgmDBStorage(glucoseValueWrapper, entryResponseBody)
                    }
                    .doOnError {
                        when (it) {
                            is BadInputDataException -> {
                                // TODO wrong NS data
                                addToLog(EventNSClientNewLog("entries BAD DATA:", "${it.badData}", EventNSClientNewLog.Direction.IN))
                                aapsLogger.error("Bad input data")
                            }

                            else                     -> aapsLogger.error("Something bad happened: $it")
                        }
                    }
                    .onErrorReturn { Unit } // TODO: map to something meaningful?
                    .toList()
                    .map { Unit } // TODO: map to something meaningful?
        }


        compositeDisposable += tasks.map { it.toObservable() }
            .combineLatest { Unit } // TODO map to something meaningful? This actually gives back a list of all results of all tasks
            .firstOrError()
            .subscribeBy(
                onSuccess = {
                    _liveData.postValue(NSClient2LiveData.State(resourceHelper.gs(R.string.ready)))
                    addToLog(EventNSClientNewLog("SYNC FINISHED", "From: $from"))
                    aapsLogger.debug(LTag.NSCLIENT, "Finished sync from $from")
                },
                onError = { /* boo! :(  */ }
            )
    }

    private fun handleNewGlucoseValuesSideEffects(it: EntryResponseBody) {
        val gv = it.toGlucoseValue()
        // stale data alarm
        if ((System.currentTimeMillis() - gv.timestamp) / (60 * 1000L) < 5L) {
            rxBus.send(EventDismissNotification(Notification.NSALARM))
            rxBus.send(EventDismissNotification(Notification.NSURGENTALARM))
        }
        // detect source
        nsClientSourcePlugin.detectSource(gv)
        // objectives
        sp.putBoolean(R.string.key_ObjectivesbgIsAvailableInNS, true)
    }

    private fun handleCgmDBStorage(glucoseValueWrapper: ValueWrapper<GlucoseValue>, entryResponseBody: EntryResponseBody): Single<Unit> {
        val gv = entryResponseBody.toGlucoseValue()
        return when (glucoseValueWrapper) {
            is ValueWrapper.Existing -> {
                entryResponseBody.srvModified?.let { srvModified -> receiveTimestamp[NightscoutCollection.ENTRIES]?.store(srvModified) }

                if (gv.contentEqualsTo(glucoseValueWrapper.value)) {
                    Single.just(Unit)
                        .doOnSuccess {
                            addToLog(EventNSClientNewLog("entries EXISTING:", "${entryResponseBody.identifier}", EventNSClientNewLog.Direction.IN))
                        }
                } else {
                    repository.runTransactionForResult(UpdateGlucoseValueTransaction(gv))
                        .doOnSuccess {
                            addToLog(EventNSClientNewLog("entries UPDATED:", "${entryResponseBody.identifier}", EventNSClientNewLog.Direction.IN))
                        }
                }
            }

            is ValueWrapper.Absent   -> {
                repository.runTransactionForResult(InsertGlucoseValueTransaction(gv))
                    .doOnSuccess { addToLog(EventNSClientNewLog("entries NEW:", "${entryResponseBody.identifier}", EventNSClientNewLog.Direction.IN)) }
            }
        }
    }

    protected fun handleTemporaryTargetDBStorage(temporaryTargetWrapper: ValueWrapper<TemporaryTarget>, entryResponseBody: EntryResponseBody): Single<Unit> {
        val tt = entryResponseBody.toTemporaryTarget()
        return when (temporaryTargetWrapper) {
            is ValueWrapper.Existing -> {
                entryResponseBody.srvModified?.let { srvModified -> receiveTimestamp[NightscoutCollection.TEMPORARY_TARGET]?.store(srvModified) }

                if (tt.contentEqualsTo(temporaryTargetWrapper.value)) {
                    Single.just(Unit)
                        .doOnSuccess {
                            addToLog(EventNSClientNewLog("temptarget EXISTING:", "${entryResponseBody.identifier}", EventNSClientNewLog.Direction.IN))
                        }
                } else {
                    repository.runTransactionForResult(UpdateTemporaryTargetTransaction(tt))
                        .doOnSuccess {
                            addToLog(EventNSClientNewLog("temptarget UPDATED:", "${entryResponseBody.identifier}", EventNSClientNewLog.Direction.IN))
                        }
                }
            }

            is ValueWrapper.Absent   -> {
                repository.runTransactionForResult(InsertTemporaryTargetAndCancelCurrentTransaction(tt))
                    .doOnSuccess { addToLog(EventNSClientNewLog("temptarget NEW:", "${entryResponseBody.identifier}", EventNSClientNewLog.Direction.IN)) }
            }
        }
    }

    @Suppress("SameParameterValue")
    private fun fullSync(from: String) {
        aapsLogger.debug(LTag.NSCLIENT, "Running FULL sync from $from")
        // reset timestamps
        for (c in NightscoutCollection.values()) {
            lastProcessedId[c]?.store(0)
            receiveTimestamp[c]?.store(0)
        }
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

    companion object {
        const val RATE_IDENT = "NSCLIENT2SYNC"
        const val RATE_SEC = 30
    }
}
