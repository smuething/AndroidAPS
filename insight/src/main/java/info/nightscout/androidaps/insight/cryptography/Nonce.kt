package info.nightscout.androidaps.insight.cryptography

internal inline class Nonce(val value: UByteArray) {

    operator fun inc(): Nonce {
        val copy = value.copyOf()
        for (i in copy.indices) {
            if (++copy[i] != 0u.toUByte()) {
                break
            }
        }
        return Nonce(copy)
    }

    operator fun compareTo(other: Nonce): Int {
        for (i in 0 until maxOf(value.size, other.value.size)) {
            val difference = (value.getOrNull(i)?.toInt() ?: 0) - (other.value.getOrNull(i)?.toInt() ?: 0)
            if (difference != 0) return difference
        }
        return 0
    }
}
