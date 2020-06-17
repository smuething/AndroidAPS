package info.nightscout.androidaps.insight.utils

import info.nightscout.androidaps.insight.dagger.PerPump
import kotlinx.coroutines.Job
import javax.inject.Inject

@PerPump
internal class JobRegistry @Inject constructor() {
    var enableBluetoothJob: Job? = null
    var connectJob: Job? = null
    var readJob: Job? = null
    var writeJob: Job? = null
    var recoveryJob: Job? = null
    var timeoutJob: Job? = null
    var verificationHeartbeatJob: Job? = null

    fun cancelAll() {
        enableBluetoothJob?.cancel()
        enableBluetoothJob = null
        connectJob?.cancel()
        connectJob = null
        readJob?.cancel()
        readJob = null
        writeJob?.cancel()
        writeJob = null
        recoveryJob?.cancel()
        recoveryJob = null
        timeoutJob?.cancel()
        timeoutJob = null
        verificationHeartbeatJob?.cancel()
        verificationHeartbeatJob = null
    }
}