package info.nightscout.androidaps.db

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.SP
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.Single
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.util.logging.Logger

@RunWith(PowerMockRunner::class)
@PrepareForTest(AppRepository::class, Logger::class, L::class, SP::class, GlucoseStatus::class)
class BgReadingTest : TestBase() {

    @Mock lateinit var defaultValueHelper: DefaultValueHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var repository: AppRepository

    var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is BgReading) {
                it.aapsLogger = aapsLogger
                it.resourceHelper = resourceHelper
                it.defaultValueHelper = defaultValueHelper
                it.profileFunction = profileFunction
                it.repository = repository
            }
        }
    }

    @Test
    fun valueToUnits() {
        val bgReading = BgReading(injector)
        bgReading.data.value = 18.0
        Assert.assertEquals(18.0, bgReading.valueToUnits(Constants.MGDL) * 1, 0.01)
        Assert.assertEquals(1.0, bgReading.valueToUnits(Constants.MMOL) * 1, 0.01)
    }

    @Test fun calculateDirection() {
        val bgReading = BgReading(injector)
        val bgReadingsList: MutableList<GlucoseValue> = mutableListOf()
        `when`(repository.compatGetBgReadingsDataFromTime(ArgumentMatchers.anyLong(), ArgumentMatchers.anyBoolean())).thenReturn(Single.just(bgReadingsList))
        Assert.assertEquals(GlucoseValue.TrendArrow.NONE, bgReading.calculateDirection())
        setReadings(72, 0)
        Assert.assertEquals(GlucoseValue.TrendArrow.DOUBLE_UP, bgReading.calculateDirection())
        setReadings(76, 60)
        Assert.assertEquals(GlucoseValue.TrendArrow.SINGLE_UP, bgReading.calculateDirection())
        setReadings(74, 65)
        Assert.assertEquals(GlucoseValue.TrendArrow.FORTY_FIVE_UP, bgReading.calculateDirection())
        setReadings(72, 72)
        Assert.assertEquals(GlucoseValue.TrendArrow.FLAT, bgReading.calculateDirection())
        setReadings(0, 72)
        Assert.assertEquals(GlucoseValue.TrendArrow.DOUBLE_DOWN, bgReading.calculateDirection())
        setReadings(60, 76)
        Assert.assertEquals(GlucoseValue.TrendArrow.SINGLE_DOWN, bgReading.calculateDirection())
        setReadings(65, 74)
        Assert.assertEquals(GlucoseValue.TrendArrow.FORTY_FIVE_DOWN, bgReading.calculateDirection())
    }

    @Before
    fun prepareMock() {
    }

    fun setReadings(current_value: Int, previous_value: Int) {

        val now = GlucoseValue(value = current_value.toDouble(), timestamp = System.currentTimeMillis(), noise = 0.0, raw = 0.0, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.NONE)
        val previous = GlucoseValue(value = previous_value.toDouble(), timestamp = System.currentTimeMillis() - 6 * 60 * 1000L, noise = 0.0, raw = 0.0, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.NONE)
        val bgReadings: MutableList<GlucoseValue> = mutableListOf()
        bgReadings.add(now)
        bgReadings.add(previous)
        `when`(repository.compatGetBgReadingsDataFromTime(ArgumentMatchers.anyLong(), ArgumentMatchers.anyBoolean())).thenReturn(Single.just(bgReadings))
    }
}