package info.nightscout.androidaps.utils.sharedPreferences

import androidx.annotation.StringRes
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.resources.ResourceHelper

@Suppress("UNCHECKED_CAST")
class PreferenceProvider<T : Any> constructor(val preference: String, val defaultValue : T, val sp: SP, val rxBus: RxBusWrapper) {

    constructor(@StringRes resId: Int, defaultValue: T, sp: SP, resourceHelper: ResourceHelper, rxBus: RxBusWrapper)
        : this(resourceHelper.gs(resId), defaultValue, sp, rxBus)

    fun get(): T {
        return when (defaultValue) {
            is Int     -> sp.getInt(preference, defaultValue) as T
            is Long    -> sp.getLong(preference, defaultValue) as T
            is Double  -> sp.getDouble(preference, defaultValue) as T
            is Boolean -> sp.getBoolean(preference, defaultValue) as T
            is String  -> sp.getString(preference, defaultValue) as T
            else       -> throw IllegalStateException("Not implemented")
        }
    }

    fun store(value: T) {
        when (value) {
            is Int     -> sp.putInt(preference, value as Int)
            is Long    -> sp.putLong(preference, value as Long)
            is Double  -> sp.putDouble(preference, value as Double)
            is Boolean -> sp.putBoolean(preference, value as Boolean)
            is String  -> sp.putString(preference, value as String)
            else       -> throw IllegalStateException("Not implemented")
        }
        rxBus.send(EventPreferenceChange(preference))
    }
}

@Suppress("unused")
typealias PreferenceInt = PreferenceProvider<Int>
@Suppress("unused")
typealias PreferenceLong = PreferenceProvider<Long>
@Suppress("unused")
typealias PreferenceDouble = PreferenceProvider<Double>
@Suppress("unused")
typealias PreferenceBoolean = PreferenceProvider<Boolean>
@Suppress("unused")
typealias PreferenceString = PreferenceProvider<String>