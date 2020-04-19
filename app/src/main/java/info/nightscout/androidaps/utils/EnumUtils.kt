package info.nightscout.androidaps.utils

/**
 * Created by adrian on 19.04.20.
 */

inline fun <reified T : Enum<T>> enumValueOfWitDefault(name: String, default: T): T = try {
    enumValueOf<T>(name)
} catch (e: IllegalArgumentException) {
    default
}

