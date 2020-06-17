package info.nightscout.androidaps.insight.data.enums

enum class SatlError(
    val id: UByte
)  {
    UNDEFINED(0u),
    INCOMPATIBLE_VERSION(1u),
    INVALID_COMM_ID(2u),
    INVALID_MAC(3u),
    INVALID_CRC(4u),
    INVALID_PACKET(5u),
    DECRYPT_VERIFY_FAILED(7u),
    COMPATIBLE_STATE(8u),
    WRONG_STATE(16u),
    INVALID_MESSAGE_TYPE(51u),
    INVALID_PAYLOAD_LENGTH(60u),
    NONE(255u)
}