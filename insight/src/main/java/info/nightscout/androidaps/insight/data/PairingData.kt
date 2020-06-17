package info.nightscout.androidaps.insight.data

import info.nightscout.androidaps.insight.cryptography.Nonce
import kotlin.properties.Delegates

internal class PairingData(
    lastSentNonce: Nonce = Nonce(UByteArray(0)),
    lastReceivedNonce: Nonce = Nonce(UByteArray(0)),
    commId: UInt = 0u,
    incomingKey: ByteArray = ByteArray(0),
    outgoingKey: ByteArray = ByteArray(0),
    var callback: Callback? = null
) {

    var lastSentNonce by Delegates.observable(lastSentNonce) { _, _, value -> callback?.lastSentNonceChanged(value) }
    var lastReceivedNonce by Delegates.observable(lastReceivedNonce) { _, _, value -> callback?.lastReceivedNonceChanged(value) }
    var commId by Delegates.observable(commId) { _, _, value -> callback?.commIdChanged(value) }
    var incomingKey by Delegates.observable(incomingKey) { _, _, value -> callback?.incomingKeyChanged(value) }
    var outgoingKey by Delegates.observable(outgoingKey) { _, _, value -> callback?.incomingKeyChanged(value) }

    interface Callback {
        fun lastSentNonceChanged(nonce: Nonce)
        fun lastReceivedNonceChanged(nonce: Nonce)
        fun commIdChanged(commId: UInt)
        fun incomingKeyChanged(byteArray: ByteArray)
        fun outgoingKeyChanged(byteArray: ByteArray)
    }
}