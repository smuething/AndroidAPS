package info.nightscout.androidaps.plugins.pump.danaRS.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaRS.encryption.BleEncryption

class DanaRS_Packet_History_Prime @JvmOverloads constructor(
    injector: HasAndroidInjector,
    from: Long = 0
) : DanaRS_Packet_History_(injector, from) {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_REVIEW__PRIME
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun getFriendlyName(): String {
        return "REVIEW__PRIME"
    }
}