package info.nightscout.androidaps.db

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.database.interfaces.DBEntry
import info.nightscout.androidaps.events.EventNewBG
import info.nightscout.androidaps.events.EventTempTargetChange
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

    init {
        disposable.add(repository.changeObservable.observeOn(Schedulers.io()).subscribe { entries: List<DBEntry> ->
            entries.filterIsInstance<GlucoseValue>().lastOrNull()?.let { rxBus.send(EventNewBG(it)) }
            if (entries.filterIsInstance<TemporaryTarget>().isNotEmpty()) rxBus.send(EventTempTargetChange())
        })
    }

    fun triggerStart() {
        rxBus.send(EventNewBG(null))
        rxBus.send(EventTempTargetChange())
    }
}