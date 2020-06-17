package info.nightscout.androidaps.insight.cryptography

import info.nightscout.androidaps.insight.utils.InsightException
import info.nightscout.androidaps.insight.utils.putUShortBE
import org.spongycastle.crypto.Digest
import org.spongycastle.crypto.InvalidCipherTextException
import org.spongycastle.crypto.digests.MD5Digest
import org.spongycastle.crypto.digests.SHA1Digest
import org.spongycastle.crypto.encodings.OAEPEncoding
import org.spongycastle.crypto.engines.RSAEngine
import org.spongycastle.crypto.engines.TwofishEngine
import org.spongycastle.crypto.generators.RSAKeyPairGenerator
import org.spongycastle.crypto.macs.HMac
import org.spongycastle.crypto.modes.CBCBlockCipher
import org.spongycastle.crypto.params.KeyParameter
import org.spongycastle.crypto.params.ParametersWithIV
import org.spongycastle.crypto.params.RSAKeyGenerationParameters
import org.spongycastle.crypto.params.RSAKeyParameters
import org.spongycastle.crypto.params.RSAPrivateCrtKeyParameters
import java.math.BigInteger
import java.security.SecureRandom
import kotlin.experimental.xor
import kotlin.math.min

internal object Cryptographer {

    private val VERIFICATION_STRING_TABLE = charArrayOf(
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',
        'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
        'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X',
        'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
        'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
        'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
        'w', 'x', 'y', 'z', '0', '1', '2', '3',
        '4', '5', '6', '7', '8', '9', '+', '/'
    )

    private val CRC_TABLE = intArrayOf(
        0x0000, 0x1189, 0x2312, 0x329b, 0x4624, 0x57ad, 0x6536, 0x74bf,
        0x8c48, 0x9dc1, 0xaf5a, 0xbed3, 0xca6c, 0xdbe5, 0xe97e, 0xf8f7,
        0x1081, 0x0108, 0x3393, 0x221a, 0x56a5, 0x472c, 0x75b7, 0x643e,
        0x9cc9, 0x8d40, 0xbfdb, 0xae52, 0xdaed, 0xcb64, 0xf9ff, 0xe876,
        0x2102, 0x308b, 0x0210, 0x1399, 0x6726, 0x76af, 0x4434, 0x55bd,
        0xad4a, 0xbcc3, 0x8e58, 0x9fd1, 0xeb6e, 0xfae7, 0xc87c, 0xd9f5,
        0x3183, 0x200a, 0x1291, 0x0318, 0x77a7, 0x662e, 0x54b5, 0x453c,
        0xbdcb, 0xac42, 0x9ed9, 0x8f50, 0xfbef, 0xea66, 0xd8fd, 0xc974,
        0x4204, 0x538d, 0x6116, 0x709f, 0x0420, 0x15a9, 0x2732, 0x36bb,
        0xce4c, 0xdfc5, 0xed5e, 0xfcd7, 0x8868, 0x99e1, 0xab7a, 0xbaf3,
        0x5285, 0x430c, 0x7197, 0x601e, 0x14a1, 0x0528, 0x37b3, 0x263a,
        0xdecd, 0xcf44, 0xfddf, 0xec56, 0x98e9, 0x8960, 0xbbfb, 0xaa72,
        0x6306, 0x728f, 0x4014, 0x519d, 0x2522, 0x34ab, 0x0630, 0x17b9,
        0xef4e, 0xfec7, 0xcc5c, 0xddd5, 0xa96a, 0xb8e3, 0x8a78, 0x9bf1,
        0x7387, 0x620e, 0x5095, 0x411c, 0x35a3, 0x242a, 0x16b1, 0x0738,
        0xffcf, 0xee46, 0xdcdd, 0xcd54, 0xb9eb, 0xa862, 0x9af9, 0x8b70,
        0x8408, 0x9581, 0xa71a, 0xb693, 0xc22c, 0xd3a5, 0xe13e, 0xf0b7,
        0x0840, 0x19c9, 0x2b52, 0x3adb, 0x4e64, 0x5fed, 0x6d76, 0x7cff,
        0x9489, 0x8500, 0xb79b, 0xa612, 0xd2ad, 0xc324, 0xf1bf, 0xe036,
        0x18c1, 0x0948, 0x3bd3, 0x2a5a, 0x5ee5, 0x4f6c, 0x7df7, 0x6c7e,
        0xa50a, 0xb483, 0x8618, 0x9791, 0xe32e, 0xf2a7, 0xc03c, 0xd1b5,
        0x2942, 0x38cb, 0x0a50, 0x1bd9, 0x6f66, 0x7eef, 0x4c74, 0x5dfd,
        0xb58b, 0xa402, 0x9699, 0x8710, 0xf3af, 0xe226, 0xd0bd, 0xc134,
        0x39c3, 0x284a, 0x1ad1, 0x0b58, 0x7fe7, 0x6e6e, 0x5cf5, 0x4d7c,
        0xc60c, 0xd785, 0xe51e, 0xf497, 0x8028, 0x91a1, 0xa33a, 0xb2b3,
        0x4a44, 0x5bcd, 0x6956, 0x78df, 0x0c60, 0x1de9, 0x2f72, 0x3efb,
        0xd68d, 0xc704, 0xf59f, 0xe416, 0x90a9, 0x8120, 0xb3bb, 0xa232,
        0x5ac5, 0x4b4c, 0x79d7, 0x685e, 0x1ce1, 0x0d68, 0x3ff3, 0x2e7a,
        0xe70e, 0xf687, 0xc41c, 0xd595, 0xa12a, 0xb0a3, 0x8238, 0x93b1,
        0x6b46, 0x7acf, 0x4854, 0x59dd, 0x2d62, 0x3ceb, 0x0e70, 0x1ff9,
        0xf78f, 0xe606, 0xd49d, 0xc514, 0xb1ab, 0xa022, 0x92b9, 0x8330,
        0x7bc7, 0x6a4e, 0x58d5, 0x495c, 0x3de3, 0x2c6a, 0x1ef1, 0x0f78
    )

