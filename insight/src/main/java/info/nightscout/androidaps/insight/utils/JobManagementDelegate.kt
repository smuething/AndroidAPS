package info.nightscout.androidaps.insight.utils

import kotlinx.coroutines.Job
import kotlin.reflect.KProperty

internal class JobManagementDelegate {

    private var job: Job? = null

    operator fun getValue(thisRef: Any?, property: KProperty<*>) = this.job

    operator fun setValue(thisRef: Any?, property: KProperty<*>, job: Job?) {
        this.job?.cancel()
        this.job = job
    }
}