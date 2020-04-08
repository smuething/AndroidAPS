package info.nightscout.androidaps.networking.nightscout.data

/**
 * Created by adrian on 2019-12-26.
 */

sealed class SetupState {

    object Success : SetupState()
    //class Error(@StringRes message: Int) : SetupState()  // TODO: use string resources
    class Error(val message: String) : SetupState()
}