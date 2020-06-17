package info.nightscout.androidaps.insight.data.enums

import kotlin.reflect.KClass

internal interface EnumWithId {
    val id: UShort
}

internal inline fun <reified T : Enum<T>> resolveOrNull(id: UShort) = enumValues<T>().firstOrNull() { (it as EnumWithId).id == id }

internal inline fun <reified T : Enum<T>> resolve(id: UShort) = resolveOrNull<T>(id) ?: throw InvalidEnumException(id, T::class)

internal data class InvalidEnumException(
    val id : UShort,
    val clazz: KClass<*>
) : Exception()