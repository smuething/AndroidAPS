package info.nightscout.androidaps.insight.actions

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import info.nightscout.androidaps.insight.InsightHandles
import info.nightscout.androidaps.insight.data.enums.InsightState
import info.nightscout.androidaps.insight.utils.InsightException
import info.nightscout.androidaps.logging.LTag
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal suspend fun InsightHandles.enableBluetooth() {
    logger.info(LTag.PUMPCOMM, "Enabling Bluetooth")
    setState(InsightState.ENABLING_BLUETOOTH)
    jobRegistry.enableBluetoothJob = scope.launch {
        val result = suspendCancellableCoroutine<Boolean> { continuation ->

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val state = intent.extras?.getInt(BluetoothAdapter.EXTRA_STATE)
                    when (state) {
                        BluetoothAdapter.STATE_ON         -> {
                            try {
                                context.unregisterReceiver(this)
                                continuation.resume(true)
                            } catch (ignored: IllegalArgumentException) {
                            }
                        }

                        BluetoothAdapter.STATE_TURNING_ON -> Unit

                        else                              -> {
                            try {
                                context.unregisterReceiver(this)
                                continuation.resume(false)
                            } catch (ignored: IllegalArgumentException) {
                            }
                        }
                    }
                }
            }
            context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
            continuation.invokeOnCancellation {
                try {
                    context.unregisterReceiver(receiver)
                } catch (ignored: IllegalArgumentException) {
                }
            }
            if (!bluetoothAdapter.enable()) {
                try {
                    context.unregisterReceiver(receiver)
                    continuation.resume(false)
                } catch (ignored: IllegalArgumentException) {
                }
            }
        }
        execute {
            jobRegistry.enableBluetoothJob = null
            if (result) {
                logger.info(LTag.PUMPCOMM, "Bluetooth successfully enabled")
                connect()
            } else {
                throw InsightException("Failed to enable bluetooth")
            }
        }
    }
}