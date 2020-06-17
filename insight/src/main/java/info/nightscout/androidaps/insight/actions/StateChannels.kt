package info.nightscout.androidaps.insight.actions

import info.nightscout.androidaps.insight.InsightHandles
import info.nightscout.androidaps.insight.data.enums.InsightState
import info.nightscout.androidaps.logging.LTag
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch

internal suspend fun InsightHandles.registerStateChannel(): ReceiveChannel<InsightState> {
    logger.info(LTag.PUMPCOMM, "Registering state channel")
    val channel = Channel<InsightState>(Channel.UNLIMITED)
    stateChannels.add(channel)
    channel.invokeOnClose {
        scope.launch {
            execute {
                stateChannels.remove(channel)
            }
        }
    }
    return channel
}