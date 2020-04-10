package info.nightscout.androidaps.networking.nightscout.responses

import androidx.annotation.StringRes
import info.nightscout.androidaps.R

/**
 * Created by adrian on 08.04.20.
 */

enum class FailureReason (@StringRes val message: Int) {
    MALFORMATTED_REQUEST(R.string.androidaps_start), // TODO: replace with correct human readable String? Or remove `val message`
    UNAUTHORIZED(R.string.androidaps_start),
    NOT_FOUND(R.string.androidaps_start), // collection not found
    FORBIDDEN(R.string.androidaps_start),
    UNPROCESSABLE_ENTITY(R.string.androidaps_start),
    UNKNOWN(R.string.androidaps_start)
}

sealed class PostEntryResponseType {
    data class Success(val lastModified: String?, val location: Location?) : PostEntryResponseType()
    data class Failure(val reason: FailureReason): PostEntryResponseType()
}

typealias Location = String

val Location.id : String
    get() =
        Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").find(this)?.value ?: ""