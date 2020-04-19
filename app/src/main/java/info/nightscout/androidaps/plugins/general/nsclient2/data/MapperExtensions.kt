package info.nightscout.androidaps.plugins.general.nsclient2.data

import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.utils.enumValueOfWitDefault

/**
 * Created by adrian on 19.04.20.
 */

fun GlucoseValue.TrendArrow.toNs(): TrendArrowNightScout = enumValueOfWitDefault(this.name, TrendArrowNightScout.NONE)
fun TrendArrowNightScout.toEntity(): GlucoseValue.TrendArrow = enumValueOfWitDefault(this.name, GlucoseValue.TrendArrow.NONE)

fun GlucoseValue.SourceSensor.toNs(): SourceSensorNightScout = enumValueOfWitDefault(this.name, SourceSensorNightScout.UNKNOWN)
fun SourceSensorNightScout.toEntity(): GlucoseValue.SourceSensor = enumValueOfWitDefault(this.name, GlucoseValue.SourceSensor.UNKNOWN)
