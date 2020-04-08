package info.nightscout.androidaps.services

import org.json.JSONArray

object DataExchangeStore {
    // There is huge amount of data coming from NS at once. In this case do not split them to intents
    // but exchange through this object
    // ACTION_NEW_SGV is still used to notify DataService to pick them up
    var nsclientSgvs: JSONArray? = null
}