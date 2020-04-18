package info.nightscout.androidaps.networking.nightscout.requests

import com.google.gson.annotations.SerializedName
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.networking.nightscout.exceptions.BadInputDataException
import info.nightscout.androidaps.utils.resources.ResourceHelper
import java.util.concurrent.TimeUnit

/**
 * Created by adrian on 08.04.20.
 */

typealias EntryResponseBody = EntryRequestBody // We get same things back as we send

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
    @SerializedName("carbs") val carbs: Int? = null,
    @SerializedName("insulin") val insulin: Int? = null,
    @SerializedName("eventType") val eventType: EventType? = null,
    @SerializedName("app") val app: String? = null,
    @SerializedName("subject") val subject: String? = null,
    @SerializedName("sgv") val sgv: Double? = null,
    @SerializedName("raw") val raw: Double? = null,
    @SerializedName("noise") val noise: Double? = null,
    @SerializedName("direction") val direction: GlucoseValue.TrendArrow? = null,
    @SerializedName("device") val device: String? = null,
    @SerializedName("units") val units: Units? = null,
    @SerializedName("isValid") val isValid: Boolean? = null,
    @SerializedName("type") val type: EntriesType? = null,
    @SerializedName("reason") val reason: TemporaryTarget.Reason? = null,
    @SerializedName("targetBottom") val targetBottom: Double? = null,
    @SerializedName("targetTop") val targetTop: Double? = null,
    @SerializedName("enteredBy") val enteredBy: String? = null,
    @SerializedName("duration") val duration: Long? = null

    // TODO: add all other possible fields
)

enum class EventType(val text: String) {
    @SerializedName("Temporary Target")
    TEMPORARY_TARGET("Temporary Target")
    ;
}

enum class Units(val text: String) {
    @SerializedName(Constants.MGDL)
    MGDL(Constants.MGDL),

    @SerializedName(Constants.MMOL)
    MMOL(Constants.MMOL)
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
        interfaceIDs_backing = interfaceIDs_backing,
        utcOffset = TimeUnit.MINUTES.toMillis(utcOffset),
        isValid = isValid ?: true,
        timestamp = date,
        raw = raw,
        value = sgv?.let { sgv -> Profile.toMgdl(sgv, units?.text) }
            ?: throw BadInputDataException(this),
        trendArrow = direction ?: GlucoseValue.TrendArrow.NONE,
        noise = noise,
        sourceSensor = GlucoseValue.SourceSensor.valueOf(device
            ?: GlucoseValue.SourceSensor.UNKNOWN.toString())
    ).also { it.interfaceIDs.nightscoutId = identifier }

fun fromGlucoseValue(glucoseValue: GlucoseValue, resourceHelper: ResourceHelper): EntryRequestBody =
    EntryRequestBody(
        version = glucoseValue.version,
        app = resourceHelper.gs(R.string.app_name),
        interfaceIDs_backing = glucoseValue.interfaceIDs_backing,
        identifier = glucoseValue.interfaceIDs.nightscoutId,
        utcOffset = TimeUnit.MILLISECONDS.toMinutes(glucoseValue.utcOffset),
        date = glucoseValue.timestamp,
        device = glucoseValue.sourceSensor.toString(),
        sgv = glucoseValue.value,
        raw = glucoseValue.raw,
        direction = glucoseValue.trendArrow,
        isValid = glucoseValue.isValid,
        noise = glucoseValue.noise,
        units = Units.MGDL,
        type = EntriesType.SGV
    )

fun EntryRequestBody.toTemporaryTarget(): TemporaryTarget =
    TemporaryTarget(
        interfaceIDs_backing = interfaceIDs_backing,
        utcOffset = TimeUnit.MINUTES.toMillis(utcOffset),
        isValid = isValid ?: true,
        timestamp = date,
        duration = duration?.let { TimeUnit.MINUTES.toMillis(it) }
            ?: throw BadInputDataException(this),
        reason = reason ?: TemporaryTarget.Reason.CUSTOM,
        lowTarget = targetBottom?.let { Profile.toMgdl(it, units?.text) }
            ?: throw BadInputDataException(this),
        highTarget = targetTop?.let { Profile.toMgdl(it, units?.text) }
            ?: throw BadInputDataException(this)
    ).also { it.interfaceIDs.nightscoutId = identifier }

fun fromTemporaryTarget(temporaryTarget: TemporaryTarget, resourceHelper: ResourceHelper): EntryRequestBody =
    EntryRequestBody(
        version = temporaryTarget.version,
        app = resourceHelper.gs(R.string.app_name),
        interfaceIDs_backing = temporaryTarget.interfaceIDs_backing,
        identifier = temporaryTarget.interfaceIDs.nightscoutId,
        utcOffset = TimeUnit.MILLISECONDS.toMinutes(temporaryTarget.utcOffset),
        date = temporaryTarget.timestamp,
        eventType = EventType.TEMPORARY_TARGET,
        duration = TimeUnit.MILLISECONDS.toMinutes(temporaryTarget.duration),
        reason = temporaryTarget.reason,
        targetBottom = temporaryTarget.lowTarget,
        targetTop = temporaryTarget.highTarget,
        units = Units.MGDL
    )
