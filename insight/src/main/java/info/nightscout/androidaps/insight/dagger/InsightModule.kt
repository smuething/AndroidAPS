package info.nightscout.androidaps.insight.dagger

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.insight.ui.InsightPairingActivity

@Module(subcomponents = [InsightPumpComponent::class])
abstract class InsightModule {

    @ContributesAndroidInjector(modules = [InsightPairingActivityModule::class])
    abstract fun contributesInsightPairingActivity(): InsightPairingActivity

    companion object {
        @Provides
        fun providesBluetoothAdapter(): BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        @Provides
        @PairingDataPreferences
        @Reusable
        fun providesPairingDataPreferences(context: Context): SharedPreferences = EncryptedSharedPreferences.create("insight_pairing_data", MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC), context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
}