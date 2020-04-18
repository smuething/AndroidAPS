package info.nightscout.androidaps.plugins.general.automation.actions

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.transactions.Transaction
import info.nightscout.androidaps.plugins.general.automation.elements.InputDuration
import info.nightscout.androidaps.plugins.general.automation.elements.InputTempTarget
import info.nightscout.androidaps.queue.Callback
import io.reactivex.Completable
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class ActionStartTempTargetTest : ActionsTestBase() {

    private lateinit var sut: ActionStartTempTarget

    @Before
    fun setup() {
        val tResult = Completable.fromAction({})
        `when`(repository.runTransaction(anyObject(Transaction::class.java))).thenReturn(tResult)

        `when`(resourceHelper.gs(R.string.starttemptarget)).thenReturn("Start temp target")

        sut = ActionStartTempTarget(injector)
    }

    @Test fun friendlyNameTest() {
        Assert.assertEquals(R.string.starttemptarget, sut.friendlyName())
    }

    @Test fun shortDescriptionTest() {
        sut.value = InputTempTarget(injector)
        sut.value.value = 100.0
        sut.duration = InputDuration(injector, 30, InputDuration.TimeUnit.MINUTES)
        Assert.assertEquals("Start temp target: 100mg/dl@null(Automation)", sut.shortDescription())
    }

    @Test fun iconTest() {
        Assert.assertEquals(R.drawable.icon_cp_cgm_target, sut.icon())
    }

    @Test fun doActionTest() {
        `when`(activePlugin.activeTreatments).thenReturn(treatmentsPlugin)
        sut.doAction(object : Callback() {
            override fun run() {
                Assert.assertTrue(result.success)
            }
        })
        Mockito.verify(repository, Mockito.times(1)).runTransaction(anyObject(Transaction::class.java))
    }

    @Test fun hasDialogTest() {
        Assert.assertTrue(sut.hasDialog())
    }

    @Test fun toJSONTest() {
        sut.value = InputTempTarget(injector)
        sut.value.value = 100.0
        sut.duration = InputDuration(injector, 30, InputDuration.TimeUnit.MINUTES)
        Assert.assertEquals("{\"data\":{\"durationInMinutes\":30,\"units\":\"mg/dl\",\"value\":100},\"type\":\"info.nightscout.androidaps.plugins.general.automation.actions.ActionStartTempTarget\"}", sut.toJSON())
    }

    @Test fun fromJSONTest() {
        sut.fromJSON("{\"value\":100,\"durationInMinutes\":30,\"units\":\"mg/dl\"}")
        Assert.assertEquals(Constants.MGDL, sut.value.units)
        Assert.assertEquals(100.0, sut.value.value, 0.001)
        Assert.assertEquals(30.0, sut.duration.getMinutes().toDouble(), 0.001)
    }
}