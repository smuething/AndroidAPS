package info.nightscout.androidaps.insight.actions

import info.nightscout.androidaps.insight.InsightHandles
import info.nightscout.androidaps.insight.data.enums.InsightState
import info.nightscout.androidaps.logging.LTag
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal suspend fun InsightHandles.recover() {
    val duration = 1000L
    logger.info(LTag.PUMPCOMM, "Recovering for $duration ms")
    setState(InsightState.RECOVERING)
    jobRegistry.recoveryJob = scope.launch {
        //TODO: Implement proper recovery strategy.
        delay(duration)
        execute {
            jobRegistry.recoveryJob = null
            connect()
        }
    }
}