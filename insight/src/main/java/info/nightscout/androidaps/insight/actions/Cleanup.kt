package info.nightscout.androidaps.insight.actions

import info.nightscout.androidaps.insight.InsightHandles
import info.nightscout.androidaps.insight.data.PairingData
import info.nightscout.androidaps.insight.data.enums.QueueState
import info.nightscout.androidaps.insight.utils.NotConnectedException
import info.nightscout.androidaps.insight.utils.closeSilently
import info.nightscout.androidaps.logging.LTag

internal fun InsightHandles.cleanup() {
    logger.info(LTag.PUMPCOMM, "Cleaning up")
    jobRegistry.cancelAll()
    bluetoothSocket?.closeSilently()
    bluetoothSocket = null
    buffer.position = 0
    writeChannel?.close()
    writeChannel = null
    if (!isPaired) {
        pairingData = PairingData()
    }
    keyPair = null
    randomData = null
    keyRequest = null
    verificationCode = null
    queueState = QueueState.QUEUE_EMPTY
    activatedServices.clear()
    val exception = NotConnectedException()
    queue.forEach { request ->
        request.completeExceptionally(exception)
    }
    queue.clear()
}