package info.nightscout.androidaps.insight

import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.Reusable
import info.nightscout.androidaps.insight.cryptography.Nonce
import info.nightscout.androidaps.insight.dagger.PairingDataPreferences
import info.nightscout.androidaps.insight.data.PairingData
import info.nightscout.androidaps.insight.data.PairingDataParameter
import info.nightscout.androidaps.insight.utils.decodeHex
import info.nightscout.androidaps.insight.utils.toHexString
import javax.inject.Inject

@Reusable
internal class PairingDataStorage @Inject constructor(
    @PairingDataPreferences
    private val sharedPreferences: SharedPreferences
) : PairingData.Callback {

    var isPaired: Boolean
        get() = sharedPreferences.getBoolean("isPaired", false)
        set(value) = sharedPreferences.edit() { putBoolean("isPaired", value) }

    var macAddress: String
        get() = sharedPreferences.getString("macAddress", null)!!
        set(value) = sharedPreferences.edit() { putString("macAddress", value) }

    val pairingDataParameter: PairingDataParameter
        get() = PairingDataParameter(
            lastSentNonce = Nonce(sharedPreferences.getString("lastSentNonce", null)!!.decodeHex().toUByteArray()),
            lastReceivedNonce = Nonce(sharedPreferences.getString("lastReceivedNonce", null)!!.decodeHex().toUByteArray()),
            commId = sharedPreferences.getInt("commId", 0).toUInt(),
            incomingKey = sharedPreferences.getString("incomingKey", null)!!.decodeHex(),
            outgoingKey = sharedPreferences.getString("outgoingKey", null)!!.decodeHex()
        )

    override fun lastSentNonceChanged(nonce: Nonce) = sharedPreferences.edit() { putString("lastSentNonce", nonce.value.toByteArray().toHexString()) }

    override fun lastReceivedNonceChanged(nonce: Nonce) = sharedPreferences.edit() { putString("lastReceivedNonce", nonce.value.toByteArray().toHexString()) }

    override fun commIdChanged(commId: UInt) = sharedPreferences.edit() { putInt("commId", commId.toInt()) }

    override fun incomingKeyChanged(byteArray: ByteArray) = sharedPreferences.edit() { putString("incomingKey", byteArray.toHexString()) }

    override fun outgoingKeyChanged(byteArray: ByteArray) = sharedPreferences.edit() { putString("outgoingKey", byteArray.toHexString()) }

    fun reset() {
        sharedPreferences.edit {
            remove("isPaired")
            remove("macAddress")
            remove("lastSentNonce")
            remove("lastReceivedNonce")
            remove("commId")
            remove("incomingKey")
            remove("outgoingKey")
        }
    }

}