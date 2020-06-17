package info.nightscout.androidaps.insight.utils

import android.bluetooth.BluetoothSocket
import java.io.IOException

fun BluetoothSocket.closeSilently() {
    try {
        close()
    } catch (e: IOException) {
    }
}