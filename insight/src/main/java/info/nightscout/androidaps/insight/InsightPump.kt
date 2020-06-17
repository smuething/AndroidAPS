package info.nightscout.androidaps.insight

import info.nightscout.androidaps.insight.actions.confirmVerificationCode
import info.nightscout.androidaps.insight.actions.registerStateChannel
import info.nightscout.androidaps.insight.actions.rejectVerificationCode
import info.nightscout.androidaps.insight.actions.requestConnection
import info.nightscout.androidaps.insight.actions.withdrawConnectionRequest
import info.nightscout.androidaps.insight.dagger.MacAddress
import info.nightscout.androidaps.insight.dagger.PerPump
import info.nightscout.androidaps.insight.data.PairingData
import kotlinx.coroutines.cancel
import javax.inject.Inject

@PerPump
internal class InsightPump @Inject constructor(
    @MacAddress
    val macAddress: String,
    private val handles: InsightHandles
) : AutoCloseable {

    override fun close() {
        handles.scope.cancel()
    }

    suspend fun registerStateChannel() = handles.execute { this@execute.registerStateChannel() }

    suspend fun requestConnection(lock: Any) = handles.execute { this@execute.requestConnection(lock) }

    suspend fun withdrawConnectionRequest(lock: Any) = handles.execute { this@execute.withdrawConnectionRequest(lock) }

    suspend fun getVerificationCode() = handles.execute { verificationCode }

    suspend fun confirmVerificationCode() = handles.execute { this@execute.confirmVerificationCode() }

    suspend fun rejectVerificationCode() = handles.execute { this@execute.rejectVerificationCode() }

    suspend fun setPairingDataCallback(callback: PairingData.Callback, pushCurrentValues: Boolean = false) = handles.execute {
        pairingData.callback = callback
        if (pushCurrentValues) {
            callback.lastSentNonceChanged(pairingData.lastReceivedNonce)
            callback.lastReceivedNonceChanged(pairingData.lastSentNonce)
            callback.commIdChanged(pairingData.commId)
            callback.incomingKeyChanged(pairingData.incomingKey)
            callback.outgoingKeyChanged(pairingData.outgoingKey)
        }
    }
}