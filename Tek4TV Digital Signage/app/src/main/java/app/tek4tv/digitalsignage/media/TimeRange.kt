package app.tek4tv.digitalsignage.media

import android.util.Log
import java.util.*

data class TimeRange(private val start: String, private val end: String) {

    val startTime: Calendar = toCalendar(start)
    val endTime: Calendar = toCalendar(end)


    operator fun contains(c: Calendar): Boolean {
        return c.timeInMillis >= startTime.timeInMillis
                && c.timeInMillis <= endTime.timeInMillis
    }


}


fun toCalendar(time: String): Calendar {
    return try {
        Calendar.getInstance().apply {
            val t = time.split(":").map { it.toInt() }
            set(Calendar.HOUR_OF_DAY, t[0])
            set(Calendar.MINUTE, t[1])
            set(Calendar.SECOND, t[2])
        }
    } catch (e: Exception) {
        Log.d("toCalendar()", e.message)
        Calendar.getInstance()
    }
}

