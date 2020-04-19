package info.nightscout.androidaps.networking.nightscout

import androidx.annotation.StringRes
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.dependencyInjection.networking.NSRetrofitFactory
import info.nightscout.androidaps.networking.nightscout.data.NightscoutCollection
import info.nightscout.androidaps.networking.nightscout.data.SetupState
import info.nightscout.androidaps.networking.nightscout.requests.EntryRequestBody
import info.nightscout.androidaps.networking.nightscout.requests.fromGlucoseValue
import info.nightscout.androidaps.networking.nightscout.requests.fromTemporaryTarget
import info.nightscout.androidaps.networking.nightscout.responses.*
import info.nightscout.androidaps.plugins.source.NSClientSourcePlugin
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.Single
import okhttp3.Headers
import retrofit2.Response
import java.net.HttpURLConnection
import java.net.UnknownHostException

/**
 * Created by adrian on 2019-12-23.
 */
class NightscoutServiceWrapper(
    private val nsRetrofitFactory: NSRetrofitFactory,
    private val resourceHelper: ResourceHelper,
    private val sp: SP,
    private val nsClientSourcePlugin: NSClientSourcePlugin
) {

    fun testConnection(): Single<SetupState> = nsRetrofitFactory.getNSService().statusVerbose().map {
        when {
            it.isSuccessful -> handleTestConnectionSuccess(it.body()!!)
            else            -> handleTestConnectionError(it.code(), it.errorBody()?.string())
        }
    }.onErrorReturn {
        handleTesConnectionThrowable(it)
    }

    private fun handleTesConnectionThrowable(it: Throwable): SetupState =
        SetupState.Error(
            when (it) {
                is UnknownHostException              -> "Offline or wrong Nightscout URL?"
                is java.net.PortUnreachableException -> "Wrong port in  Nightscout URL?"
                else                                 -> "Unknown network error: ${it.javaClass.name}"
            }
        )

    private fun handleTestConnectionSuccess(statusResponse: StatusResponse): SetupState {
        val errors = mutableListOf<String>()

        NightscoutCollection.values().forEach {
            when (it) {
                NightscoutCollection.SETTINGS         -> statusResponse.addRequiredPermissionToErrors(R.string.key_ns_settings, it, errors)
                NightscoutCollection.FOOD             -> statusResponse.addRequiredPermissionToErrors(R.string.key_ns_food, it, errors)
                NightscoutCollection.PROFILE          -> statusResponse.addRequiredPermissionToErrors(R.string.key_ns_profile, it, errors)
                NightscoutCollection.TEMPORARY_TARGET -> statusResponse.addRequiredPermissionToErrors(R.string.key_ns_temptargets, it, errors)
                NightscoutCollection.INSULIN          -> statusResponse.addRequiredPermissionToErrors(R.string.key_ns_insulin, it, errors)
                NightscoutCollection.CARBS            -> statusResponse.addRequiredPermissionToErrors(R.string.key_ns_carbs, it, errors)
                NightscoutCollection.CAREPORTAL_EVENT -> statusResponse.addRequiredPermissionToErrors(R.string.key_ns_careportal, it, errors)
                NightscoutCollection.PROFILE_SWITCH   -> statusResponse.addRequiredPermissionToErrors(R.string.key_ns_profileswitch, it, errors)
                NightscoutCollection.TEMPORARY_BASAL  -> statusResponse.addRequiredPermissionToErrors(R.string.key_ns_temporarybasal, it, errors)
                NightscoutCollection.EXTENDED_BOLUS   -> statusResponse.addRequiredPermissionToErrors(R.string.key_ns_extendedbolus, it, errors)

                NightscoutCollection.DEVICE_STATUS    ->
                    if (!Config.NSCLIENT)
                        if (!statusResponse.apiPermissions.deviceStatus.readCreate)
                            errors.add(PERMISSIONS_INSUFFICIENT.format(NightscoutCollection.DEVICE_STATUS.collection))

                NightscoutCollection.ENTRIES          -> {
                    statusResponse.addRequiredPermissionToErrors(R.string.key_ns_cgm, it, errors)
                    if (nsClientSourcePlugin.isEnabled() && !statusResponse.apiPermissions.entries.read) errors.add(PERMISSIONS_INSUFFICIENT.format(it.collection))
                }
            }
        }

        return if (errors.isEmpty()) {
            SetupState.Success(statusResponse.apiPermissions)
        } else {
            SetupState.Error(errors.reduce { acc: String, s: String -> acc + "\n" + s })
        }
    }

    private fun StatusResponse.addRequiredPermissionToErrors(@StringRes spVal: Int, collection: NightscoutCollection, errors: MutableList<String>) {
        when (sp.getString(spVal, "PUSH")) { // TODO: const val or Enum?
            "PULL" -> if (!apiPermissions.of(collection).read) errors.add(PERMISSIONS_INSUFFICIENT.format(collection.collection))
            "PUSH" -> if (!apiPermissions.of(collection).createUpdate) errors.add(PERMISSIONS_INSUFFICIENT.format(collection.collection))
            "SYNC" -> if (!apiPermissions.of(collection).full) errors.add(PERMISSIONS_INSUFFICIENT.format(collection.collection))
        }
    }

    private fun handleTestConnectionError(code: Int, message: String?): SetupState =
        when (code) {
            HttpURLConnection.HTTP_UNAUTHORIZED -> if (message?.contains(BAD_ACCESS_TOKEN_MESSAGE) == true) {
                SetupState.Error("Check credentials token.")
            } else if (message?.contains(TIME_HEADER_TOLERANCE_MESSAGE) == true) {
                SetupState.Error("Time/date out of sync with server!")
            } else {
                SetupState.Error("Unauthorized!")
            }

            else                                -> SetupState.Error("Network error code: $code, $message")

        }

    fun lastModified() = nsRetrofitFactory.getNSService().lastModified()

    // BG READINGS
    fun insert(glucoseValue: GlucoseValue) =
        insertEntry(NightscoutCollection.ENTRIES, fromGlucoseValue(glucoseValue, resourceHelper))

    fun delete(glucoseValue: GlucoseValue) =
        glucoseValue.interfaceIDs.nightscoutId?.let {
            deleteEntry(NightscoutCollection.ENTRIES, it)
        }

    fun updateFromNS(glucoseValue: GlucoseValue) =
        glucoseValue.interfaceIDs.nightscoutId?.let {
            updateEntry(NightscoutCollection.ENTRIES, it, fromGlucoseValue(glucoseValue, resourceHelper))
        }
            ?: insertEntry(NightscoutCollection.ENTRIES, fromGlucoseValue(glucoseValue, resourceHelper))

    // TEMP TARGET
    fun insert(temporaryTarget: TemporaryTarget) =
        insertEntry(NightscoutCollection.TEMPORARY_TARGET, fromTemporaryTarget(temporaryTarget, resourceHelper))

    fun delete(temporaryTarget: TemporaryTarget) =
        temporaryTarget.interfaceIDs.nightscoutId?.let {
            deleteEntry(NightscoutCollection.TEMPORARY_TARGET, it)
        }

    fun updateFromNS(temporaryTarget: TemporaryTarget) =
        temporaryTarget.interfaceIDs.nightscoutId?.let {
            updateEntry(NightscoutCollection.TEMPORARY_TARGET, it, fromTemporaryTarget(temporaryTarget, resourceHelper))
        }
            ?: insertEntry(NightscoutCollection.TEMPORARY_TARGET, fromTemporaryTarget(temporaryTarget, resourceHelper))

    // GENERAL
    private fun insertEntry(collection: NightscoutCollection, entryRequestBody: EntryRequestBody): Single<PostEntryResponseType> = nsRetrofitFactory.getNSService()
        .insertEntry(collection.collection, entryRequestBody)
        .map { it.toResponseType() }

    private fun updateEntry(collection: NightscoutCollection, nsId: String, entryRequestBody: EntryRequestBody): Single<PostEntryResponseType> = nsRetrofitFactory.getNSService()
        .updateEntry(collection.collection, nsId, entryRequestBody)
        .map { it.toResponseType() }

    private fun deleteEntry(collection: NightscoutCollection, nsId: String): Single<PostEntryResponseType> = nsRetrofitFactory.getNSService()
        .deleteEntry(collection.collection, nsId)
        .map { it.toResponseType() }

    private fun Response<DummyResponse>.toResponseType(): PostEntryResponseType {
        val headers: Headers = this.headers()
        // headers["Last-Modified"] not used
        return PostEntryResponseType(ResponseCode.fromInt(this.code()), headers["Location"])
    }

    fun getByDate(collection: NightscoutCollection, from: Long, sort: String = "date", limit: Int = 1000) =
        nsRetrofitFactory.getNSService().getByDate(collection.collection, from, sort, limit)

    fun getByLastModified(collection: NightscoutCollection, from: Long, sort: String = "srvModified", limit: Int = 1000) =
        collection.eventType?.let { eventType ->
            nsRetrofitFactory.getNSService().getByLastModified(
                collection.collection,
                if (from == 0L) System.currentTimeMillis() - T.months(2).msecs() else from,
                eventType.text,
                sort, limit)
        } ?: nsRetrofitFactory.getNSService().getByLastModified(
            collection.collection,
            if (from == 0L) System.currentTimeMillis() - T.months(2).msecs() else from,
            sort, limit)

    companion object {
        const val BAD_ACCESS_TOKEN_MESSAGE = "Missing or bad access token or JWT"
        const val TIME_HEADER_TOLERANCE_MESSAGE = "Date header out of tolerance"

        // TODO: Internationalize:
        const val PERMISSIONS_INSUFFICIENT = "Permissions insufficient for %s"
    }
}