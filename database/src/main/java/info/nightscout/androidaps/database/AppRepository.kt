package info.nightscout.androidaps.database

import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.interfaces.DBEntry
import info.nightscout.androidaps.database.transactions.Transaction
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.Callable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject internal constructor(
    internal val database: AppDatabase
) {

    private val changeSubject = PublishSubject.create<List<DBEntry>>()

    val changeObservable: Observable<List<DBEntry>> = changeSubject

    val databaseVersion = DATABASE_VERSION

    /**
     * Executes a transaction ignoring its result
     * Runs on IO scheduler
     */
    fun <T> runTransaction(transaction: Transaction<T>): Completable {
        val changes = mutableListOf<DBEntry>()
        return Completable.fromCallable {
            database.runInTransaction {
                transaction.database = DelegatedAppDatabase(changes, database)
                transaction.run()
            }
        }.subscribeOn(Schedulers.io()).doOnComplete {
            changeSubject.onNext(changes)
        }
    }

    /**
     * Executes a transaction and returns its result
     * Runs on IO scheduler
     */
    fun <T> runTransactionForResult(transaction: Transaction<T>): Single<T> {
        val changes = mutableListOf<DBEntry>()
        return Single.fromCallable {
            database.runInTransaction(Callable<T> {
                transaction.database = DelegatedAppDatabase(changes, database)
                transaction.run()
            })
        }.subscribeOn(Schedulers.io()).doOnSuccess {
            changeSubject.onNext(changes)
        }
    }

    fun clearDatabases() = database.clearAllTables()

    fun compatGetBgreadingsDataFromTime(timestamp: Long, ascending: Boolean) =
        database.glucoseValueDao.compatGetBgreadingsDataFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun compatGetBgreadingsDataFromTime(start: Long, end: Long, ascending: Boolean) =
        database.glucoseValueDao.compatGetBgreadingsDataFromTime(start, end)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun compatGetAllBgreadingsDataFromTime(timestamp: Long, ascending: Boolean) =
        database.glucoseValueDao.compatGetAllBgreadingsDataFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getDataFromId(lastId: Long) =
        database.glucoseValueDao.getDataFromId(lastId)
            .subscribeOn(Schedulers.io())

    fun updateGlucoseValue(glucoseValue :GlucoseValue) : Long =
        database.glucoseValueDao.updateExistingEntry(glucoseValue)
}