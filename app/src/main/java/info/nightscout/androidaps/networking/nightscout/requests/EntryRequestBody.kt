package info.nightscout.androidaps.networking.nightscout.requests

import com.google.gson.annotations.SerializedName

/**
 * Created by adrian on 08.04.20.
 */

data class EntryRequestBody(
    @SerializedName("identifier") val identifier: String,
    @SerializedName("date") val date: Long,
    @SerializedName("utcOffset") val utcOffset: Int,
    @SerializedName("carbs") val carbs: Int? = null, // TODO: add `? = null` to the types that can be not present
    @SerializedName("insulin") val insulin: Int? = null,
    @SerializedName("eventType") val eventType: EventType? = null, // TODO: For selection, use enums with @SerializedName
    @SerializedName("app") val app: String? = null,
    @SerializedName("subject") val subject: String? = null,
    @SerializedName("svg") val svg: Int? = null

    // TODO: add all other possible fields
)

enum class EventType {
    @SerializedName("Snack Bolus")
    SNACK_BOLUS
}