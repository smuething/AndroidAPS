package info.nightscout.androidaps.core.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import dagger.Module
import dagger.Provides
import javax.inject.Provider

@Module
abstract class ViewModelModule<O : ViewModelStoreOwner, V : ViewModel>(
    private val vmClass: Class<@JvmSuppressWildcards V>
) {

    @Provides
    fun providesViewModelFactory(provider: Provider<@JvmSuppressWildcards V>): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>) = provider.get() as T
    }

    @Provides
    @info.nightscout.androidaps.core.di.ViewModel
    fun providesViewModel(owner: @JvmSuppressWildcards O, factory: ViewModelProvider.Factory): @JvmSuppressWildcards V = ViewModelProvider(owner, factory).get(vmClass)
}