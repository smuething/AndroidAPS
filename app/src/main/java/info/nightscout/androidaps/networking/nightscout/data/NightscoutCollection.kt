package info.nightscout.androidaps.networking.nightscout.data

enum class NightscoutCollection (val collection: String) {
    DEVICESTATUS("devicestatus"),
    ENTRIES("entries"),
    FOOD("food"),
    PROFILE("profile"),
    SETTINGS("settings"),
    TREATMENTS("treatments")
}