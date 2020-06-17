package info.nightscout.androidaps.insight

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import info.nightscout.androidaps.insight.actions.reconnectIfRequested
import info.nightscout.androidaps.insight.cryptography.KeyPair
import info.nightscout.androidaps.insight.dagger.PerPump
import info.nightscout.androidaps.insight.data.PairingData
import info.nightscout.androidaps.insight.data.PairingDataParameter
import info.nightscout.androidaps.insight.data.enums.InsightState
import info.nightscout.androidaps.insight.data.enums.QueueState
import info.nightscout.androidaps.insight.data.enums.Service
import info.nightscout.androidaps.insight.utils.Buffer
import info.nightscout.androidaps.insight.utils.CommandRequest
import info.nightscout.androidaps.insight.utils.InsightException
import info.nightscout.androidaps.insight.utils.JobRegistry
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * This class holds all handles that are needed for communicating with a pump.
 * You should only touch them while holding a lock on [mutex] using [execute].
 */
@PerPump
internal class InsightHandles @Inject constructor(
    val scope: CoroutineScope,
    val context: Context,
    pairingData: PairingDataParameter?,
    val logger: AAPSLogger,
    val bluetoothAdapter: BluetoothAdapter,
    val bluetoothDevice: BluetoothDevice,
    val jobRegistry: JobRegistry,
    val buffer: Buffer
) {

    val mutex = Mutex()

    var state = InsightState.DISCONNECTED
        private set

    var bluetoothSocket: BluetoothSocket? = null
    var writeChannel: SendChannel<ByteArray>? = null
    var isPaired = pairingData != null
    var pairingData = pairingData?.let {
        PairingData(
            lastSentNonce = it.lastSentNonce,
            lastReceivedNonce = it.lastReceivedNonce,
            commId = it.commId,
            incomingKey = it.incomingKey,
            outgoingKey = it.outgoingKey
        )
    } ?: PairingData()
    var keyPair: KeyPair? = null
    var randomData: ByteArray? = null
    var keyRequest: ByteArray? = null
    var verificationCode: String? = null
    var queueState = QueueState.QUEUE_EMPTY
    val queue = mutableListOf<CommandRequest<*>>()
    val activatedServices = mutableSetOf<Service>()
    val locks = mutableSetOf<Any>()
    val stateChannels = mutableSetOf<SendChannel<InsightState>>()

    /**
     * Changes the [state] property and notifies all [stateChannels].
     * @param state The new state
     */
    suspend fun setState(state: InsightState) {
        this.state = state
        logger.info(LTag.PUMPCOMM, "State: $state")
        stateChannels.forEach {
            try {
                it.send(state)
            } catch (e: ClosedSendChannelException) {
            }
        }
    }

    /**
     * Acquires a lock on [mutex] and executes [block] on [scope].
     */
    suspend fun <T> execute(block: suspend InsightHandles.() -> T) = mutex.withLock {
        withContext(scope.coroutineContext) {
            try {
                block()
            } catch (e: InsightException) {
                logger.error(LTag.PUMP, "Caught exception in state $state", e)
                reconnectIfRequested()
                throw e
            }
        }
    }
}