package info.nightscout.androidaps.insight.utils

import info.nightscout.androidaps.insight.data.enums.EnumWithId
import info.nightscout.androidaps.insight.data.enums.resolve

internal fun ByteArray.getUShortLE(position: Int) =
    (this[position].toUInt() and 0xFFu or (this[position + 1].toUInt() and 0xFFu shl 8)).toUShort()

internal fun ByteArray.putUShortLE(position: Int, number: UShort) {
    this[position] = number.toByte()
    this[position + 1] = (number.toUInt() shr 8).toByte()
}

internal fun ByteArray.putUShortBE(position: Int, number: UShort) {
    this[position] = (number.toUInt() shr 8).toByte()
    this[position + 1] = number.toByte()
}

internal fun ByteArray.getUIntLE(position: Int) =
    this[position].toUInt() and 0xFFu or
        (this[position + 1].toUInt() and 0xFFu shl 8) or
        (this[position + 2].toUInt() and 0xFFu shl 16) or
        (this[position + 3].toUInt() and 0xFFu shl 24)

internal fun ByteArray.putUIntLE(position: Int, number: UInt) {
    this[position] = number.toByte()
    this[position + 1] = (number shr 8).toByte()
    this[position + 2] = (number shr 16).toByte()
    this[position + 3] = (number shr 24).toByte()
}

internal inline fun <reified T : Enum<T>> ByteArray.getEnum(position: Int) = resolve<T>(getUShortLE(position))

internal fun ByteArray.putEnum(position: Int, enum: EnumWithId) = putUShortLE(position, enum.id)