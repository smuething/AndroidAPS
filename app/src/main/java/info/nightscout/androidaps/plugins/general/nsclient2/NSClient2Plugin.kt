package info.nightscout.androidaps.plugins.general.nsclient2

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.networking.nightscout.NightscoutService
import info.nightscout.androidaps.networking.nightscout.data.SetupState
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import javax.inject.Inject

/**
 * Created by adrian on 2019-12-24.
 */

class NSClient2Plugin @Inject constructor(
    aapsLogger: AAPSLogger,
    resourceHelper: ResourceHelper,
    injector: HasAndroidInjector,
    private val nightscoutService: NightscoutService
) : PluginBase(PluginDescription()
    .mainType(PluginType.GENERAL)
    .fragmentClass(NSClient2Fragment::class.java.name)
    .pluginName(R.string.nsclientinternal2)
    .shortName(R.string.nsclientinternal2_shortname)
    .preferencesId(R.xml.pref_nsclient2)
    .description(R.string.description_ns_client)
    , aapsLogger, resourceHelper, injector
) {

    private val _testResultLiveData: MutableLiveData<String> = MutableLiveData("")
    val testResultLiveData: LiveData<String> = _testResultLiveData // Expose non-mutable form (avoid post from other classes)

    private val compositeDisposable = CompositeDisposable() //TODO: once transformed to VM, clear! (atm plugins live forever)

    fun testConnection() = compositeDisposable.add(
        nightscoutService.testSetup().subscribeBy(
            onSuccess = {
                _testResultLiveData.postValue(
                    when (it) {
                        SetupState.Success  -> "SUCCESS!"
                        is SetupState.Error -> it.message
                    }
                )
            },
            onError = { _testResultLiveData.postValue("failure: ${it.message}") })
    )

    fun exampleStatusCall() = compositeDisposable.add(
        nightscoutService.status().subscribeBy(
            onSuccess = { _testResultLiveData.postValue("success: $it") },
            onError = { _testResultLiveData.postValue("failure: ${it.message}") })
    )

    fun lastModifiedCall() = compositeDisposable.add(
        nightscoutService.lastModified().subscribeBy(
            onSuccess = { _testResultLiveData.postValue("success: $it") },
            onError = { _testResultLiveData.postValue("failure: ${it.message}") })
    )

}