package info.nightscout.androidaps.insight.cryptography

internal data class DerivedKeys(
    val incomingKey: ByteArray,
    val outgoingKey: ByteArray,
    val verificationString: String
)