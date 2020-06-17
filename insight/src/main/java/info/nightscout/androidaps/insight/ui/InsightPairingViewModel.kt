package info.nightscout.androidaps.insight.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import info.nightscout.androidaps.insight.InsightPlugin
import info.nightscout.androidaps.insight.InsightPump
import info.nightscout.androidaps.insight.dagger.InsightPumpComponent
import info.nightscout.androidaps.insight.data.enums.InsightState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class InsightPairingViewModel @Inject constructor(
    private val context: Context,
    private val createInsightPumpComponent: InsightPumpComponent.Factory,
    private val bluetoothAdapter: BluetoothAdapter,
    private val insightPlugin: InsightPlugin
) : ViewModel(), CoroutineScope by MainScope() {

    val requestBluetoothEnable = MutableLiveData<Boolean>()
    val bluetoothDevices = MutableLiveData<Array<BluetoothDevice>>(emptyArray())
    val screen = MutableLiveData<Screen>()
    val code = MutableLiveData<String>()

    private var insightPump: InsightPump? = null
    private var stateJob: Job? = null

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED      -> {
                    if (intent.extras?.get(BluetoothAdapter.EXTRA_STATE) == BluetoothAdapter.STATE_ON && screen.value == Screen.DEVICE_SEARCH) {
                        bluetoothAdapter.startDiscovery()
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (bluetoothAdapter.isEnabled && screen.value == Screen.DEVICE_SEARCH) {
                        bluetoothAdapter.startDiscovery()
                    }
                }

                BluetoothDevice.ACTION_FOUND               -> {
                    val bluetoothDevice = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)!!
                    if (bluetoothDevice.name?.startsWith("PUMP") == true && !bluetoothDevices.value!!.contains(bluetoothDevice)) {
                        bluetoothDevices.value = arrayOf(*bluetoothDevices.value!!, bluetoothDevice)
                    }
                }
            }
        }
    }

    init {
        context.registerReceiver(bluetoothReceiver, IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_FOUND)
        })
        startDiscovery()
    }

    fun startDiscovery() {
        screen.value = Screen.DEVICE_SEARCH
        if (bluetoothAdapter.isEnabled) {
            bluetoothAdapter.startDiscovery()
        } else {
            requestBluetoothEnable.value = true
        }
    }

    fun pair(address: String) {
        screen.value = Screen.PLEASE_WAIT
        bluetoothAdapter.cancelDiscovery()
        insightPump = createInsightPumpComponent(insightPlugin.coroutineContext, address).insightPump().also { insightPump ->
            insightPump.readState()
            launch { insightPump.requestConnection(this@InsightPairingViewModel) }
        }
    }

    private fun InsightPump.readState() {
        stateJob = launch {
            registerStateChannel().consumeEach {
                Log.d("dadasd", it.toString())
                when (it) {
                    InsightState.WAITING_FOR_CODE_CONFIRMATION -> {
                        val code = getVerificationCode()
                        if (code != null) {
                            this@InsightPairingViewModel.code.value = code
                            screen.value = Screen.VERIFICATION_CODE
                        }
                    }

                    InsightState.CONNECTED                     -> {
                        insightPlugin.takeOverPump(this@readState)
                        withdrawConnectionRequest(this@InsightPairingViewModel)
                        screen.value = Screen.SUCCESS
                        stateJob = null
                        insightPump = null
                        return@launch
                    }

                    InsightState.DISCONNECTED                  -> {
                        screen.value = Screen.ERROR
                        insightPump = null
                        stateJob = null
                        close()
                        return@launch
                    }

                    else                                       -> {
                        screen.value = Screen.PLEASE_WAIT
                    }
                }
            }
        }
    }

    fun confirmCode() {
        screen.value = Screen.PLEASE_WAIT
        launch { insightPump?.confirmVerificationCode() }
    }

    fun rejectCode() {
        screen.value = Screen.PLEASE_WAIT
        launch { insightPump?.rejectVerificationCode() }
    }

    override fun onCleared() {
        insightPump?.close()
        cancel()
        context.unregisterReceiver(bluetoothReceiver)
    }

    enum class Screen {
        DEVICE_SEARCH,
        PLEASE_WAIT,
        VERIFICATION_CODE,
        ERROR,
        SUCCESS
    }
}