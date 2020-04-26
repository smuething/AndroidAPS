package info.nightscout.androidaps.dependencyInjection.networking

import com.google.gson.Gson
import info.nightscout.androidaps.R
import info.nightscout.androidaps.dependencyInjection.networking.NetModule.Companion.NAME_NIGHTSCOUT
import info.nightscout.androidaps.networking.nightscout.NightscoutService
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named

/**
 * Created by adrian on 2019-12-23.
 */

/*
* Usually we would like to provide the service directly.
* Unfortunately it seems that Retrofit has a static base url.
*
* Until there is a better solution, we won't inject the service directly but use a provider that dynamically generates the Retrofit instance.
* Improvement idea: keep an instance that only gets invalidated on preference change of the base URL.
*
* */

class NSRetrofitFactory(
    private val sp: SP,
    @Named(NAME_NIGHTSCOUT) private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {

    // TODO: handle empty string case
    private fun getBaseURL() = sp.getString(R.string.key_nsclient2_baseurl, "") // Test-Server: "nsapiv3.herokuapp.com"

    private fun getRetrofit(): Retrofit = Retrofit.Builder()
        .baseUrl("${getBaseURL()}")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .addCallAdapterFactory(RxJava2CallAdapterFactory.createWithScheduler(Schedulers.io()))
        .build()

    fun getNSService(): NightscoutService = getRetrofit().create(NightscoutService::class.java)
}