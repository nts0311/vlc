package app.tek4tv.digitalsignage

import kotlinx.coroutines.*
import java.util.*

class Timer(private val lifecycleScope: CoroutineScope)
{
    private var tickJob: Job? = null

    private var timeListeners = mutableMapOf<Long, suspend (Long) -> Unit>()
    /*private var secondListeners = mutableMapOf<String, suspend (Long) -> Unit>()
    private var minuteListeners = mutableMapOf<String, suspend (Long) -> Unit>()
    private var hourListeners = mutableMapOf<String, suspend (Long) -> Unit>()*/

    private var before = Calendar.getInstance()

    var delay = 1000L

    private var id = 0L

    fun start()
    {
        tickJob = lifecycleScope.launch(Dispatchers.Default) {
            while (true)
            {
                val now = Calendar.getInstance()

                timeListeners.forEach { it.value.invoke(now.timeInMillis) }

                before = now

                delay(delay)
            }
        }
    }

    /*private suspend fun onTimeChanged(now: Calendar)
    {
        timeListeners.forEach { it.value.invoke(now.timeInMillis) }


        val hourBefore = before.get(Calendar.HOUR_OF_DAY)
        val minuteBefore = before.get(Calendar.MINUTE)
        val secondBefore = before.get(Calendar.SECOND)

        val hourNow = now.get(Calendar.HOUR_OF_DAY)
        val minuteNow = now.get(Calendar.MINUTE)
        val secondNow = now.get(Calendar.SECOND)

        if (secondNow != secondBefore)
            secondListeners.forEach { it.value.invoke(now.timeInMillis) }

        if (minuteNow != minuteBefore)
            minuteListeners.forEach { it.value.invoke(now.timeInMillis) }

        if (hourNow != hourBefore)
            hourListeners.forEach { it.value.invoke(now.timeInMillis) }
    }*/

    fun stop()
    {
        tickJob?.cancel()
    }

    fun addTimeListener(listener: suspend (Long) -> Unit): Long
    {
        val listenerId = id++
        timeListeners[listenerId] = listener
        return listenerId
    }

    fun removeTimeListener(id: Long)
    {
        timeListeners.remove(id)
    }

    fun removeAllTimeListener()
    {
        timeListeners.clear()
    }
}