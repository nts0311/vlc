package app.tek4tv.digitalsignage.media

import android.util.Log
import java.util.*




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

fun getDurationInSecond(duration: String): Long {
    return try {
        (duration.split(":").mapIndexed { index, s ->
            when (index) {
                0 -> s.toInt() * 3600
                1 -> s.toInt() * 60
                2 -> s.toInt()
                else -> 0
            }
        }.fold(0L) { acc, it -> acc + it })
    } catch (e: Exception) {
        0
    }
}