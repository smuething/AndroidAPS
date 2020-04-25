package info.nightscout.androidaps.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import info.nightscout.androidaps.database.TABLE_GLUCOSE_VALUES
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.interfaces.DBEntryWithTime
import info.nightscout.androidaps.database.interfaces.TraceableDBEntry
import java.util.*

@Entity(tableName = TABLE_GLUCOSE_VALUES,
    foreignKeys = [ForeignKey(
        entity = GlucoseValue::class,
        parentColumns = ["id"],
        childColumns = ["referenceId"])],
    indices = [Index("referenceId"), Index("timestamp")])
data class GlucoseValue(
    @PrimaryKey(autoGenerate = true)
    override var id: Long = 0,
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    @Embedded
    override var interfaceIDs_backing: InterfaceIDs? = InterfaceIDs(),
    override var timestamp: Long,
    override var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    var raw: Double?,
    var value: Double,
    var trendArrow: TrendArrow,
    var noise: Double?,
    var sourceSensor: SourceSensor
) : TraceableDBEntry, DBEntryWithTime {

    fun contentEqualsTo(other: GlucoseValue): Boolean =
        timestamp == other.timestamp &&
            utcOffset == other.utcOffset &&
            raw == other.raw &&
            value == other.value &&
            trendArrow == other.trendArrow &&
            noise == other.noise &&
            sourceSensor == other.sourceSensor &&
            isValid == other.isValid

    fun isRecordDeleted(other: GlucoseValue): Boolean =
        isValid && !other.isValid

    enum class TrendArrow {
        NONE,
        TRIPLE_UP,
        DOUBLE_UP,
        SINGLE_UP,
        FORTY_FIVE_UP,
        FLAT,
        FORTY_FIVE_DOWN,
        SINGLE_DOWN,
        DOUBLE_DOWN,
        TRIPLE_DOWN
    }

    enum class SourceSensor {
        DEXCOM_NATIVE_UNKNOWN,
        DEXCOM_G6_NATIVE,
        DEXCOM_G5_NATIVE,
        DEXCOM_G4_WIXEL,
        DEXCOM_G4_XBRIDGE,
        DEXCOM_G4_NATIVE,
        MEDTRUM_A6,
        DEXCOM_G4_NET,
        DEXCOM_G4_NET_XBRIDGE,
        DEXCOM_G4_NET_CLASSIC,
        DEXCOM_G5_XDRIP,
        DEXCOM_G6_NATIVE_XDRIP,
        DEXCOM_G5_NATIVE_XDRIP,
        LIBRE_1_NET,
        LIBRE_1_BLUE,
        LIBRE_1_PL,
        LIBRE_1_BLUCON,
        LIBRE_1_TOMATO,
        LIBRE_1_RF,
        LIBRE_1_LIMITTER,
        GLIMP,
        LIBRE_2_NATIVE,
        POCTECH_NATIVE,
        MM_600_SERIES,
        EVERSENSE,
        RANDOM,
        UNKNOWN
    }
}