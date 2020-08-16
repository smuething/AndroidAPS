package info.nightscout.androidaps.plugins.pump.omnipod.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data.RLHistoryItem;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertSet;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertSlot;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertType;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodManager;
import info.nightscout.androidaps.plugins.pump.omnipod.data.RLHistoryItemOmnipod;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodPodType;

import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodDeviceState;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodDriverState;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodDeviceStatusChange;
import info.nightscout.androidaps.utils.alertDialogs.OKDialog;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by andy on 4/8/19.
 */
@Singleton
public class OmnipodUtil {

    private final AAPSLogger aapsLogger;
    private final RxBusWrapper rxBus;
    private final RileyLinkUtil rileyLinkUtil;
    private final OmnipodPumpStatus omnipodPumpStatus;
    private final ResourceHelper resourceHelper;
    private final ActivePluginProvider activePlugins;
    private final SP sp;
    private final ResourceHelper resourceHelper;
    private final HasAndroidInjector injector;

    private boolean lowLevelDebug = true;
    private OmnipodCommandType currentCommand;
    private Gson gsonInstance = createGson();
    private OmnipodPodType omnipodPodType;
    private OmnipodDriverState driverState = OmnipodDriverState.NotInitalized;

    @Inject
    public OmnipodUtil(
            AAPSLogger aapsLogger,
            RxBusWrapper rxBus,
            RileyLinkUtil rileyLinkUtil,
            OmnipodPumpStatus omnipodPumpStatus,
            SP sp,
            ResourceHelper resourceHelper,
            ActivePluginProvider activePlugins,
            HasAndroidInjector injector
    ) {
        this.aapsLogger = aapsLogger;
        this.rxBus = rxBus;
        this.rileyLinkUtil = rileyLinkUtil;
        this.omnipodPumpStatus = omnipodPumpStatus;
        this.sp = sp;
        this.resourceHelper = resourceHelper;
        this.activePlugins = activePlugins;
    }

    public boolean isLowLevelDebug() {
        return lowLevelDebug;
    }

    public void setLowLevelDebug(boolean lowLevelDebug) {
        this.lowLevelDebug = lowLevelDebug;
    }

    public OmnipodCommandType getCurrentCommand() {
        return currentCommand;
    }

    public void setCurrentCommand(OmnipodCommandType currentCommand) {
        this.currentCommand = currentCommand;

        if (currentCommand != null)
            rileyLinkUtil.getRileyLinkHistory().add(new RLHistoryItem(currentCommand));

        rxBus.send(new EventOmnipodDeviceStatusChange((OmnipodCommandType) null));
    }

    public static void displayNotConfiguredDialog(Context context) {
        OKDialog.showConfirmation(context, MainApp.gs(R.string.combo_warning),
                MainApp.gs(R.string.omnipod_error_operation_not_possible_no_configuration), (Runnable) null);
    }

    public OmnipodDriverState getDriverState() {
        return driverState;
    }

    public void setDriverState(OmnipodDriverState state) {
        if (driverState == state)
            return;

        driverState = state;
        omnipodPumpStatus.driverState = state;

        // TODO maybe remove
//        if (OmnipodUtil.omnipodPumpStatus != null) {
//            OmnipodUtil.omnipodPumpStatus.driverState = state;
//        }
//
//        if (OmnipodUtil.omnipodPumpPlugin != null) {
//            OmnipodUtil.omnipodPumpPlugin.setDriverState(state);
//        }
    }

    private Gson createGson() {
        GsonBuilder gsonBuilder = new GsonBuilder()
                .registerTypeAdapter(DateTime.class, (JsonSerializer<DateTime>) (dateTime, typeOfSrc, context) ->
                        new JsonPrimitive(ISODateTimeFormat.dateTime().print(dateTime)))
                .registerTypeAdapter(DateTime.class, (JsonDeserializer<DateTime>) (json, typeOfT, context) ->
                        ISODateTimeFormat.dateTime().parseDateTime(json.getAsString()))
                .registerTypeAdapter(DateTimeZone.class, (JsonSerializer<DateTimeZone>) (timeZone, typeOfSrc, context) ->
                        new JsonPrimitive(timeZone.getID()))
                .registerTypeAdapter(DateTimeZone.class, (JsonDeserializer<DateTimeZone>) (json, typeOfT, context) ->
                        DateTimeZone.forID(json.getAsString()));

        return gsonBuilder.create();
    }

    public void setPodDeviceState(PodDeviceState podDeviceState) {
        omnipodPumpStatus.podDeviceState = podDeviceState;
    }

    public void setOmnipodPodType(OmnipodPodType omnipodPodType) {
        this.omnipodPodType = omnipodPodType;
    }

    public OmnipodPodType getOmnipodPodType() {
        return this.omnipodPodType;
    }

    public PodDeviceState getPodDeviceState() {
        return omnipodPumpStatus.podDeviceState;
    }

    public boolean isOmnipodEros() {
        return this.activePlugins.getActivePump().model() == PumpType.Insulet_Omnipod;
    }

    public boolean isOmnipodDash() {
        return this.activePlugins.getActivePump().model() == PumpType.Insulet_Omnipod_Dash;
    }

    public void setPumpType(PumpType pumpType_) {
        omnipodPumpStatus.pumpType = pumpType_;
    }

    public PumpType getPumpType() {
        return omnipodPumpStatus.pumpType;
    }

    public Gson getGsonInstance() {
        return this.gsonInstance;
    }

    public AAPSLogger getAapsLogger() {
        return this.aapsLogger;
    }

    public SP getSp() {
        return this.sp;
    }

    public List<String> getTranslatedActiveAlerts(PodStateManager podStateManager) {
        List<String> translatedAlerts = new ArrayList<>();
        AlertSet activeAlerts = podStateManager.getActiveAlerts();

        for (AlertSlot alertSlot : activeAlerts.getAlertSlots()) {
            translatedAlerts.add(translateAlertType(podStateManager.getConfiguredAlertType(alertSlot)));
        }
        return translatedAlerts;
    }

    private String translateAlertType(AlertType alertType) {
        if (alertType == null) {
            return resourceHelper.gs(R.string.omnipod_alert_unknown_alert);
        }
        switch (alertType) {
            case FINISH_PAIRING_REMINDER:
                return resourceHelper.gs(R.string.omnipod_alert_finish_pairing_reminder);
            case FINISH_SETUP_REMINDER:
                return resourceHelper.gs(R.string.omnipod_alert_finish_setup_reminder_reminder);
            case EXPIRATION_ALERT:
                return resourceHelper.gs(R.string.omnipod_alert_expiration);
            case EXPIRATION_ADVISORY_ALERT:
                return resourceHelper.gs(R.string.omnipod_alert_expiration_advisory);
            case SHUTDOWN_IMMINENT_ALARM:
                return resourceHelper.gs(R.string.omnipod_alert_shutdown_imminent);
            case LOW_RESERVOIR_ALERT:
                return resourceHelper.gs(R.string.omnipod_alert_low_reservoir);
            default:
                return alertType.name();
        }
    }
}
