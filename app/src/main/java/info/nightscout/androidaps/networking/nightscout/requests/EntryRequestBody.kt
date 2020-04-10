package info.nightscout.androidaps.networking.nightscout.requests

import com.google.gson.annotations.SerializedName
import info.nightscout.androidaps.Constants

/**
 * Created by adrian on 08.04.20.
 */

data class EntryRequestBody(
    @SerializedName("identifier") val identifier: String? = null,
    @SerializedName("date") val date: Long,
    @SerializedName("utcOffset") val utcOffset: Long,
    @SerializedName("carbs") val carbs: Int? = null, // TODO: add `? = null` to the types that can be not present
    @SerializedName("insulin") val insulin: Int? = null,
    @SerializedName("eventType") val eventType: EventType? = null, // TODO: For selection, use enums with @SerializedName
    @SerializedName("app") val app: String? = null,
    @SerializedName("subject") val subject: String? = null,
    @SerializedName("sgv") val sgv: Double? = null,
    @SerializedName("direction") val direction: String? = null,
    @SerializedName("device") val device: String? = null,
    @SerializedName("units") val units: Units? = null,
    @SerializedName("isValid") val isValid: Boolean? = null

    // TODO: add all other possible fields
)

enum class EventType {
    @SerializedName("Snack Bolus")
    SNACK_BOLUS
}

enum class Units(val units: String) {
    MGDL(Constants.MGDL),
    MMOL(Constants.MMOL)
}

enum class EntriesType(val type: String) {
    SGV("sgv"),
    MBG("mbg"),
    CAL("cal")
}