    private const val KEY_SEED = "master secret"
    private const val VERIFICATION_SEED = "finished"
    private const val SERVICE_PASSWORD_SEED = "service pwd"

    fun generateKeyPair(): KeyPair {
        val generator = RSAKeyPairGenerator()
        generator.init(RSAKeyGenerationParameters(BigInteger.valueOf(65537L), SecureRandom(), 2048, 8))
        val ackp = generator.generateKeyPair()
        val private = ackp.private as RSAPrivateCrtKeyParameters
        val public = ackp.public as RSAKeyParameters
        return KeyPair(private, public)
    }

    private fun getHmac(secret: ByteArray, data: ByteArray, algorithm: Digest): ByteArray {
        val hmac = HMac(algorithm)
        hmac.init(KeyParameter(secret))
        val result = ByteArray(hmac.macSize)
        hmac.update(data, 0, data.size)
        hmac.doFinal(result, 0)
        return result
    }

    private fun getMultiHmac(secret: ByteArray, data: ByteArray, bytes: Int, algorithm: Digest): ByteArray {
        var nuData = data
        val output = ByteArray(bytes)
        var size = 0
        while (size < bytes) {
            nuData = getHmac(secret, nuData, algorithm)
            val preOutput = getHmac(secret, nuData + data, algorithm)
            preOutput.copyInto(output, size, 0, min(bytes - size, preOutput.size))
            size += preOutput.size
        }
        return output
    }

    private fun getMultiHashXOR(secret: ByteArray, seed: ByteArray, bytes: Int): ByteArray {
        val firstHalf = secret.copyOf(secret.size / 2)
        val secondHalf = secret.copyOfRange(secret.size / 2, secret.size)
        val md5 = getMultiHmac(firstHalf, seed, bytes, MD5Digest())
        val sha1 = getMultiHmac(secondHalf, seed, bytes, SHA1Digest())
        return md5 xor sha1
    }

    fun getServicePasswordHash(servicePassword: String, salt: ByteArray) = getMultiHashXOR(servicePassword.toByteArray(), SERVICE_PASSWORD_SEED.toByteArray() + salt, 16)

    fun deriveKeys(keyPair: KeyPair, keyRequest: ByteArray, keyResponse: ByteArray, preMasterSecret: ByteArray, localRandomBytes: ByteArray, peerRandomBytes: ByteArray): DerivedKeys {
        try {
            val rsaCipher = OAEPEncoding(RSAEngine())
            rsaCipher.init(false, keyPair.privateKey)
            val decryptedSecret = rsaCipher.processBlock(preMasterSecret, 0, preMasterSecret.size)
            val result = getMultiHashXOR(decryptedSecret, KEY_SEED.toByteArray() + localRandomBytes + peerRandomBytes, 32)
            val incomingKey = result.copyOf(16)
            val outgoingKey = result.copyOfRange(16, 32)
            val verificationString = getVerificationString(keyRequest, keyResponse, result)
            return DerivedKeys(incomingKey, outgoingKey, verificationString)
        } catch (e: InvalidCipherTextException) {
            throw InsightException("Invalid cipher text", e)
        }
    }

