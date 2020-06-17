package info.nightscout.androidaps.insight.actions

import info.nightscout.androidaps.insight.InsightHandles
import info.nightscout.androidaps.insight.utils.InsightException
import info.nightscout.androidaps.logging.LTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream

private const val BUFFER_SIZE = 1024

internal fun InsightHandles.readBluetooth(inputStream: InputStream) {
    jobRegistry.readJob = scope.launch(Dispatchers.IO) {
        val internalBuffer = ByteArray(BUFFER_SIZE)
        try {
            while (true) {
                val read = inputStream.read(internalBuffer)
                if (read == -1) throw IOException("Socket closed")
                execute {
                    if (buffer.position + read >= buffer.content.size) {
                        throw InsightException("Buffer overflow")
                    }
                    internalBuffer.copyInto(buffer.content, buffer.position, 0, read)
                    buffer.position += read
                    processBytesInBuffer()
                }
            }
        } catch (e: IOException) {
            execute {
                logger.error(LTag.PUMPCOMM, "Connection lost", e)
                jobRegistry.readJob = null
                reconnectIfRequested()
            }
        }
    }
}

