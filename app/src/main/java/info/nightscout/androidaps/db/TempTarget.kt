package info.nightscout.androidaps.db

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.database.interfaces.end
import info.nightscout.androidaps.interfaces.Interval
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.logging.StacktraceLoggerWrapper.Companion.getLogger
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import org.slf4j.Logger
import java.util.concurrent.TimeUnit

class TempTarget(
    var data: TemporaryTarget = TemporaryTarget(
        timestamp = 0,
        utcOffset = 0,
        reason = TemporaryTarget.Reason.CUSTOM,
        highTarget = 0.0,
        lowTarget = 0.0,
        duration = 0
    )
) : Interval {

    fun target(): Double {
        return (data.lowTarget + data.highTarget) / 2
    }

    fun isEqual(other: TempTarget): Boolean {
        if (data.timestamp != data.timestamp) {
            return false
        }
        if (data.duration != other.data.duration) return false
        if (data.lowTarget != other.data.lowTarget) return false
        if (data.highTarget != other.data.highTarget) return false
        if (data.reason != other.data.reason) return false
        return data.interfaceIDs.nightscoutId == other.data.interfaceIDs.nightscoutId
    }

    fun copyFrom(t: TempTarget) {
        data = t.data.copy(id = 0, version = 0, dateCreated = -1, referenceId = null)
    }

    fun date(date: Long): TempTarget {
        data.timestamp = date
        return this
    }

    fun low(low: Double): TempTarget {
        data.lowTarget = low
        return this
    }

    fun high(high: Double): TempTarget {
        data.highTarget = high
        return this
    }

    fun duration(duration: Int): TempTarget {
        data.duration = TimeUnit.MINUTES.toMillis(duration.toLong())
        return this
    }

    fun reason(reason: String?): TempTarget {
        data.reason = when (reason) {
            "Eating Soon" -> TemporaryTarget.Reason.EATING_SOON
            "Activity"    -> TemporaryTarget.Reason.ACTIVITY
            "Hypo"        -> TemporaryTarget.Reason.HYPOGLYCEMIA
            "Automation"  -> TemporaryTarget.Reason.AUTOMATION
            else          -> TemporaryTarget.Reason.CUSTOM
        }
        return this
    }

    val reason
        get() = when (data.reason) {
            TemporaryTarget.Reason.EATING_SOON  -> "Eating Soon"
            TemporaryTarget.Reason.ACTIVITY     -> "Activity"
            TemporaryTarget.Reason.HYPOGLYCEMIA -> "Hypo"
            TemporaryTarget.Reason.AUTOMATION   -> "Automation"
            TemporaryTarget.Reason.CUSTOM       -> "Custom"
        }

    fun _id(_id: String?): TempTarget {
        data.interfaceIDs.nightscoutId = _id
        return this
    }

    fun source(source: Int): TempTarget {
        return this
    }

    // -------- Interval interface ---------
    private var cuttedEnd: Long? = null

    override fun durationInMsec(): Long {
        return data.duration
    }

    override fun start(): Long {
        return data.timestamp
    }

    // planned end time at time of creation
    override fun originalEnd(): Long {
        return data.end
    }

    // end time after cut
    override fun end(): Long {
        return if (cuttedEnd != null) cuttedEnd!! else originalEnd()
    }

    override fun cutEndTo(end: Long) {
        cuttedEnd = end
    }

    override fun match(time: Long): Boolean {
        return start() <= time && end() >= time
    }

    override fun before(time: Long): Boolean {
        return end() < time
    }

    override fun after(time: Long): Boolean {
        return start() > time
    }

    override fun isInProgress(): Boolean {
        return match(System.currentTimeMillis())
    }

    override fun isEndingEvent(): Boolean {
        return false
    }

    override fun isValid(): Boolean {
        return true
    }

    // -------- Interval interface end ---------
    fun lowValueToUnitsToString(units: String): String {
        return if (units == Constants.MGDL) DecimalFormatter.to0Decimal(data.lowTarget) else DecimalFormatter.to1Decimal(data.lowTarget * Constants.MGDL_TO_MMOLL)
    }

    fun highValueToUnitsToString(units: String): String {
        return if (units == Constants.MGDL) DecimalFormatter.to0Decimal(data.highTarget) else DecimalFormatter.to1Decimal(data.highTarget * Constants.MGDL_TO_MMOLL)
    }

    override fun toString(): String {
        return "TemporaryTarget{" +
            "date=" + data.timestamp +
            "date=" + DateUtil.dateAndTimeString(data.timestamp) +
            ", isValid=" + isValid +
            ", duration=" + data.duration +
            ", reason=" + data.reason +
            ", low=" + data.lowTarget +
            ", high=" + data.highTarget +
            '}'
    }

    fun friendlyDescription(units: String): String {
        return Profile.toTargetRangeString(data.lowTarget, data.highTarget, Constants.MGDL, units) +
            units +
            "@" + MainApp.gs(R.string.mins, TimeUnit.MILLISECONDS.toMinutes(data.duration)) + data.reason
    }

    companion object {
        private val log: Logger = getLogger(L.DATABASE)
    }
}