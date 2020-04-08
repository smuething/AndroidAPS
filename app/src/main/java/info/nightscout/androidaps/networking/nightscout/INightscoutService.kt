package info.nightscout.androidaps.networking.nightscout

import info.nightscout.androidaps.networking.nightscout.requests.EntryRequestBody
import info.nightscout.androidaps.networking.nightscout.responses.DummyResponse
import info.nightscout.androidaps.networking.nightscout.responses.LastModifiedResponse
import info.nightscout.androidaps.networking.nightscout.responses.StatusResponse
import io.reactivex.Single
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Created by adrian on 2019-12-23.
 */

interface INightscoutService {

    @GET("v3/status")
    // used to get the raw response for more error checking. E.g. to give the user better feedback after new settings.
    fun statusVerbose(): Single<Response<StatusResponse>>

    @GET("v3/status")
    fun statusSimple(): Single<StatusResponse>

    @GET("v3/lastModified")
    fun lastModified(): Single<LastModifiedResponse>

    @POST("v3/entries")
    fun postEntry(
        @Body entryRequestBody: EntryRequestBody
    ): Single<Response<DummyResponse>>

}