package info.nightscout.androidaps.insight.actions

import info.nightscout.androidaps.insight.InsightHandles
import info.nightscout.androidaps.insight.utils.InsightException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TIMEOUT = 3000L

internal fun InsightHandles.setTimeout() {
    jobRegistry.timeoutJob = scope.launch {
        delay(TIMEOUT)
        execute<Nothing> {
            jobRegistry.timeoutJob = null
            throw InsightException("Timeout")
        }
    }
}