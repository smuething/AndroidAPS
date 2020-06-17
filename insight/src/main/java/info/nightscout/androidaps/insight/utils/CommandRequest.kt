package info.nightscout.androidaps.insight.utils

import info.nightscout.androidaps.insight.commands.AppLayerCommand
import info.nightscout.androidaps.insight.data.enums.ErrorCode
import kotlinx.coroutines.CompletableDeferred

internal data class CommandRequest<T>(
    val command: AppLayerCommand<T>,
    private val deferred: CompletableDeferred<T>
) {
    fun completeExceptionally(e: CommandException) {
        deferred.completeExceptionally(e)
    }

    fun complete(body: ByteArray): T {
        val data = command.deserializeResponse(body)
        deferred.complete(data)
        return data
    }
}

sealed class CommandException : Exception {
    constructor() : super()
    constructor(message: String?) : super(message)
}
internal class NotConnectedException : CommandException()
internal class AppErrorException(val errorCode: ErrorCode): CommandException("Received error code: $errorCode")