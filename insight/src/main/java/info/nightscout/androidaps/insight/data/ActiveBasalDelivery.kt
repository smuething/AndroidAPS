package info.nightscout.androidaps.insight.data

import info.nightscout.androidaps.insight.data.enums.ActiveBasalProfile

data class ActiveBasalDelivery(
    val activeProfile: ActiveBasalProfile,
    val profileName: String,
    val basalAmount: Double
)