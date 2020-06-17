package info.nightscout.androidaps.insight

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.PumpDescription
import info.nightscout.androidaps.interfaces.PumpInterface
import info.nightscout.androidaps.plugins.common.ManufacturerType
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.utils.TimeChangeType
import org.json.JSONObject
import javax.inject.Inject

class DummyPumpInterface @Inject constructor(
    private val hasAndroidInjector: HasAndroidInjector
) : PumpInterface {

    override fun isConnecting(): Boolean = false

    override fun getJSONStatus(profile: Profile?, profileName: String?, version: String?): JSONObject = JSONObject()

    override fun shortStatus(veryShort: Boolean): String = ""

    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo?): PumpEnactResult = PumpEnactResult(hasAndroidInjector)

    override fun isThisProfileSet(profile: Profile?): Boolean = true

    override fun connect(reason: String?) {
    }

    override fun isConnected(): Boolean  = false

    override fun getPumpStatus() {
    }

    override fun lastDataTime(): Long = System.currentTimeMillis()

    override fun serialNumber(): String = ""

    override fun canHandleDST(): Boolean = true

    override fun isHandshakeInProgress(): Boolean = false

    override fun stopBolusDelivering() {

    }

    override fun getReservoirLevel(): Double = 100.0

    override fun setNewBasalProfile(profile: Profile?): PumpEnactResult = PumpEnactResult(hasAndroidInjector)

    override fun isBusy(): Boolean = false

    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult = PumpEnactResult(hasAndroidInjector)

    override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType?) {
    }

    override fun isFakingTempsByExtendedBoluses(): Boolean = false

    override fun getBaseBasalRate(): Double = 1.0

    override fun getCustomActions(): MutableList<CustomAction> = mutableListOf()

    override fun isSuspended(): Boolean = false

    override fun disconnect(reason: String?) {
    }

    override fun manufacturer(): ManufacturerType = ManufacturerType.Roche

    override fun stopConnecting() {
    }

    override fun setTempBasalPercent(percent: Int?, durationInMinutes: Int?, profile: Profile?, enforceNew: Boolean): PumpEnactResult = PumpEnactResult(hasAndroidInjector)

    override fun setExtendedBolus(insulin: Double?, durationInMinutes: Int?): PumpEnactResult = PumpEnactResult(hasAndroidInjector)

    override fun isInitialized(): Boolean = false

    override fun executeCustomAction(customActionType: CustomActionType?) {
    }

    override fun setTempBasalAbsolute(absoluteRate: Double?, durationInMinutes: Int?, profile: Profile?, enforceNew: Boolean): PumpEnactResult = PumpEnactResult(hasAndroidInjector)

    override fun finishHandshaking() {
    }

    override fun loadTDDs(): PumpEnactResult = PumpEnactResult(hasAndroidInjector)

    override fun cancelExtendedBolus(): PumpEnactResult = PumpEnactResult(hasAndroidInjector)

    override fun getPumpDescription(): PumpDescription = PumpDescription(PumpType.AccuChekInsightBluetooth)

    override fun model(): PumpType = PumpType.AccuChekInsightBluetooth

    override fun getBatteryLevel(): Int = 100

}