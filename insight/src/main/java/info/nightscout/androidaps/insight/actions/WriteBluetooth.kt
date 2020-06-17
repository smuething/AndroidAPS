package info.nightscout.androidaps.insight.actions

import info.nightscout.androidaps.insight.InsightHandles
import info.nightscout.androidaps.logging.LTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream

internal fun InsightHandles.writeBluetooth(outputStream: OutputStream) {
    val channel = Channel<ByteArray>(Channel.BUFFERED)
    writeChannel = channel
    jobRegistry.writeJob = scope.launch(Dispatchers.IO) {
        channel.consumeEach {
            try {
                outputStream.write(it)
                outputStream.flush()
            } catch (e: IOException) {
                execute {
                    logger.error(LTag.PUMPCOMM, "Connection lost", e)
                    jobRegistry.writeJob = null
                    reconnectIfRequested()
                }
            }
        }
    }
}