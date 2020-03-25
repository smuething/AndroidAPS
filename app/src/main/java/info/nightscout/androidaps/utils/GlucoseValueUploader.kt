package info.nightscout.androidaps.utils

import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.db.DbRequest
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.nsclient.UploadQueue
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject

class GlucoseValueUploader @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val sp: SP
) {

    operator fun invoke(glucoseValue: GlucoseValue, source: String) {
        if (sp.getBoolean(R.string.key_dexcomg5_nsupload, false)) {
            val data = JSONObject()
            try {
                data.put("device", source)
                data.put("date", glucoseValue.timestamp)
                data.put("dateString", DateUtil.toISOString(glucoseValue.timestamp))
                data.put("sgv", glucoseValue.value)
                data.put("direction", glucoseValue.trendArrow.toText())
                data.put("type", "sgv")
            } catch (e: JSONException) {
                aapsLogger.error(LTag.NSCLIENT, "Unhandled exception", e)
            }
            UploadQueue.add(DbRequest("dbAdd", "entries", data))
        }
    }
}