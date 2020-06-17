package info.nightscout.androidaps.insight.cryptography

import org.spongycastle.crypto.params.RSAKeyParameters
import org.spongycastle.crypto.params.RSAPrivateCrtKeyParameters

internal class KeyPair(
    val privateKey: RSAPrivateCrtKeyParameters,
    val publicKey: RSAKeyParameters
)