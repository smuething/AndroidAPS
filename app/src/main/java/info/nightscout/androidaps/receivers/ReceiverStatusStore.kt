package info.nightscout.androidaps.receivers

import android.content.Context
import android.content.Intent
import info.nightscout.androidaps.events.EventChargingState
import info.nightscout.androidaps.events.EventNetworkChange
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiverStatusStore @Inject constructor(val context: Context, val rxBus: RxBusWrapper) {

    var lastNetworkEvent: EventNetworkChange? = null

    val isWifiConnected: Boolean
        get() = lastNetworkEvent?.wifiConnected ?: false

    val isConnected: Boolean
        get() = lastNetworkEvent?.wifiConnected ?: false || lastNetworkEvent?.mobileConnected ?: false

    fun updateNetworkStatus() {
        // TODO: don't send broadcast. Callers expect it to be blocking
        context.sendBroadcast(Intent(context, NetworkChangeReceiver::class.java))
    }

    var lastChargingEvent: EventChargingState? = null
        set(value) {
            field = value
            value?.let { rxBus.send(it) }
        }

    val isCharging: Boolean
        get() = lastChargingEvent?.isCharging ?: false

    fun broadcastChargingState() { // TODO make atomic: override setter? called when network state changes, not charging state?
        lastChargingEvent?.let { rxBus.send(it) }
    }
}