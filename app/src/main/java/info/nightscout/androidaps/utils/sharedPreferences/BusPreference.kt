package info.nightscout.androidaps.utils.sharedPreferences

import androidx.annotation.StringRes
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Created by adrian on 03.05.20.
 */

@Suppress("UNCHECKED_CAST")
class BusPreference<T>(val preference: String, val defaultValue: T, val sp: SP, val rxBus: RxBusWrapper) : ReadWriteProperty<Any?, T> {

    constructor(@StringRes resId: Int, defaultValue: T, sp: SP, resourceHelper: ResourceHelper, rxBus: RxBusWrapper)
        : this(resourceHelper.gs(resId), defaultValue, sp, rxBus)

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return when (defaultValue) {
            is Int     -> sp.getInt(preference, defaultValue) as T
            is Long    -> sp.getLong(preference, defaultValue) as T
            is Double  -> sp.getDouble(preference, defaultValue) as T
            is Boolean -> sp.getBoolean(preference, defaultValue) as T
            is String  -> sp.getString(preference, defaultValue) as T
            else       -> throw IllegalStateException("Not implemented")
        }
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
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