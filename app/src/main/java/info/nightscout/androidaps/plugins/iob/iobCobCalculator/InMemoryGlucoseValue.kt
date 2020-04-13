package info.nightscout.androidaps.plugins.iob.iobCobCalculator

import info.nightscout.androidaps.database.entities.GlucoseValue

class InMemoryGlucoseValue constructor(var timestamp: Long = 0L, var value: Double = 0.0) {

    constructor(gv: GlucoseValue) : this(gv.timestamp, gv.value)
}