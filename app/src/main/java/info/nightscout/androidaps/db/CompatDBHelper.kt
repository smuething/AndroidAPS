package info.nightscout.androidaps.db

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.interfaces.DBEntry
import info.nightscout.androidaps.events.EventNewBG
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompatDBHelper @Inject constructor(
    val injector: HasAndroidInjector,
    val aapsLogger: AAPSLogger,
    val repository: AppRepository,
    val rxBus: RxBusWrapper
) {

    private val disposable = CompositeDisposable()

    private val bgWorker = Executors.newSingleThreadScheduledExecutor()
    private var scheduledBgPost: ScheduledFuture<*>? = null

    init {
        disposable.add(repository.changeObservable.observeOn(Schedulers.io()).subscribe { entries: List<DBEntry?> ->
            var changedGlucoseValue: GlucoseValue? = null
            entries.map { if (it is GlucoseValue) changedGlucoseValue = it }
            changedGlucoseValue?.let { scheduleBgChange(it) }
        })
    }

    fun triggerStart() {
        scheduleBgChange(null)
    }

    private fun scheduleBgChange(glucoseValue: GlucoseValue?) {
        class PostRunnable : Runnable {
            override fun run() {
                aapsLogger.debug(LTag.DATABASE, "Firing EventNewBg")
                rxBus.send(EventNewBG(glucoseValue))
                scheduledBgPost = null
            }
        }
        // prepare task for execution in 1 sec
        // cancel waiting task to prevent sending multiple posts
        scheduledBgPost?.cancel(false)
        scheduledBgPost = bgWorker.schedule(PostRunnable(), 1L, TimeUnit.SECONDS)
    }

}