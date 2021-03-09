package app.tek4tv.digitalsignage.utils

import android.util.Log
import java.io.DataOutputStream
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

//duration: HH:mm:ss
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


fun setSystemTime(requireTime: String) {
    try {
        val process = Runtime.getRuntime().exec("su")
        val os = DataOutputStream(process.outputStream)

        val t = requireTime.split(" ")
        val date = t[0].split("/")
        val time = t[1].split(":")

        val command = "date ${date[1]}${date[0]}${time[0]}${time[1]}${date[2]}.${time[2]} \n"
        os.writeBytes(command)
        os.flush()
        os.writeBytes("exit\n")
        os.flush()
        process.waitFor()
    } catch (e: Exception) {
        Log.e("timesync", e.message ?: "")
        e.printStackTrace()
    }
}

