package info.nightscout.androidaps.networking.nightscout

import androidx.annotation.StringRes
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.dependencyInjection.networking.NSRetrofitFactory
import info.nightscout.androidaps.networking.nightscout.data.NightscoutCollection
import info.nightscout.androidaps.networking.nightscout.data.SetupState
import info.nightscout.androidaps.networking.nightscout.requests.EntryRequestBody
import info.nightscout.androidaps.networking.nightscout.responses.*
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.toText
import io.reactivex.Single
import okhttp3.Headers
import retrofit2.Response
import java.net.HttpURLConnection
import java.net.UnknownHostException

/**
 * Created by adrian on 2019-12-23.
 */
class NightscoutService(
    private val nsRetrofitFactory: NSRetrofitFactory,
    private val resourceHelper: ResourceHelper,
    private val sp: SP
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

        for (collection in NightscoutCollection.values())
            when (collection) {
                NightscoutCollection.DEVICESTATUS ->
                    if (!Config.NSCLIENT)
                        if (!statusResponse.apiPermissions.deviceStatus.readCreate)
                            errors.add(PERMISSIONS_INSUFFICIENT.format(NightscoutCollection.DEVICESTATUS.collection))

                NightscoutCollection.FOOD         -> statusResponse.mapRequiredPermissionToError(R.string.key_ns_food, collection, errors)
                NightscoutCollection.PROFILE      -> statusResponse.mapRequiredPermissionToError(R.string.key_ns_profile, collection, errors)

                NightscoutCollection.TREATMENTS   -> {
                    statusResponse.mapRequiredPermissionToError(R.string.key_ns_insulin, collection, errors)
                    statusResponse.mapRequiredPermissionToError(R.string.key_ns_carbs, collection, errors)
                    statusResponse.mapRequiredPermissionToError(R.string.key_ns_careportal, collection, errors)
                }

                NightscoutCollection.ENTRIES      -> statusResponse.mapRequiredPermissionToError(R.string.key_ns_cgm, collection, errors)
                NightscoutCollection.SETTINGS     -> statusResponse.mapRequiredPermissionToError(R.string.key_ns_settings, collection, errors)
            }
        return if (errors.isEmpty()) {
            SetupState.Success(statusResponse.apiPermissions)
        } else {
            SetupState.Error(errors.reduce { acc: String, s: String -> acc + "\n" + s })
        }
    }

    fun StatusResponse.mapRequiredPermissionToError(@StringRes spVal: Int, collection: NightscoutCollection, errors: MutableList<String>) {
        when (sp.getString(R.string.key_ns_cgm, "PULL")) {
            "PULL" -> if (!apiPermissions.entries.read) errors.add(PERMISSIONS_INSUFFICIENT.format(collection.collection))
            "PUSH" -> if (!apiPermissions.entries.createUpdate) errors.add(PERMISSIONS_INSUFFICIENT.format(collection.collection))
            "SYNC" -> if (!apiPermissions.entries.full) errors.add(PERMISSIONS_INSUFFICIENT.format(collection.collection))
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

    fun postGlucoseStatus(glucoseValue: GlucoseValue) = postEntry(
        EntryRequestBody(
            date = glucoseValue.timestamp,
            utcOffset = glucoseValue.utcOffset,
            app = resourceHelper.gs(R.string.app_name),
            device = glucoseValue.sourceSensor.toString(),
            sgv = glucoseValue.value,
            direction = glucoseValue.trendArrow.toText(),
            isValid = glucoseValue.isValid
        )
    )

    private fun postEntry(entryRequestBody: EntryRequestBody): Single<PostEntryResponseType> = nsRetrofitFactory.getNSService()
        .postEntry(entryRequestBody)
        .map { it.toResponseType() }

    private fun Response<DummyResponse>.toResponseType(): PostEntryResponseType {
        val headers: Headers = this.headers()
        return when (this.code()) {
            // Todo: do we want to distinguish between "created new document" (201) and "Successfully finished operation"?
            201, 204 -> PostEntryResponseType.Success(headers["Last-Modified"], headers["Location"])  // here we should according to standard also get the datum back that we created?
            400      -> PostEntryResponseType.Failure(FailureReason.MALFORMATTED_REQUEST)
            401      -> PostEntryResponseType.Failure(FailureReason.UNAUTHORIZED)
            403      -> PostEntryResponseType.Failure(FailureReason.FORBIDDEN)
            404      -> PostEntryResponseType.Failure(FailureReason.NOT_FOUND)
            422      -> PostEntryResponseType.Failure(FailureReason.UNPROCESSABLE_ENTITY)
            else     -> PostEntryResponseType.Failure(FailureReason.UNKNOWN)
        }
    }

    fun getByDate(collection: NightscoutCollection, from: Long, sort: String = "date", limit: Int = 1000) =
        nsRetrofitFactory.getNSService().getByDate(collection, from, sort, limit)

    fun getByLastModified(collection: NightscoutCollection, from: Long, sort: String = "srvModified", limit: Int = 1000) =
        nsRetrofitFactory.getNSService().getByLastModified(collection, from, sort, limit)

    companion object {
        const val BAD_ACCESS_TOKEN_MESSAGE = "Missing or bad access token or JWT"
        const val TIME_HEADER_TOLERANCE_MESSAGE = "Date header out of tolerance"

        // TODO: Internationalize:
        const val PERMISSIONS_INSUFFICIENT = "Permissions insufficient for %s"
    }
}