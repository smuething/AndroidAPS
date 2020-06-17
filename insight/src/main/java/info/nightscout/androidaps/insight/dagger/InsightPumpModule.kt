package info.nightscout.androidaps.insight.dagger

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext

@Module
internal object InsightPumpModule {

    @Provides
    @PerPump
    fun providesBluetoothDevice(@MacAddress macAddress: String, adapter: BluetoothAdapter): BluetoothDevice = adapter.getRemoteDevice(macAddress)

    @Provides
    @PerPump
    fun providesCoroutineScope(context: CoroutineContext): CoroutineScope = CoroutineScope(context + Job(context[Job]))

}