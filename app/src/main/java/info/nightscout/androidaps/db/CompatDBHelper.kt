package info.nightscout.androidaps.db

import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.events.EventNewBG
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import io.reactivex.disposables.Disposable
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompatDBHelper @Inject constructor(
    val aapsLogger: AAPSLogger,
    val repository: AppRepository,
    val rxBus: RxBusWrapper
) {

    fun triggerStart(): Disposable = repository
        .changeObservable()
        .map { it.filterIsInstance<GlucoseValue>() }
        .filter { it.isNotEmpty() }
        .map { it.last() }
        .debounce(1L, TimeUnit.SECONDS)
        .doOnSubscribe { rxBus.send(EventNewBG(null)) }
        .subscribe {
            aapsLogger.debug(LTag.DATABASE, "Firing EventNewBg")
            rxBus.send(EventNewBG(it))
        }
}