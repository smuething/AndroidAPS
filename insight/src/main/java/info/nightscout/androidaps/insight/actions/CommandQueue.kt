package info.nightscout.androidaps.insight.actions

import info.nightscout.androidaps.insight.InsightHandles
import info.nightscout.androidaps.insight.commands.AppLayerCommand
import info.nightscout.androidaps.insight.data.enums.InsightState
import info.nightscout.androidaps.insight.data.enums.QueueState
import info.nightscout.androidaps.insight.utils.CommandRequest
import info.nightscout.androidaps.insight.utils.NotConnectedException
import info.nightscout.androidaps.logging.LTag
import kotlinx.coroutines.CompletableDeferred

internal suspend fun InsightHandles.sendNextRequest() {
    if (queue.isNotEmpty()) {
        val command = queue.first().command
        when {
            activatedServices.contains(command.service) -> sendAppCommand(command)
            command.service.password == null            -> sendActivateServiceRequest(command.service)
            else                                        -> sendServiceChallengeRequest(command.service)
        }
    } else {
        queueState = QueueState.QUEUE_EMPTY
    }
}

internal suspend fun <T> InsightHandles.requestCommand(command: AppLayerCommand<T>, deferred: CompletableDeferred<T>) {
    if (state == InsightState.CONNECTED) {
        logger.info(LTag.PUMPCOMM, "Enqueuing command ($command)")
        val commandRequest = CommandRequest(command, deferred)
        queue.add(commandRequest)
        if (queueState == QueueState.QUEUE_EMPTY) {
            sendNextRequest()
        }
    } else {
        deferred.completeExceptionally(NotConnectedException())
    }
}