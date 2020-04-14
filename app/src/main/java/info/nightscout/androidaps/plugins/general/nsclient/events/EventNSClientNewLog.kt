package info.nightscout.androidaps.plugins.general.nsclient.events

import info.nightscout.androidaps.events.Event
import java.text.SimpleDateFormat
import java.util.*

class EventNSClientNewLog @JvmOverloads constructor(var action: String, var logText: String, val direction: Direction = Direction.NONE) : Event() {
    var date = Date()

    enum class Direction { IN, OUT, NONE }

    private var timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun toPreparedHtml(): StringBuilder {
        val stringBuilder = StringBuilder()
        stringBuilder.append(timeFormat.format(date))
        stringBuilder.append(" <b>")
        stringBuilder.append(when(direction) {
            Direction.IN   -> "<<< "
            Direction.OUT  -> ">>> "
            Direction.NONE -> ""
        })
        stringBuilder.append(action)
        stringBuilder.append("</b> ")
        stringBuilder.append(logText)
        stringBuilder.append("<br>")
        return stringBuilder
    }
}
