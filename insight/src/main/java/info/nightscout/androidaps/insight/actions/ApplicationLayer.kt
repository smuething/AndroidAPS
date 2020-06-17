package info.nightscout.androidaps.insight.actions

import info.nightscout.androidaps.insight.InsightHandles
import info.nightscout.androidaps.insight.commands.AppLayerCommand
import info.nightscout.androidaps.insight.cryptography.Cryptographer
import info.nightscout.androidaps.insight.data.enums.ErrorCode
import info.nightscout.androidaps.insight.data.enums.InsightState
import info.nightscout.androidaps.insight.data.enums.QueueState
import info.nightscout.androidaps.insight.data.enums.Service
import info.nightscout.androidaps.insight.utils.AppErrorException
import info.nightscout.androidaps.insight.utils.InsightException
import info.nightscout.androidaps.insight.utils.getUShortLE
import info.nightscout.androidaps.insight.utils.putUShortLE
import info.nightscout.androidaps.insight.utils.toHexString
import info.nightscout.androidaps.logging.LTag

private const val VERSION: UByte = 32u
private const val CONNECT: UShort = 61451u
private const val DISCONNECT: UShort = 61460u
private const val ACTIVATE_SERVICE: UShort = 61687u
private const val BIND: UShort = 62413u
private const val SERVICE_CHALLENGE: UShort = 62418u
private val MODEL_NUMBER = "mobile client".toByteArray().copyOf(68)

private const val REGULAR_TERMINATION: UShort = 24579u
private const val STRIP_INSERTION: UShort = 24581u
private const val INACTIVITY_TIMEOUT: UShort = 24582u
private const val ERROR_ALARM: UShort = 24585u
private const val MANAGER_COMMUNICATION: UShort = 24586u

private suspend fun InsightHandles.sendAppMessage(service: Service, command: UShort, data: ByteArray, crc: Boolean = false) {
    val byteArray = ByteArray(data.size + if (crc) 6 else 4)
    byteArray[0] = VERSION.toByte()
    byteArray[1] = service.id.toByte()
    byteArray.putUShortLE(2, command)
    data.copyInto(byteArray, 4)
    if (crc) {
        byteArray.putUShortLE(data.size + 4, Cryptographer.calculateCRC(data).toUShort())
    }
    sendDataMessage(byteArray)
}

internal suspend fun InsightHandles.sendAppBindRequest() {
    logger.info(LTag.PUMPCOMM, "Sending BIND request)")
    setState(InsightState.APP_BIND)
    sendAppMessage(Service.CONNECTION, BIND, MODEL_NUMBER)
}

internal suspend fun InsightHandles.sendAppConnectRequest() {
    logger.info(LTag.PUMPCOMM, "Sending CONNECT request)")
    setState(InsightState.APP_CONNECT)
    sendAppMessage(Service.CONNECTION, CONNECT, ByteArray(8))
}

internal suspend fun InsightHandles.sendDisconnectRequest() {
    logger.info(LTag.PUMPCOMM, "Sending DISCONNECT request")
    setState(InsightState.APP_DISCONNECT)
    sendAppMessage(Service.CONNECTION, DISCONNECT, ByteArray(2).apply { putUShortLE(0, REGULAR_TERMINATION) })
}

internal suspend fun InsightHandles.sendActivateServiceRequest(service: Service, salt: ByteArray? = null) {
    logger.info(LTag.PUMPCOMM, "Sending ACTIVATE_SERVICE request (service=$service, salt=${salt?.toHexString()})")
    val data = ByteArray(19)
    data[0] = service.id.toByte()
    data[1] = service.versionMajor.toByte()
    data[2] = service.versionMinor.toByte()
    if (salt != null) {
        Cryptographer.getServicePasswordHash(service.password!!, salt).copyInto(data, 3)
    }
    queueState = QueueState.ACTIVATING_SERVICE
    sendAppMessage(Service.CONNECTION, ACTIVATE_SERVICE, data)
}

internal suspend fun InsightHandles.sendServiceChallengeRequest(service: Service) {
    logger.info(LTag.PUMPCOMM, "Sending SERVICE_CHALLENGE request ($service)")
    val data = ByteArray(3)
    data[0] = service.id.toByte()
    data[1] = service.versionMajor.toByte()
    data[2] = service.versionMinor.toByte()
    queueState = QueueState.SERVICE_CHALLENGE
    sendAppMessage(Service.CONNECTION, SERVICE_CHALLENGE, data)
}

internal suspend fun InsightHandles.sendAppCommand(command: AppLayerCommand<*>) {
    logger.info(LTag.PUMPCOMM, "Sending generic command request ($command)")
    queueState = QueueState.WAITING_FOR_RESPONSE
    sendAppMessage(command.service, command.command, command.serializeRequest(), command.outCRC)
}

