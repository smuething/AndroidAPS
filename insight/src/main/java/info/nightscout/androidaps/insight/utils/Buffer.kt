package info.nightscout.androidaps.insight.utils

import info.nightscout.androidaps.insight.dagger.PerPump
import javax.inject.Inject

@PerPump
class Buffer @Inject constructor() {

    var position = 0
    val content = ByteArray(BUFFER_SIZE)

    operator fun plusAssign(byteArray: ByteArray) {
        byteArray.copyInto(content, position)
        position += byteArray.size
    }

    fun getAndRemove(amount: Int): ByteArray {
        val bytes = content.copyOf(amount)
        content.copyInto(content, 0, amount)
        position -= amount
        return bytes
    }

    private companion object {
        const val BUFFER_SIZE = 1024
    }
}