package info.nightscout.androidaps.data

import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.db.TempTarget
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner
import java.util.*

@RunWith(PowerMockRunner::class)
class OverlappingIntervalsTest {

    private val startDate = 0L
    var list = OverlappingIntervals<TempTarget>()


    @Test fun doTests() {
        // create one 10h interval and test value in and out
        list.add(TempTarget(TemporaryTarget(timestamp = startDate, duration = T.hours(10).msecs(), lowTarget = 100.0, highTarget = 100.0, reason = TemporaryTarget.Reason.CUSTOM)))
        Assert.assertEquals(null, list.getValueByInterval(startDate - T.secs(1).msecs()))
        Assert.assertEquals(100.0, list.getValueByInterval(startDate)!!.target(), 0.01)
        Assert.assertEquals(null, list.getValueByInterval(startDate + T.hours(10).msecs() + 1))

        // stop temp target after 5h
        list.add(TempTarget(TemporaryTarget(timestamp = startDate + T.hours(5).msecs(), duration = 0, lowTarget = 0.0, highTarget = 0.0, reason = TemporaryTarget.Reason.CUSTOM)))
        Assert.assertEquals(null, list.getValueByInterval(startDate - T.secs(1).msecs()))
        Assert.assertEquals(100.0, list.getValueByInterval(startDate)!!.target(), 0.01)
        Assert.assertEquals(100.0, list.getValueByInterval(startDate + T.hours(5).msecs() - 1)!!.target(), 0.01)
        Assert.assertEquals(null, list.getValueByInterval(startDate + T.hours(5).msecs() + 1))
        Assert.assertEquals(null, list.getValueByInterval(startDate + T.hours(10).msecs() + 1))

        // insert 1h interval inside
        list.add(TempTarget(TemporaryTarget(timestamp = startDate + T.hours(3).msecs(), duration = T.hours(1).msecs(), lowTarget = 200.0, highTarget = 200.0, reason = TemporaryTarget.Reason.CUSTOM)))
        Assert.assertEquals(null, list.getValueByInterval(startDate - T.secs(1).msecs()))
        Assert.assertEquals(100.0, list.getValueByInterval(startDate)!!.target(), 0.01)
        Assert.assertEquals(100.0, list.getValueByInterval(startDate + T.hours(5).msecs() - 1)!!.target(), 0.01)
        Assert.assertEquals(null, list.getValueByInterval(startDate + T.hours(5).msecs() + 1))
        Assert.assertEquals(200.0, list.getValueByInterval(startDate + T.hours(3).msecs())!!.target(), 0.01)
        Assert.assertEquals(100.0, list.getValueByInterval(startDate + T.hours(4).msecs() + 1)!!.target(), 0.01)
        Assert.assertEquals(null, list.getValueByInterval(startDate + T.hours(10).msecs() + 1))
    }

    @Test fun testCopyConstructor() {
        list.reset()
        list.add(TempTarget(TemporaryTarget(timestamp = startDate, duration = T.hours(10).msecs(), lowTarget = 100.0, highTarget = 100.0, reason = TemporaryTarget.Reason.CUSTOM)))
        val list2 = OverlappingIntervals(list)
        Assert.assertEquals(1, list2.list.size.toLong())
    }

    @Test fun testReversingArrays() {
        val someList: MutableList<TempTarget> = ArrayList()
        someList.add(TempTarget(TemporaryTarget(timestamp = startDate, duration = T.hours(3).msecs(), lowTarget = 200.0, highTarget = 200.0, reason = TemporaryTarget.Reason.CUSTOM)))
        someList.add(TempTarget(TemporaryTarget(timestamp = startDate + T.hours(1).msecs(), duration = T.hours(1).msecs(), lowTarget = 100.0, highTarget = 100.0, reason = TemporaryTarget.Reason.CUSTOM)))
        list.reset()
        list.add(someList)
        Assert.assertEquals(startDate, list[0].data.timestamp)
        Assert.assertEquals(startDate + T.hours(1).msecs(), list.getReversed(0).data.timestamp)
        Assert.assertEquals(startDate + T.hours(1).msecs(), list.reversedList[0].data.timestamp)
    }
}