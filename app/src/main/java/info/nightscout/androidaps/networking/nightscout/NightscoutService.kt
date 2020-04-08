package info.nightscout.androidaps.networking.nightscout

import info.nightscout.androidaps.dependencyInjection.networking.NSRetrofitFactory
import info.nightscout.androidaps.networking.nightscout.data.SetupState
import info.nightscout.androidaps.networking.nightscout.requests.EntryRequestBody
import info.nightscout.androidaps.networking.nightscout.responses.*
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import io.reactivex.Single
import okhttp3.Headers
import retrofit2.Response
import java.net.HttpURLConnection
import java.net.UnknownHostException

/**
 * Created by adrian on 2019-12-23.
 */
class NightscoutService(private val nsRetrofitFactory: NSRetrofitFactory) {

    fun testSetup(): Single<SetupState> = nsRetrofitFactory.getNSService().statusVerbose().map {
        when {
            it.isSuccessful -> handleTestSetupSuccess(it.body()!!)
            else            -> handleTestSetupError(it.code(), it.errorBody()?.string())
        }
    }.onErrorReturn {
        handleTestSetupThrowable(it)
    }

    private fun handleTestSetupThrowable(it: Throwable): SetupState =
        SetupState.Error(
            when (it) {
                is UnknownHostException              -> "Offline or wrong Nightscout URL?"
                is java.net.PortUnreachableException -> "Wrong port in  Nightscout URL?"
                else                                 -> "Unknown network error: ${it.javaClass.name}"
            }
        )

    private fun handleTestSetupSuccess(statusResponse: StatusResponse): SetupState {
        val errors = mutableListOf<String>()

        statusResponse.apiPermissions.let {
            if (!it.deviceStatus.readCreate) errors.add(PERMISSIONS_INSUFFICIENT.format("devicestatus"))
            if (!it.settings.read) errors.add(PERMISSIONS_INSUFFICIENT.format("settings"))
            if (!it.entries.full) errors.add(PERMISSIONS_INSUFFICIENT.format("entries"))
            if (!it.treatments.full) errors.add(PERMISSIONS_INSUFFICIENT.format("treatments"))
            if (!it.food.full) errors.add(PERMISSIONS_INSUFFICIENT.format("food"))
        }
        return if (errors.isEmpty()) {
            SetupState.Success
        } else {
            SetupState.Error(errors.reduce { acc: String, s: String -> acc + "\n" + s })
        }
    }

    private fun handleTestSetupError(code: Int, message: String?): SetupState =
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

    fun status() = nsRetrofitFactory.getNSService().statusSimple()

    fun lastModified() = nsRetrofitFactory.getNSService().lastModified()

    fun postGlucoseStatus(glucoseStatus: GlucoseStatus) = postEntry(
        EntryRequestBody(
            identifier = "todo",
            date = glucoseStatus.date,
            utcOffset = 0
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

    companion object {
        const val BAD_ACCESS_TOKEN_MESSAGE = "Missing or bad access token or JWT"
        const val TIME_HEADER_TOLERANCE_MESSAGE = "Date header out of tolerance"

        // TODO: Internationalize:
        const val PERMISSIONS_INSUFFICIENT = "Permissions insufficient for %s"
    }
}