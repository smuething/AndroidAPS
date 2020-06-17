package info.nightscout.androidaps.insight.data.enums

internal enum class Service(
    val id: UByte,
    val versionMajor: UByte,
    val versionMinor: UByte,
    val password: String?
) {
    CONNECTION(0u, 0u, 0u, null),
    STATUS(15u, 1u, 0u, null),
    PARAMETER(51u, 2u, 0u, null),
    HISTORY(60u, 2u, 0u, null),
    CONFIGURATION(85u, 2u, 0u, "u+5Fhz6Gw4j1Kkas"),
    REMOTE_CONTROL(102u, 1u, 0u, "MAbcV2X6PVjxuz+R")
}