internal suspend fun InsightHandles.processDataMessage(data: ByteArray) {
    when (state) {
        InsightState.APP_BIND       -> processBindResponse(data)
        InsightState.APP_CONNECT    -> processConnectResponse(data)
        InsightState.APP_DISCONNECT -> processDisconnectResponse(data)

        InsightState.CONNECTED      -> {
            when (queueState) {
                QueueState.SERVICE_CHALLENGE    -> processServiceChallengeResponse(data)
                QueueState.ACTIVATING_SERVICE   -> processActivateServiceResponse(data)
                QueueState.WAITING_FOR_RESPONSE -> processCommandResponse(data)
                else                            -> throw InsightException("Wrong state")
            }
        }

        else                        -> throw InsightException("Wrong state")
    }
}

private suspend fun checkAppMessage(data: ByteArray, service: Service, command: UShort, dataLength: Int?, crc: Boolean = false, errorBlock: (suspend (ErrorCode) -> Unit)? = null, successBlock: (suspend (ByteArray) -> Unit)? = null) {
    if (data.size < if (crc) 8 else 6) throw InsightException("Invalid payload length")
    val receivedVersion = data[0].toUByte()
    val receivedService = data[1].toUByte()
    val receivedCommand = data.getUShortLE(2)
    val receivedError = data.getUShortLE(4)
    when {
        receivedVersion != VERSION                                                                           -> throw InsightException("Invalid version: $receivedVersion")
        receivedService != service.id                                                                        -> throw InsightException("Invalid service: ${service.id}")
        receivedCommand != command                                                                           -> throw InsightException("Invalid command: $receivedCommand")

        receivedError != 0u.toUShort()                                                                       -> {
            val error = ErrorCode.values().firstOrNull { it.id == receivedError }
                ?: throw InsightException("Unknown error code: $receivedError")
            errorBlock?.invoke(error) ?: throw InsightException("Received error: $error")
        }

        dataLength != null && data.size - (if (crc) 8 else 6) != dataLength                                  -> throw InsightException("Invalid payload length")

        crc && data.getUShortLE(data.size - 2).toInt() != Cryptographer.calculateCRC(data, 6, data.size - 2) -> throw InsightException("Invalid CRC")

        else                                                                                                 -> successBlock?.invoke(data.copyOfRange(6, data.size - if (crc) 2 else 0))
    }
}

private suspend fun InsightHandles.processBindResponse(data: ByteArray) {
    checkAppMessage(data, Service.CONNECTION, BIND, 16)
    logger.info(LTag.PUMPCOMM, "Received BIND response")
    setState(InsightState.CONNECTED)
}

private suspend fun InsightHandles.processConnectResponse(data: ByteArray) {
    checkAppMessage(data, Service.CONNECTION, CONNECT, 8)
    logger.info(LTag.PUMPCOMM, "Received CONNECT response")
    setState(InsightState.CONNECTED)
}

private suspend fun InsightHandles.processDisconnectResponse(data: ByteArray) {
    checkAppMessage(data, Service.CONNECTION, DISCONNECT, 0)
    logger.info(LTag.PUMPCOMM, "Received DISCONNECT response")
    reconnectIfRequested()
}

private suspend fun InsightHandles.processServiceChallengeResponse(data: ByteArray) {
    checkAppMessage(data, Service.CONNECTION, SERVICE_CHALLENGE, 16) { body ->
        logger.info(LTag.PUMPCOMM, "Received SERVICE_CHALLENGE response (salt: ${body.toHexString()})")
        sendActivateServiceRequest(queue.first().command.service, body)
    }
}

private suspend fun InsightHandles.processActivateServiceResponse(data: ByteArray) {
    checkAppMessage(data, Service.CONNECTION, ACTIVATE_SERVICE, 3) { body ->
        val id = body[0].toUByte()
        val versionMajor = body[1].toUByte()
        val versionMinor = body[2].toUByte()
        val command = queue.first().command
        if (command.service.id != id) throw InsightException("Invalid service id: $id")
        logger.info(LTag.PUMPCOMM, "Received ACTIVATE_SERVICE response (id: $id, versionMajor: $versionMajor, versionMinor: $versionMinor)")
        activatedServices.add(command.service)
        sendAppCommand(command)
    }
}

private suspend fun InsightHandles.processCommandResponse(data: ByteArray) {
    val request = queue.first()
    val command = request.command
    checkAppMessage(data, command.service, command.command, command.responseLength, command.inCRC,
        errorBlock = {
            logger.info(LTag.PUMPCOMM, "Received generic command error: $it")
            request.completeExceptionally(AppErrorException(it))
        },
        successBlock = {
            logger.info(LTag.PUMPCOMM, "Received generic command response ($it)")
            request.complete(it)
        })
    queue.removeAt(0)
    if (locks.isEmpty()) {
        sendDisconnectRequest()
    } else {
        sendNextRequest()
    }
}