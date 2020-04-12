package info.nightscout.androidaps.utils.sharedPreferences

import androidx.annotation.StringRes
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.resources.ResourceHelper

@Suppress("UNCHECKED_CAST")
class SPValue<T : Any> constructor(@StringRes val resId: Int, val sp: SP, val resourceHelper: ResourceHelper, val rxBus: RxBusWrapper) {

    var value: T = Any() as T
        get() =
            when (field) {
                is Int     -> sp.getInt(resId, 0) as T
                is Long    -> sp.getLong(resId, 0) as T
                is Double  -> sp.getDouble(resId, 0.0) as T
                is Boolean -> sp.getBoolean(resId, false) as T
                is String  -> sp.getString(resId, "") as T
                else       -> throw IllegalStateException("Not implemented")
            }
        set(value) {
            when (field) {
                is Int     -> sp.putInt(resId, value as Int)
                is Long    -> sp.putLong(resId, value as Long)
                is Double  -> sp.putDouble(resId, value as Double)
                is Boolean -> sp.putBoolean(resId, value as Boolean)
                is String  -> sp.putString(resId, value as String)
                else       -> throw IllegalStateException("Not implemented")
            }
            rxBus.send(EventPreferenceChange(resourceHelper, resId))
        }
}

@Suppress("unused")
typealias SPInt = SPValue<Int>
@Suppress("unused")
typealias SPLong = SPValue<Long>
@Suppress("unused")
typealias SPDouble = SPValue<Double>
@Suppress("unused")
typealias SPBoolean = SPValue<Boolean>
@Suppress("unused")
typealias SPString = SPValue<String>