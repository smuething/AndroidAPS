package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_GLUCOSE_VALUES
import info.nightscout.androidaps.database.entities.GlucoseValue
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Single

@Dao
internal interface GlucoseValueDao : TraceableDao<GlucoseValue> {

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE id = :id")
    override fun findById(id: Long): GlucoseValue?

    @Query("DELETE FROM $TABLE_GLUCOSE_VALUES")
    override fun deleteAllEntries()

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE nightscoutId = :nsId AND referenceId IS NULL")
    fun findByNSId(nsId: String): GlucoseValue?

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE timestamp = :timestamp AND sourceSensor = :sourceSensor AND referenceId IS NULL")
    fun findByTimestampAndSensor(timestamp: Long, sourceSensor: GlucoseValue.SourceSensor): GlucoseValue?

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE timestamp >= :timestamp AND isValid = 1 AND referenceId IS NULL AND value >= 39 ORDER BY timestamp ASC")
    fun compatGetBgreadingsDataFromTime(timestamp: Long): Single<List<GlucoseValue>>

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE timestamp BETWEEN :start AND :end AND isValid = 1 AND referenceId IS NULL AND value >= 39 ORDER BY timestamp ASC")
    fun compatGetBgreadingsDataFromTime(start: Long, end: Long): Single<List<GlucoseValue>>

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE timestamp >= :timestamp AND isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    fun compatGetAllBgreadingsDataFromTime(timestamp: Long): Single<List<GlucoseValue>>

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE id > :lastId AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getDataFromId(lastId: Long): Single<List<GlucoseValue>>
}