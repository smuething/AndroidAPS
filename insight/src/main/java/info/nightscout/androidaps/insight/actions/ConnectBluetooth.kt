package info.nightscout.androidaps.insight.actions

import android.bluetooth.BluetoothSocket
import android.os.SystemClock
import info.nightscout.androidaps.insight.InsightHandles
import info.nightscout.androidaps.insight.data.enums.InsightState
import info.nightscout.androidaps.insight.utils.closeSilently
import info.nightscout.androidaps.logging.LTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.*

private val BLUETOOTH_PROFILE_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

private fun InsightHandles.createSocket(): BluetoothSocket {
    val bluetoothSocket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(BLUETOOTH_PROFILE_UUID)
    this.bluetoothSocket = bluetoothSocket
    return bluetoothSocket
}

internal suspend fun InsightHandles.connect() {
    logger.info(LTag.PUMPCOMM, "Connecting to pump")
    setState(InsightState.CONNECTING)
    val bluetoothSocket = this.bluetoothSocket ?: createSocket()
    jobRegistry.connectJob = scope.launch(Dispatchers.IO) {
        val start = SystemClock.uptimeMillis()
        try {
            bluetoothSocket.connect()
            val inputStream = bluetoothSocket.inputStream
            val outputStream = bluetoothSocket.outputStream
            execute {
                jobRegistry.connectJob = null
                readBluetooth(inputStream)
                writeBluetooth(outputStream)
                logger.info(LTag.PUMPCOMM, "Successfully connected")
                if (isPaired) {
                    sendSynRequest()
                } else {
                    sendConnectionRequest()
                }
            }
        } catch (e: IOException) {
            execute {
                logger.error(LTag.PUMPCOMM, "Failed to connect, e")
                jobRegistry.connectJob = null
                val duration = SystemClock.uptimeMillis() - start
                if (duration <= 1000L) {
                    bluetoothSocket.closeSilently()
                    this@execute.bluetoothSocket = null
                }
                if (isPaired) {
                    recover()
                } else {
                    setState(InsightState.DISCONNECTED)
                    cleanup()
                }
            }
        }
    }
}

internal suspend fun InsightHandles.reconnectIfRequested() {
    cleanup()
    if (isPaired && locks.isNotEmpty()) {
        if (bluetoothAdapter.isEnabled) {
            connect()
        } else {
            enableBluetooth()
        }
    } else {
        setState(InsightState.DISCONNECTED)
    }
}