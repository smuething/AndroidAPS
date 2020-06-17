package info.nightscout.androidaps.insight.ui

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import info.nightscout.androidaps.insight.databinding.AdapterBluetoothDeviceBinding
import kotlin.properties.Delegates

class DeviceAdapter(
    private val callback: (String) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

    var bluetoothDevices: Array<BluetoothDevice> by Delegates.observable(emptyArray()) { _, oldValue, newValue ->
        notifyItemRangeInserted(oldValue.size, newValue.size - oldValue.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(AdapterBluetoothDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = bluetoothDevices.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val bluetoothDevice = bluetoothDevices[position]
        holder.binding.deviceName.text = bluetoothDevice.name ?: bluetoothDevice.address
        holder.binding.root.setOnClickListener { callback(bluetoothDevice.address) }
    }

    class ViewHolder(val binding: AdapterBluetoothDeviceBinding) : RecyclerView.ViewHolder(binding.root) {}
}