package info.nightscout.androidaps.insight.actions

import android.util.Log
import info.nightscout.androidaps.insight.InsightHandles
import info.nightscout.androidaps.insight.cryptography.Cryptographer
import info.nightscout.androidaps.insight.cryptography.Nonce
import info.nightscout.androidaps.insight.data.enums.InsightState
import info.nightscout.androidaps.insight.data.enums.SatlError
import info.nightscout.androidaps.insight.utils.*
import info.nightscout.androidaps.logging.LTag
import org.spongycastle.util.encoders.Hex
import org.threeten.bp.ZonedDateTime
import kotlin.random.Random

private val PREAMBLE = byteArrayOf(0x88.toByte(), 0xCC.toByte(), 0xEE.toByte(), 0xFF.toByte())
private const val VERSION: UByte = 32u
private const val MINIMUM_PACKET_SIZE = 37

private const val DATA: UByte = 3u
private const val ERROR: UByte = 6u
private const val CONNECTION_REQUEST: UByte = 9u
private const val CONNECTION_RESPONSE: UByte = 10u
private const val KEY_REQUEST: UByte = 12u
private const val VERIFY_CONFIRM_REQUEST: UByte = 14u
private const val KEY_RESPONSE: UByte = 17u
private const val VERIFY_DISPLAY_REQUEST: UByte = 18u
private const val VERIFY_DISPLAY_RESPONSE: UByte = 20u
private const val SYN_REQUEST: UByte = 23u
private const val SYN_ACK_RESPONSE: UByte = 24u
private const val VERIFY_CONFIRM_RESPONSE: UByte = 30u

private val PENDING: UShort = 1683u
private val REJECTED: UShort = 7850u
private val CONFIRMED: UShort = 11835u

internal suspend fun InsightHandles.processBytesInBuffer() {
    logger.info(LTag.PUMPBTCOMM, "Received data, current buffer: ${buffer.content.copyOf(buffer.position).toHexString()}")
    while (buffer.position >= MINIMUM_PACKET_SIZE && buffer.position >= buffer.content.getUShortLE(4).toInt() + 8) {
        jobRegistry.timeoutJob?.cancel()
        jobRegistry.timeoutJob = null
        processSatlMessage()
    }
    if (buffer.position > 0) {
        logger.info(LTag.PUMPCOMM, "Buffer is not empty, setting timeout")
        jobRegistry.timeoutJob?.cancel()
        jobRegistry.timeoutJob = null
        setTimeout()
    }
}

private suspend fun InsightHandles.processSatlMessage() {
    val packetLength = buffer.content.getUShortLE(4).toInt()
    val byteArray = buffer.getAndRemove(packetLength + 8)
    logger.info(LTag.PUMPBTCOMM, "Received SATL packet: ${byteArray.toHexString()}")
    val preamble = byteArray.copyOf(4)
    val packetLengthXor = byteArray.getUShortLE(6).toInt() xor 0xFFFF
    val version = byteArray[8].toUByte()
    val command = byteArray[9].toUByte()
    val dataLength = byteArray.getUShortLE(10).toInt()
    if (dataLength + 29 != packetLength) throw InsightException("Invalid payload length")
    val commId = byteArray.getUIntLE(12)
    val nonce = Nonce(byteArray.copyOfRange(16, 29).asUByteArray())
    val mac = byteArray.copyOfRange(packetLength, packetLength + 8)
    val data = if (pairingData.incomingKey.isEmpty()) {
        val receivedCRC = byteArray.getUShortLE(27 + dataLength).toInt()
        val calculatedCRC = Cryptographer.calculateCRC(byteArray, 8, byteArray.size - 10)
        if (receivedCRC != calculatedCRC) throw InsightException("Invalid CRC")
        byteArray.copyOfRange(29, 27 + dataLength)
    } else {
        if (nonce <= pairingData.lastReceivedNonce) throw InsightException("Invalid nonce")
        val decrypted = Cryptographer.encryptOrDecrypt(byteArray.copyOfRange(29, 29 + dataLength), pairingData.incomingKey, nonce)
        val calculatedMac = Cryptographer.calculateMac(nonce, decrypted, byteArray.copyOfRange(8, 29), pairingData.incomingKey)
        if (!mac.contentEquals(calculatedMac)) throw InsightException("Invalid mac")
        decrypted
    }
    if (!preamble.contentEquals(PREAMBLE)) throw InsightException("Invalid preamble: ${preamble.toHexString()}")
    if (packetLength != packetLengthXor) throw InsightException("Packet length xor mismatch")
    if (version != VERSION) throw InsightException("Invalid version: $version")
    if (pairingData.incomingKey.isNotEmpty() && commId != pairingData.commId) throw InsightException("Comm id mismatch")
    when (command) {
        SYN_ACK_RESPONSE        -> processSynAckResponse(data)
        CONNECTION_RESPONSE     -> processConnectionResponse(data)
        KEY_RESPONSE            -> processKeyResponse(data, byteArray, commId)
        VERIFY_DISPLAY_RESPONSE -> processVerifyDisplayResponse(data)
        VERIFY_CONFIRM_RESPONSE -> processVerifyConfirmResponse(data)
        ERROR                   -> processErrorMessage(data)
        DATA                    -> processDataMessage(data)
        else                    -> throw InsightException("Unknown command: $command")
    }
}

