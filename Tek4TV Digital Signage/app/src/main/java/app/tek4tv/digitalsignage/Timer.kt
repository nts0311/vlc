package app.tek4tv.digitalsignage

import kotlinx.coroutines.*
import java.util.*

class Timer(private val coroutineScope: CoroutineScope) {
    private var tickJob: Job? = null

    private var timeListeners = mutableMapOf<Long, suspend (Long) -> Unit>()

    private var before = Calendar.getInstance()

    var delay = 1000L

    private var id = 0L

    fun start()
    {
        tickJob = coroutineScope.launch(Dispatchers.Default) {
            while (true) {
                val now = Calendar.getInstance()

                timeListeners.forEach { it.value.invoke(now.timeInMillis) }

                before = now

                delay(delay)
            }
        }
    }

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

    fun addTimeListener(dispatcher: CoroutineDispatcher, listener: suspend (Long) -> Unit): Long
    {
        val listenerId = id++
        timeListeners[listenerId] = {
            withContext(dispatcher)
            {
                listener.invoke(it)
            }
        }
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