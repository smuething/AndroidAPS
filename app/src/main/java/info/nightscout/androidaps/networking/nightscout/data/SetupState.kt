package info.nightscout.androidaps.networking.nightscout.data

import info.nightscout.androidaps.networking.nightscout.responses.ApiPermissions

/**
 * Created by adrian on 2019-12-26.
 */

sealed class SetupState {
    class Success(val permissions: ApiPermissions) : SetupState()
    class Error(val message: String) : SetupState()
}