package info.nightscout.androidaps.plugins.general.nsclient2

import android.text.Spanned

sealed class NSClient2LiveData {
    data class Log(val spanned: Spanned) : NSClient2LiveData()
    data class State(val state : String) : NSClient2LiveData()
}