package info.nightscout.androidaps.insight.data

import info.nightscout.androidaps.insight.cryptography.Nonce

internal class PairingDataParameter(
    var lastSentNonce: Nonce,
    var lastReceivedNonce: Nonce,
    var commId: UInt,
    var incomingKey: ByteArray,
    var outgoingKey: ByteArray
) {

    private val isValid
        get() = lastSentNonce.value.size == 13 &&
            lastReceivedNonce.value.size == 13 &&
            incomingKey.size == 16 &&
            outgoingKey.size == 16

    init {
        if (!isValid) {
            throw IllegalArgumentException("Invalid pairing data")
        }
    }
}