private fun processErrorMessage(data: ByteArray): Nothing {
    if (data.size != 1) throw InsightException("Invalid payload length")
    val id = data[0].toUByte()
    val error = SatlError.values().firstOrNull { it.id == id }
        ?: throw InsightException("Unknown error code: $id")
    throw InsightException("Received error message: $error")
}

private suspend fun InsightHandles.processSynAckResponse(data: ByteArray) {
    logger.info(LTag.PUMPCOMM, "Received SYN_ACK_RESPONSE")
    if (data.isNotEmpty()) throw InsightException("Invalid payload length")
    if (state != InsightState.SYN_REQUEST) throw InsightException("Wrong state")
    setState(InsightState.APP_CONNECT)
    sendAppConnectRequest()
}

private suspend fun InsightHandles.processConnectionResponse(data: ByteArray) {
    logger.info(LTag.PUMPCOMM, "Received CONNECTION_RESPONSE")
    if (data.size != 1) throw InsightException("Invalid payload length")
    if (data[0].toUInt() != 0u) throw InsightException("Incompatible version")
    if (state != InsightState.CONNECTION_REQUEST) throw InsightException("Wrong state")
    val keyPair = Cryptographer.generateKeyPair().also { this.keyPair = it }
    val randomData = Random.nextBytes(28).also { this.randomData = it }
    keyRequest = sendKeyRequest(randomData, Cryptographer.getModulus(keyPair))
}

private suspend fun InsightHandles.processKeyResponse(data: ByteArray, fullPacket: ByteArray, commId: UInt) {
    logger.info(LTag.PUMPCOMM, "Received KEY_RESPONSE")
    if (data.size != 288) throw InsightException("Invalid payload length")
    if (state != InsightState.KEY_REQUEST) throw InsightException("Wrong state")
    val randomData = data.copyOf(28)
    val preMasterSecret = data.copyOfRange(32, 288)
    val satlContent = fullPacket.copyOfRange(8, fullPacket.size - 8)
    val (incomingKey, outgoingKey, verificationString) = Cryptographer.deriveKeys(
        keyPair = keyPair!!,
        keyRequest = keyRequest!!,
        keyResponse = satlContent,
        preMasterSecret = preMasterSecret,
        localRandomBytes = this.randomData!!,
        peerRandomBytes = randomData
    )
    this.keyRequest = null
    this.randomData = null
    this.keyPair = null
    this.pairingData.incomingKey = incomingKey
    this.pairingData.outgoingKey = outgoingKey
    this.pairingData.commId = commId
    this.pairingData.lastSentNonce = Nonce(UByteArray(13))
    this.pairingData.lastReceivedNonce = Nonce(UByteArray(13))
    this.verificationCode = verificationString
    sendVerifyDisplayRequest()
}

private suspend fun InsightHandles.processVerifyDisplayResponse(data: ByteArray) {
    logger.info(LTag.PUMPCOMM, "Received VERIFY_DISPLAY_RESPONSE")
    if (data.isNotEmpty()) throw InsightException("Invalid payload length")
    if (state != InsightState.VERIFY_DISPLAY_REQUEST) throw InsightException("Wrong state")
    setState(InsightState.WAITING_FOR_CODE_CONFIRMATION)
}

private suspend fun InsightHandles.processVerifyConfirmResponse(data: ByteArray) {
    if (data.size != 2) throw InsightException("Invalid payload length")
    if (state != InsightState.VERIFY_CONFIRM_REQUEST) throw InsightException("Wrong state")
    jobRegistry.verificationHeartbeatJob?.cancel()
    jobRegistry.verificationHeartbeatJob = null
    when (data.getUShortLE(0)) {
        CONFIRMED -> {
            logger.info(LTag.PUMPCOMM, "Verification code confirmed by remote")
            sendAppBindRequest()
        }

        REJECTED  -> {
            throw InsightException("Verification code rejected")
        }

        PENDING   -> {
            logger.info(LTag.PUMPCOMM, "Verification is still pending")
            setVerificationHeartbeatTimer()
        }

        else      -> throw InsightException("Invalid pairing status")
    }
}

