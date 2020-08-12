package info.nightscout.androidaps.plugins.pump.omnipod.driver;

import java.util.Arrays;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.common.data.PumpStatus;
import info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data.RLHistoryItem;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodDeviceState;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodDeviceStatusChange;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by andy on 4.8.2019
 */
@Singleton
public class OmnipodPumpStatus extends PumpStatus {
    // TODO remove all fields that can also be obtained via PodStateManager
    //  We can probably get rid of this class altogether

    private final ResourceHelper resourceHelper;
    private final SP sp;
    private final RileyLinkUtil rileyLinkUtil;
    private final RxBusWrapper rxBus;
    private final PodStateManager podStateManager;

    public String rileyLinkErrorDescription = null;
    public String rileyLinkAddress = null;
    public boolean inPreInit = true;

    // statuses
    public double currentBasal = 0;
    public long tempBasalStart;
    public long tempBasalEnd;
    public Double tempBasalAmount = 0.0d;
    public Integer tempBasalLength;
    public long tempBasalPumpId;
    public PumpType pumpType;

    public String regexMac = "([\\da-fA-F]{1,2}(?:\\:|$)){6}";

    public PodDeviceState podDeviceState = PodDeviceState.NeverContacted;
    // TODO: needed?
    //public Boolean podAvailable = false;
    //public boolean podAvailibityChecked = false;
    public boolean ackAlertsAvailable = false;
    public String ackAlertsText = null;

    public boolean beepBolusEnabled = true;
    public boolean beepBasalEnabled = true;
    public boolean beepSMBEnabled = true;
    public boolean beepTBREnabled = true;
    public boolean podDebuggingOptionsEnabled = false;
    public boolean timeChangeEventEnabled = true;

    public OmnipodDriverState driverState = OmnipodDriverState.NotInitalized;
    private PumpDeviceState pumpDeviceState;

    @Inject
    public OmnipodPumpStatus(ResourceHelper resourceHelper,
                             info.nightscout.androidaps.utils.sharedPreferences.SP sp,
                             RxBusWrapper rxBus,
                             RileyLinkUtil rileyLinkUtil,
                             PodStateManager podStateManager) {
        super(PumpType.Insulet_Omnipod);
        this.resourceHelper = resourceHelper;
        this.sp = sp;
        this.rxBus = rxBus;
        this.rileyLinkUtil = rileyLinkUtil;
        this.podStateManager = podStateManager;
        initSettings();
    }

    @Override
    public void initSettings() {
        this.activeProfileName = "";
        this.reservoirRemainingUnits = 75d;
        this.batteryRemaining = 75;
        this.lastConnection = sp.getLong(OmnipodConst.Statistics.LastGoodPumpCommunicationTime, 0L);
        this.pumpType = PumpType.Insulet_Omnipod;
    }

    // For Omnipod, this method only returns a RileyLink error description
    @Override
    public String getErrorInfo() {
        return this.rileyLinkErrorDescription;
    }

    @Override
    public <E> E getCustomData(String key, Class<E> clazz) {
        switch(key) {
            case "POD_LOT_NUMBER":
                return (E) ("" + podStateManager.getLot());

            case "POD_AVAILABLE":
                return (E) Boolean.valueOf(podStateManager.hasState());

            default:
                return null;
        }

    }


//    public boolean setNotInPreInit() {
//        this.inPreInit = false;
//
//        return reconfigureService();
//    }


    public void clearTemporaryBasal() {
        this.tempBasalStart = 0L;
        this.tempBasalEnd = 0L;
        this.tempBasalAmount = 0.0d;
        this.tempBasalLength = 0;
    }


    public TempBasalPair getTemporaryBasal() {

        TempBasalPair tbr = new TempBasalPair();
        tbr.setDurationMinutes(tempBasalLength);
        tbr.setInsulinRate(tempBasalAmount);
        tbr.setStartTime(tempBasalStart);
        tbr.setEndTime(tempBasalEnd);

        return tbr;
    }

    @Override
    public String toString() {
        return "OmnipodPumpStatus{" +
                "rileyLinkErrorDescription='" + rileyLinkErrorDescription + '\'' +
                ", rileyLinkAddress='" + rileyLinkAddress + '\'' +
                ", inPreInit=" + inPreInit +
                ", currentBasal=" + currentBasal +
                ", tempBasalStart=" + tempBasalStart +
                ", tempBasalEnd=" + tempBasalEnd +
                ", tempBasalAmount=" + tempBasalAmount +
                ", tempBasalLength=" + tempBasalLength +
                ", regexMac='" + regexMac + '\'' +
                ", podDeviceState=" + podDeviceState +
                ", lastDataTime=" + lastDataTime +
                ", lastConnection=" + lastConnection +
                ", previousConnection=" + previousConnection +
                ", lastBolusTime=" + lastBolusTime +
                ", lastBolusAmount=" + lastBolusAmount +
                ", activeProfileName='" + activeProfileName + '\'' +
                ", reservoirRemainingUnits=" + reservoirRemainingUnits +
                ", reservoirFullUnits=" + reservoirFullUnits +
                ", batteryRemaining=" + batteryRemaining +
                ", batteryVoltage=" + batteryVoltage +
                ", iob='" + iob + '\'' +
                ", dailyTotalUnits=" + dailyTotalUnits +
                ", maxDailyTotalUnits='" + maxDailyTotalUnits + '\'' +
                ", validBasalRateProfileSelectedOnPump=" + validBasalRateProfileSelectedOnPump +
                ", pumpType=" + pumpType +
                ", profileStore=" + profileStore +
                ", units='" + units + '\'' +
                ", pumpStatusType=" + pumpStatusType +
                ", basalsByHour=" + Arrays.toString(basalsByHour) +
                ", currentBasal=" + currentBasal +
                ", tempBasalInProgress=" + tempBasalInProgress +
                ", tempBasalRatio=" + tempBasalRatio +
                ", tempBasalRemainMin=" + tempBasalRemainMin +
                ", tempBasalStart=" + tempBasalStart +
                ", pumpType=" + pumpType +
                "} ";
    }


    public PumpDeviceState getPumpDeviceState() {
        return pumpDeviceState;
    }


    public void setPumpDeviceState(PumpDeviceState pumpDeviceState) {
        this.pumpDeviceState = pumpDeviceState;

        rileyLinkUtil.getRileyLinkHistory().add(new RLHistoryItem(pumpDeviceState, RileyLinkTargetDevice.Omnipod));

        rxBus.send(new EventRileyLinkDeviceStatusChange(pumpDeviceState));
    }

}
