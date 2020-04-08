package info.nightscout.androidaps.networking.nightscout.responses

import com.google.gson.annotations.SerializedName

data class LastModifiedResponse(
    @SerializedName("srvDate") val srvDate: Long,
    @SerializedName("collections") val collections: Collections
)

data class Collections(
    @SerializedName("devicestatus") internal val deviceStatus: Long,
    @SerializedName("entries") val entries: Long,
    @SerializedName("food") val food: Long,
    @SerializedName("profile") val profile: Long,
    @SerializedName("settings") val settings: Long,
    @SerializedName("treatments") val treatments: Long
)