private suspend fun InsightHandles.sendSatlMessage(command: UByte, data: ByteArray, commId: UInt? = null): ByteArray {
    Log.d("Sent payload", Hex.toHexString(data))
    val encrypted = pairingData.outgoingKey.isNotEmpty()
    val preparedData: ByteArray
    val nonce: Nonce
    if (encrypted) {
        nonce = ++pairingData.lastSentNonce
        preparedData = Cryptographer.encryptOrDecrypt(data, pairingData.outgoingKey, nonce)
    } else {
        nonce = Nonce(UByteArray(13))
        preparedData = data
    }
    val packet = ByteArray(preparedData.size + if (encrypted) 37 else 39)
    PREAMBLE.copyInto(packet)
    val length = (preparedData.size + if (encrypted) 29 else 31).toUShort()
    packet.putUShortLE(4, length)
    packet.putUShortLE(6, length xor 0xFFFFu)
    packet[8] = VERSION.toByte()
    packet[9] = command.toByte()
    packet.putUShortLE(10, (data.size + if (encrypted) 0 else 2).toUShort())
    packet.putUIntLE(12, commId ?: pairingData.commId)
    nonce.value.asByteArray().copyInto(packet, 16)
    preparedData.copyInto(packet, 29)
    if (encrypted) {
        Cryptographer.calculateMac(nonce, data, packet.copyOfRange(8, 29), pairingData.outgoingKey)
            .copyInto(packet, 29 + preparedData.size)
    } else {
        packet.putUShortLE(preparedData.size + 29, Cryptographer.calculateCRC(packet, 8, packet.size - 10).toUShort())
    }
    logger.info(LTag.PUMPBTCOMM, "Sending SATL packet: ${packet.toHexString()}")
    writeChannel!!.send(packet)
    setTimeout()
    return packet.copyOfRange(8, packet.size - 8)
}

internal suspend fun InsightHandles.sendConnectionRequest() {
    logger.info(LTag.PUMPCOMM, "Sending CONNECTION_REQUEST")
    setState(InsightState.CONNECTION_REQUEST)
    sendSatlMessage(CONNECTION_REQUEST, ByteArray(0), 0u)
}

internal suspend fun InsightHandles.sendKeyRequest(randomBytes: ByteArray, modulus: ByteArray): ByteArray {
    logger.info(LTag.PUMPCOMM, "Sending KEY_REQUEST")
    val byteArray = ByteArray(288)
    randomBytes.copyInto(byteArray)
    val translatedDate = with(ZonedDateTime.now()) {
        second.toUInt() or (minute.toUInt() shl 6) or (hour.toUInt() shl 12) or
            (dayOfMonth.toUInt() shl 17) or (monthValue.toUInt() shl 22) or (year.toUInt().rem(100u) shl 26)
    }
    byteArray.putUIntLE(28, translatedDate)
    modulus.copyInto(byteArray, 32)
    setState(InsightState.KEY_REQUEST)
    return sendSatlMessage(KEY_REQUEST, byteArray, 1u)
}

internal suspend fun InsightHandles.sendVerifyDisplayRequest() {
    logger.info(LTag.PUMPCOMM, "Sending VERIFY_DISPLAY_REQUEST")
    setState(InsightState.VERIFY_DISPLAY_REQUEST)
    sendSatlMessage(VERIFY_DISPLAY_REQUEST, ByteArray(0))
}

internal suspend fun InsightHandles.sendVerifyConfirmRequest() {
    logger.info(LTag.PUMPCOMM, "Sending VERIFY_CONFIRM_REQUEST")
    setState(InsightState.VERIFY_CONFIRM_REQUEST)
    sendSatlMessage(VERIFY_CONFIRM_REQUEST, ByteArray(2).apply { putUShortLE(0, CONFIRMED) })
}

internal suspend fun InsightHandles.sendSynRequest(){
    logger.info(LTag.PUMPCOMM, "Sending SYN_REQUEST")
    setState(InsightState.SYN_REQUEST)
    sendSatlMessage(SYN_REQUEST, ByteArray(0))
}

internal suspend fun InsightHandles.sendDataMessage(data: ByteArray) {
    logger.info(LTag.PUMPCOMM, "Sending DATA_MESSAGE: ${data.toHexString()}")
    sendSatlMessage(DATA, data)
}