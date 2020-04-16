package info.nightscout.androidaps.networking.nightscout.requests

import com.google.gson.annotations.SerializedName
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.networking.nightscout.exceptions.BadInputDataException
import info.nightscout.androidaps.utils.resources.ResourceHelper

/**
 * Created by adrian on 08.04.20.
 */

data class EntryRequestBody(
    // response only
    @SerializedName("srvModified") val srvModified: Long? = null,
    @SerializedName("identifier") val identifier: String? = null,

    // post & response

    // core TraceableDBEntry
    @SerializedName("version") val version: Int,
    @SerializedName("interfaceIDs_backing") val interfaceIDs_backing: InterfaceIDs? = null,
    // user data
    @SerializedName("date") val date: Long,
    @SerializedName("utcOffset") val utcOffset: Long,
    @SerializedName("carbs") val carbs: Int? = null, // TODO: add `? = null` to the types that can be not present
    @SerializedName("insulin") val insulin: Int? = null,
    @SerializedName("eventType") val eventType: EventType? = null, // TODO: For selection, use enums with @SerializedName
    @SerializedName("app") val app: String? = null,
    @SerializedName("subject") val subject: String? = null,
    @SerializedName("sgv") val sgv: Double? = null,
    @SerializedName("raw") val raw: Double? = null,
    @SerializedName("noise") val noise: Double? = null,
    @SerializedName("direction") val direction: GlucoseValue.TrendArrow? = null,
    @SerializedName("device") val device: String? = null,
    @SerializedName("units") val units: Units? = null,
    @SerializedName("isValid") val isValid: Boolean? = null,
    @SerializedName("type") val type: EntriesType? = null

    // TODO: add all other possible fields
)

enum class EventType {
    @SerializedName("Snack Bolus")
    SNACK_BOLUS
}

enum class Units() {
    @SerializedName(Constants.MGDL)
    MGDL,

    @SerializedName(Constants.MMOL)
    MMOL
}

enum class EntriesType {
    @SerializedName("sgv")
    SGV,

    @SerializedName("mbg")
    MBG,

    @SerializedName("cal")
    CAL
}

fun EntryRequestBody.toGlucoseValue(): GlucoseValue =
    GlucoseValue(
        timestamp = date,
        utcOffset = utcOffset,
        raw = raw,
        value = sgv?.let { sgv -> if (units == Units.MGDL) sgv else sgv * Constants.MGDL_TO_MMOLL }
            ?: throw BadInputDataException(),
        trendArrow = direction ?: GlucoseValue.TrendArrow.NONE,
        noise = noise,
        sourceSensor = GlucoseValue.SourceSensor.valueOf(device
            ?: GlucoseValue.SourceSensor.UNKNOWN.toString()),
        interfaceIDs_backing = interfaceIDs_backing,
        isValid = isValid ?: true
    ).also { it.interfaceIDs.nightscoutId = identifier }

fun fromGlucoseValue(glucoseValue: GlucoseValue, resourceHelper: ResourceHelper): EntryRequestBody =
    EntryRequestBody(
        version = glucoseValue.version,
        interfaceIDs_backing = glucoseValue.interfaceIDs_backing,
        date = glucoseValue.timestamp,
        utcOffset = glucoseValue.utcOffset,
        app = resourceHelper.gs(R.string.app_name),
        device = glucoseValue.sourceSensor.toString(),
        sgv = glucoseValue.value,
        raw = glucoseValue.raw,
        direction = glucoseValue.trendArrow,
        isValid = glucoseValue.isValid,
        noise = glucoseValue.noise,
        units = Units.MGDL,
        type = EntriesType.SGV,
        identifier = glucoseValue.interfaceIDs.nightscoutId
    )
