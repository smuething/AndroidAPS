package info.nightscout.androidaps.networking.nightscout.responses

import androidx.appcompat.app.AppCompatDelegate
import com.google.gson.annotations.SerializedName
import info.nightscout.androidaps.networking.nightscout.data.NightscoutCollection

/**
 * Created by adrian on 2019-12-23.
 */

data class StatusResponse(
    @SerializedName("version") val version: String,
    @SerializedName("apiVersion") val apiVersion: String,
    @SerializedName("srvDate") val srvDate: Long,
    @SerializedName("storage") val storage: Storage,
    @SerializedName("apiPermissions") val apiPermissions: ApiPermissions
)

data class Storage(
    @SerializedName("storage") val storage: String,
    @SerializedName("version") val version: String
)

data class ApiPermissions(
    @SerializedName("devicestatus") val deviceStatus: ApiPermission,
    @SerializedName("entries") val entries: ApiPermission,
    @SerializedName("food") val food: ApiPermission,
    @SerializedName("profile") val profile: ApiPermission,
    @SerializedName("settings") val settings: ApiPermission,
    @SerializedName("treatments") val treatments: ApiPermission
)

typealias ApiPermission = String

val ApiPermission.create: Boolean
    get() = this.contains('c')

val ApiPermission.read: Boolean
    get() = this.contains('r')

val ApiPermission.update: Boolean
    get() = this.contains('u')

val ApiPermission.delete: Boolean
    get() = this.contains('d')

val ApiPermission.readCreate: Boolean
    get() = this.read && this.create

val ApiPermission.createUpdate: Boolean
    get() = this.update && this.create

val ApiPermission.full: Boolean
    get() = this.create && this.read && this.update && this.delete

fun ApiPermissions.of(collection: NightscoutCollection) : ApiPermission =
    when(collection) {
        NightscoutCollection.DEVICESTATUS -> deviceStatus
        NightscoutCollection.ENTRIES      -> entries
        NightscoutCollection.FOOD         -> food
        NightscoutCollection.PROFILE      -> profile
        NightscoutCollection.SETTINGS     -> settings
        NightscoutCollection.TREATMENTS   -> treatments
    }
