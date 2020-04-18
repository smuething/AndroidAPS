package info.nightscout.androidaps.networking.nightscout.data

import info.nightscout.androidaps.networking.nightscout.requests.EventType

enum class NightscoutCollection(val collection: String, val eventType: EventType? = null) {
    DEVICE_STATUS("devicestatus"),
    ENTRIES("entries"),
    FOOD("food"),
    PROFILE("profile"),
    SETTINGS("settings"),
    TEMPORARY_TARGET("treatments", EventType.TEMPORARY_TARGET),
    CAREPORTAL_EVENT("treatments"),
    INSULIN("treatments"),
    CARBS("treatments"),
    PROFILE_SWITCH("treatments"),
    TEMPORARY_BASAL("treatments"),
    EXTENDED_BOLUS("treatments")
}