package info.nightscout.androidaps.insight.commands

import info.nightscout.androidaps.insight.data.enums.Service

internal abstract class AppLayerCommand<T> {

    abstract val service: Service
    abstract val command: UShort
    abstract val responseLength: Int?
    open val inCRC: Boolean = false
    open val outCRC: Boolean = false

    abstract fun serializeRequest(): ByteArray

    abstract fun deserializeResponse(data: ByteArray): T

    override fun toString() = this::class.simpleName ?: ""
}