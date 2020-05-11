package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgHistoryBasalHour
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgHistoryBasalHourTest : DanaRTestBase() {

    @Test fun runTest() {
<<<<<<< HEAD
        val packet = MsgHistoryBasalHour(aapsLogger, RxBusWrapper(aapsSchedulers), dateUtil)
=======
        val packet = MsgHistoryBasalHour(aapsLogger, RxBusWrapper(), dateUtil, databaseHelper)
>>>>>>> origin/dev
        // nothing left to test
    }
}