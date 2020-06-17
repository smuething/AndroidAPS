package info.nightscout.androidaps.insight.utils

private const val HEX_CHARS = "0123456789ABCDEF"

internal fun ByteArray.toHexString(): String {
    val stringBuilder = StringBuilder()
    for (i in indices) {
        val byte = this[i].toInt()
        stringBuilder.append(HEX_CHARS[byte ushr 4 and 0x0F])
        stringBuilder.append(HEX_CHARS[byte and 0x0F])
    }
    return stringBuilder.toString()
}

internal fun String.decodeHex(): ByteArray {
    val byteArray = ByteArray(length / 2)
    for (i in 0 until length / 2) {
        byteArray[i] = (HEX_CHARS.indexOf(this[i].toUpperCase()) shl 4 or (HEX_CHARS.indexOf(this[i + 1].toUpperCase()))).toByte()
    }
    return byteArray
}