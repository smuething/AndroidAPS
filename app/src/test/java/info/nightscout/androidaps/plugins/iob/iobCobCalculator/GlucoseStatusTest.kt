package info.nightscout.androidaps.plugins.iob.iobCobCalculator

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSgv
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.util.*

/**
 * Created by mike on 26.03.2018.
 */
@RunWith(PowerMockRunner::class)
@PrepareForTest(IobCobCalculatorPlugin::class, DateUtil::class)
class GlucoseStatusTest : TestBase() {

    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var iobCobCalculatorPlugin: IobCobCalculatorPlugin

    val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is GlucoseStatus) {
                it.aapsLogger = aapsLogger
                it.iobCobCalculatorPlugin = iobCobCalculatorPlugin
            }
            if (it is BgReading) {
            }
        }
    }

    @Test fun toStringShouldBeOverloaded() {
        val glucoseStatus = GlucoseStatus(injector)
        Assert.assertEquals(true, glucoseStatus.log().contains("Delta"))
    }

    @Test fun roundTest() {
        val glucoseStatus = GlucoseStatus(injector)
        glucoseStatus.glucose = 100.11111
        Assert.assertEquals(100.1, glucoseStatus.round().glucose, 0.0001)
    }

    @Test fun calculateValidGlucoseStatus() {
        PowerMockito.`when`(iobCobCalculatorPlugin.bgReadings).thenReturn(generateValidBgData())
        val glucoseStatus = GlucoseStatus(injector).glucoseStatusData!!
        Assert.assertEquals(214.0, glucoseStatus.glucose, 0.001)
        Assert.assertEquals(-2.0, glucoseStatus.delta, 0.001)
        Assert.assertEquals(-2.5, glucoseStatus.short_avgdelta, 0.001) // -2 -2.5 -3 deltas are relative to current value
        Assert.assertEquals(-2.5, glucoseStatus.avgdelta, 0.001) // the same as short_avgdelta
        Assert.assertEquals(-2.0, glucoseStatus.long_avgdelta, 0.001) // -2 -2 -2 -2
        Assert.assertEquals(1514766900000L, glucoseStatus.date) // latest date
    }

    @Test fun calculateMostRecentGlucoseStatus() {
        PowerMockito.`when`(iobCobCalculatorPlugin.bgReadings).thenReturn(generateMostRecentBgData())
        val glucoseStatus: GlucoseStatus = GlucoseStatus(injector).glucoseStatusData!!
        Assert.assertEquals(215.0, glucoseStatus.glucose, 0.001) // (214+216) / 2
        Assert.assertEquals(-1.0, glucoseStatus.delta, 0.001)
        Assert.assertEquals(-1.0, glucoseStatus.short_avgdelta, 0.001)
        Assert.assertEquals(-1.0, glucoseStatus.avgdelta, 0.001)
        Assert.assertEquals(0.0, glucoseStatus.long_avgdelta, 0.001)
        Assert.assertEquals(1514766900000L, glucoseStatus.date) // latest date, even when averaging
    }

    @Test fun oneRecordShouldProduceZeroDeltas() {
        PowerMockito.`when`(iobCobCalculatorPlugin.bgReadings).thenReturn(generateOneCurrentRecordBgData())
        val glucoseStatus: GlucoseStatus = GlucoseStatus(injector).glucoseStatusData!!
        Assert.assertEquals(214.0, glucoseStatus.glucose, 0.001)
        Assert.assertEquals(0.0, glucoseStatus.delta, 0.001)
        Assert.assertEquals(0.0, glucoseStatus.short_avgdelta, 0.001) // -2 -2.5 -3 deltas are relative to current value
        Assert.assertEquals(0.0, glucoseStatus.avgdelta, 0.001) // the same as short_avgdelta
        Assert.assertEquals(0.0, glucoseStatus.long_avgdelta, 0.001) // -2 -2 -2 -2
        Assert.assertEquals(1514766900000L, glucoseStatus.date) // latest date
    }

    @Test fun insuffientDataShouldReturnNull() {
        PowerMockito.`when`(iobCobCalculatorPlugin.bgReadings).thenReturn(generateInsufficientBgData())
        val glucoseStatus: GlucoseStatus? = GlucoseStatus(injector).glucoseStatusData
        Assert.assertEquals(null, glucoseStatus)
    }

    @Test fun oldDataShouldReturnNull() {
        PowerMockito.`when`(iobCobCalculatorPlugin.bgReadings).thenReturn(generateOldBgData())
        val glucoseStatus: GlucoseStatus? = GlucoseStatus(injector).glucoseStatusData
        Assert.assertEquals(null, glucoseStatus)
    }

    @Test fun returnOldDataIfAllowed() {
        PowerMockito.`when`(iobCobCalculatorPlugin.bgReadings).thenReturn(generateOldBgData())
        val glucoseStatus: GlucoseStatus? = GlucoseStatus(injector).getGlucoseStatusData(true)
        Assert.assertNotEquals(null, glucoseStatus)
    }

    @Test fun averageShouldNotFailOnEmptyArray() {
        Assert.assertEquals(0.0, GlucoseStatus.average(ArrayList()), 0.001)
    }

    @Test fun calculateGlucoseStatusForLibreTestBgData() {
        PowerMockito.`when`(iobCobCalculatorPlugin.bgReadings).thenReturn(generateLibreTestData())
        val glucoseStatus: GlucoseStatus = GlucoseStatus(injector).glucoseStatusData!!
        Assert.assertEquals(100.0, glucoseStatus.glucose, 0.001) //
        Assert.assertEquals(-10.0, glucoseStatus.delta, 0.001)
        Assert.assertEquals(-10.0, glucoseStatus.short_avgdelta, 0.001)
        Assert.assertEquals(-10.0, glucoseStatus.avgdelta, 0.001)
        Assert.assertEquals(-10.0, glucoseStatus.long_avgdelta, 0.001)
        Assert.assertEquals(1514766900000L, glucoseStatus.date) // latest date
    }

    @Before
    fun initMocking() {
        PowerMockito.mockStatic(DateUtil::class.java)
        PowerMockito.`when`(DateUtil.now()).thenReturn(1514766900000L + T.mins(1).msecs())
        `when`(iobCobCalculatorPlugin.dataLock).thenReturn(Unit)
    }

    private fun generateValidBgData(): List<GlucoseValue> {
        val list: MutableList<GlucoseValue> = ArrayList()
        list.add(GlucoseValue(value = 214.0, timestamp = 1514766900000, trendArrow = GlucoseValue.TrendArrow.FLAT, noise = 0.0, raw = 0.0, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        list.add(GlucoseValue(value = 216.0, timestamp = 1514766600000, trendArrow = GlucoseValue.TrendArrow.FLAT, noise = 0.0, raw = 0.0, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        list.add(GlucoseValue(value = 219.0, timestamp = 1514766300000, trendArrow = GlucoseValue.TrendArrow.FLAT, noise = 0.0, raw = 0.0, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        list.add(GlucoseValue(value = 223.0, timestamp = 1514766000000, trendArrow = GlucoseValue.TrendArrow.FLAT, noise = 0.0, raw = 0.0, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        list.add(GlucoseValue(value = 222.0, timestamp = 1514765700000, trendArrow = GlucoseValue.TrendArrow.FLAT, noise = 0.0, raw = 0.0, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        list.add(GlucoseValue(value = 224.0, timestamp = 1514765400000, trendArrow = GlucoseValue.TrendArrow.FLAT, noise = 0.0, raw = 0.0, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        list.add(GlucoseValue(value = 226.0, timestamp = 1514765100000, trendArrow = GlucoseValue.TrendArrow.FLAT, noise = 0.0, raw = 0.0, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        list.add(GlucoseValue(value = 228.0, timestamp = 1514764800000, trendArrow = GlucoseValue.TrendArrow.FLAT, noise = 0.0, raw = 0.0, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        return list
    }

    private fun generateMostRecentBgData(): List<GlucoseValue> {
        val list: MutableList<GlucoseValue> = ArrayList()
        list.add(GlucoseValue(value = 214.0, timestamp = 1514766900000, trendArrow = GlucoseValue.TrendArrow.FLAT, noise = 0.0, raw = 0.0, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        list.add(GlucoseValue(value = 216.0, timestamp = 1514766800000, trendArrow = GlucoseValue.TrendArrow.FLAT, noise = 0.0, raw = 0.0, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        list.add(GlucoseValue(value = 216.0, timestamp = 1514766600000, trendArrow = GlucoseValue.TrendArrow.FLAT, noise = 0.0, raw = 0.0, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        return list
    }

    private fun generateInsufficientBgData(): List<GlucoseValue> {
        return ArrayList()
    }

    private fun generateOldBgData(): List<GlucoseValue> {
        val list: MutableList<GlucoseValue> = ArrayList()
        list.add(GlucoseValue(value = 228.0, timestamp = 1514764800000, trendArrow = GlucoseValue.TrendArrow.FLAT, noise = 0.0, raw = 0.0, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        return list
    }

    private fun generateOneCurrentRecordBgData(): List<GlucoseValue> {
        val list: MutableList<GlucoseValue> = ArrayList()
        list.add(GlucoseValue(value = 214.0, timestamp = 1514766900000, trendArrow = GlucoseValue.TrendArrow.FLAT, noise = 0.0, raw = 0.0, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        return list
    }

    private fun generateLibreTestData(): List<GlucoseValue> {
        val list: MutableList<GlucoseValue> = ArrayList()
        val endTime = 1514766900000L
        val latestReading = 100.0
        // Now
        list.add(GlucoseValue(value = latestReading, timestamp = endTime, trendArrow = GlucoseValue.TrendArrow.FLAT, noise = 0.0, raw = 0.0, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        // One minute ago
        list.add(GlucoseValue(value = latestReading, timestamp = endTime - 1000 * 60 * 1, trendArrow = GlucoseValue.TrendArrow.FLAT, noise = 0.0, raw = 0.0, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        // Two minutes ago
        list.add(GlucoseValue(value = latestReading, timestamp = endTime - 1000 * 60 * 1, trendArrow = GlucoseValue.TrendArrow.FLAT, noise = 0.0, raw = 0.0, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))

        // Three minutes and beyond at constant rate
        for (i in 3..49) {
            list.add(GlucoseValue(value = latestReading + i * 2, timestamp = endTime - 1000 * 60 * i, trendArrow = GlucoseValue.TrendArrow.FLAT, noise = 0.0, raw = 0.0, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        }
        return list
    }
}