package info.nightscout.androidaps.insight.actions

import info.nightscout.androidaps.insight.InsightHandles
import info.nightscout.androidaps.insight.data.enums.InsightState
import info.nightscout.androidaps.insight.data.enums.QueueState
import info.nightscout.androidaps.logging.LTag

internal suspend fun InsightHandles.requestConnection(lock: Any) {
    logger.info(LTag.PUMPCOMM, "Connection requested by $lock")
    locks.add(lock)
    if (state == InsightState.DISCONNECTED) {
        if (bluetoothAdapter.isEnabled) {
            connect()
        } else {
            enableBluetooth()
        }
    }
}

internal suspend fun InsightHandles.withdrawConnectionRequest(lock: Any) {
    logger.info(LTag.PUMPCOMM, "Connection request withdrawn by $lock")
    locks.remove(lock)
    if (locks.isEmpty()) {
        if (state == InsightState.CONNECTED && queueState == QueueState.QUEUE_EMPTY) {
            sendDisconnectRequest()
        } else {
            cleanup()
            setState(InsightState.DISCONNECTED)
        }
    }
}