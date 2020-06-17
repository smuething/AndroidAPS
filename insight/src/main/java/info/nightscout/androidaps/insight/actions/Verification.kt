package info.nightscout.androidaps.insight.actions

import info.nightscout.androidaps.insight.InsightHandles
import info.nightscout.androidaps.insight.data.enums.InsightState
import info.nightscout.androidaps.logging.LTag
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val VERIFICATION_HEARTBEAT_INTERVAL = 1000L

internal suspend fun InsightHandles.setVerificationHeartbeatTimer() {
    jobRegistry.verificationHeartbeatJob = scope.launch {
        logger.info(LTag.PUMPCOMM, "Sending next VERIFY_CONFIRM_REQUEST in $VERIFICATION_HEARTBEAT_INTERVAL ms")
        delay(VERIFICATION_HEARTBEAT_INTERVAL)
        execute {
            jobRegistry.verificationHeartbeatJob = null
            sendVerifyConfirmRequest()
        }
    }
}

internal suspend fun InsightHandles.confirmVerificationCode() {
    if (state == InsightState.WAITING_FOR_CODE_CONFIRMATION) {
        logger.info(LTag.PUMPCOMM, "Verification code confirmed")
        verificationCode = null
        sendVerifyConfirmRequest()
    }
}

internal suspend fun InsightHandles.rejectVerificationCode() {
    if (state == InsightState.WAITING_FOR_CODE_CONFIRMATION) {
        logger.info(LTag.PUMPCOMM, "Verification code rejected")
        cleanup()
        setState(InsightState.DISCONNECTED)
    }
}