    private fun getVerificationString(keyRequest: ByteArray, keyResponse: ByteArray, key: ByteArray): String {
        val verificationData = getMultiHashXOR(key, VERIFICATION_SEED.toByteArray() + keyRequest + keyResponse, 8)
        var value: Long = 0
        for (i in 7 downTo 0) {
            var byteValue = verificationData[i].toLong()
            if (byteValue < 0) byteValue += 256
            value = value or (byteValue shl (i * 8))
        }
        val stringBuilder = StringBuilder()
        for (index in 0..9) {
            if (index == 3 || index == 6) stringBuilder.append(" ")
            stringBuilder.append(VERIFICATION_STRING_TABLE[value.toInt() and 63])
            value = value shr 6
        }
        return stringBuilder.toString()
    }

    fun calculateCRC(byteArray: ByteArray, start: Int = 0, end: Int = byteArray.size) = (start until end).fold(0xFFFF) { acc, i -> acc ushr 8 xor CRC_TABLE[acc xor byteArray[i].toInt() and 0xFF] }

    fun encryptOrDecrypt(data: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        require(key.size == 16) { "Invalid key length" }

        require(nonce.size == 13) { "Invalid nonce length" }
        val engine = TwofishEngine()
        engine.init(true, KeyParameter(key))

        val paddedLength = data.size + 16 - data.size % 16
        val result = ByteArray(paddedLength)

        val ctrBlock = ByteArray(16)
        ctrBlock[0] = 0x01
        nonce.copyInto(ctrBlock, 1)

        for (i in 0 until paddedLength / 16) {
            ctrBlock.putUShortBE(14, (i + 1).toUShort())
            engine.processBlock(ctrBlock, 0, result, i * 16)
        }

        return (data xor result).copyOf(data.size)
    }

    fun encryptOrDecrypt(data: ByteArray, key: ByteArray, nonce: Nonce) = encryptOrDecrypt(data, key, nonce.value.asByteArray())

    fun calculateMac(nonce: ByteArray, data: ByteArray, header: ByteArray, key: ByteArray): ByteArray {
        require(key.size == 16) { "Invalid key length" }
        require(nonce.size == 13) { "Invalid nonce length" }
        require(header.size == 21) { "Invalid header length" }

        val engine = TwofishEngine()
        val cbc = CBCBlockCipher(engine)
        val keyParameter = KeyParameter(key)
        engine.init(true, KeyParameter(key))

        val iv = ByteArray(16)
        iv[0] = 0x59
        nonce.copyInto(iv, 1)
        iv.putUShortBE(14, data.size.toUShort())
        engine.processBlock(iv, 0, iv, 0)

        cbc.init(true, ParametersWithIV(keyParameter, iv))

        val headerPrefix = ByteArray(2)
        headerPrefix.putUShortBE(0, header.size.toUShort())
        val authenticatedData = (headerPrefix + header).addZeroPadding() + data.addZeroPadding()

        val result = ByteArray(authenticatedData.size)
        for (i in 0 until authenticatedData.size / 16) {
            cbc.processBlock(authenticatedData, i * 16, result, i * 16)
        }

        val ctrBlock = ByteArray(16)
        ctrBlock[0] = 0x01
        nonce.copyInto(ctrBlock, 1)
        engine.processBlock(ctrBlock, 0, ctrBlock, 0)

        return result.copyOfRange(result.size - 16, result.size - 8) xor ctrBlock
    }

    fun calculateMac(nonce: Nonce, data: ByteArray, header: ByteArray, key: ByteArray) = calculateMac(nonce.value.asByteArray(), data, header, key)

    fun getModulus(keyPair: KeyPair) = keyPair.privateKey.modulus.toByteArray().copyOfRange(1, 257)

    private fun ByteArray.addZeroPadding(): ByteArray {
        val modulo = size % 16
        return if (modulo == 0) this
        else this + ByteArray(16 - modulo)
    }

    private infix fun ByteArray.xor(other: ByteArray): ByteArray {
        val length = min(size, other.size)
        val xor = ByteArray(length)
        for (i in 0 until length) {
            xor[i] = this[i] xor other[i]
        }
        return xor
    }
}