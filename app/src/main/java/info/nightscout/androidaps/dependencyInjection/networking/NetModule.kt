package info.nightscout.androidaps.dependencyInjection.networking

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.Reusable
import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.R
import info.nightscout.androidaps.networking.nightscout.NightscoutServiceWrapper
import info.nightscout.androidaps.plugins.source.NSClientSourcePlugin
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
class NetModule {

    @Provides
    @Singleton
    internal fun providesNightscoutService(nsRetrofitFactory: NSRetrofitFactory, resourceHelper: ResourceHelper, sp: SP, nsClientSourcePlugin: NSClientSourcePlugin): NightscoutServiceWrapper {
        return NightscoutServiceWrapper(nsRetrofitFactory, resourceHelper, sp, nsClientSourcePlugin)
    }

    @Provides
    @Named(NAME_NIGHTSCOUT)
    internal fun providesNSOkHttpClient(
        context: Context,
        sp: SP
    ): OkHttpClient {
        return setupOkHttpClient(
            context,
            NSAuthInterceptor(sp)
        )
    }

    @Provides
    @Reusable
    internal fun nsRetrofitProvider(sp: SP, @Named(NAME_NIGHTSCOUT) okHttpClient: OkHttpClient, gson: Gson): NSRetrofitFactory {
        return NSRetrofitFactory(sp, okHttpClient, gson)
    }

    @Provides
    @Singleton
    internal fun provideGson(): Gson = GsonBuilder().create()

    class NSAuthInterceptor(private val sp: SP) : Interceptor {

        private fun getAuthToken() = sp.getString(R.string.key_nsclient2_token, "") // Test server: "testreadab-76eaff2418bfb7e0"

        override fun intercept(chain: Interceptor.Chain): Response {
            var request = chain.request()
            getAuthToken().takeIf { !it.isBlank() }?.let { token ->
                val url = request.url.newBuilder()
                    .addQueryParameter("token", token)
                    .addQueryParameter("now", System.currentTimeMillis().toString())
                    .build()
                request = request.newBuilder().url(url).build()
            }
            return chain.proceed(request)
        }
    }

    private fun setupOkHttpClient(
        context: Context,
        vararg networkInterceptors: Interceptor
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()

        for (interceptor in networkInterceptors) {
            builder.addInterceptor(interceptor)
        }

        if (BuildConfig.DEBUG) {
            builder.addNetworkInterceptor(HttpLoggingInterceptor().also { it.level = HttpLoggingInterceptor.Level.BODY })
        }

        builder
            .cache(Cache(context.cacheDir, OK_HTTP_CACHE_SIZE))
            .readTimeout(OK_HTTP_READ_TIMEOUT, TimeUnit.MILLISECONDS)
            .writeTimeout(OK_HTTP_WRITE_TIMEOUT, TimeUnit.MILLISECONDS)

        return builder.build()
    }

    companion object {
        private const val OK_HTTP_CACHE_SIZE = 10L * 1024 * 1024
        private const val OK_HTTP_READ_TIMEOUT = 60L * 1000
        private const val OK_HTTP_WRITE_TIMEOUT = 60L * 1000
        const val NAME_NIGHTSCOUT = "network_nightscout"
    }
}