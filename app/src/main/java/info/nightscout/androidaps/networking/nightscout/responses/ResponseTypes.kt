package info.nightscout.androidaps.networking.nightscout.responses

import androidx.annotation.StringRes
import info.nightscout.androidaps.R

/**
 * Created by adrian on 08.04.20.
 */

enum class ResponseCode (val code : Int, @StringRes val message: Int) {

    RECORD_CREATED( 201, R.string.recordcreated),
    RECORD_EXISTS(204, R.string.recordexist),
    MALFORMATTED_REQUEST(400, R.string.malformatedrequest),
    UNAUTHORIZED(401, R.string.unauthorized),
    NOT_FOUND(404, R.string.collectionnotfound), // collection not found
    FORBIDDEN(403, R.string.forbidden),
    UNPROCESSABLE_ENTITY(422, R.string.unprocessableentity),
    UNKNOWN(0, R.string.unknown);

    companion object {
        private val map = values().associateBy(ResponseCode::code)
        fun fromInt(type: Int) = map[type] ?: UNKNOWN
    }
}

class PostEntryResponseType (val code: ResponseCode?, val location: Location?)

typealias Location = String

val Location.id : String
    get() =
        Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").find(this)?.value ?: ""