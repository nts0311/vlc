package app.tek4tv.digitalsignage.repo

import android.util.Log
import app.tek4tv.digitalsignage.utils.readFile
import app.tek4tv.digitalsignage.utils.writeFile
import com.squareup.moshi.JsonAdapter
import kotlinx.coroutines.*
import java.io.IOException

abstract class BaseRepo<T>(
    protected val fileStoragePath: String
) {
    abstract val jsonAdapter: JsonAdapter<List<T>>

    private var saveFileJob: Job? = null

    protected val coroutineScope = CoroutineScope(Dispatchers.Default)

    suspend fun saveItemsToFile(playlist: List<T>) {
        saveFileJob?.join()
        saveFileJob = coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val json = jsonAdapter.toJson(playlist)
                Log.d("writefile", json)

                try {
                    writeFile(fileStoragePath, json)
                } catch (e: IOException) {
                    Log.e("BaseRepo", "error writing playlist!!!")
                }
            }
        }
    }

    suspend fun readItemsFromFile(): List<T> {

        var result = listOf<T>()

        withContext(Dispatchers.IO) {
            try {
                val contentFromFile = readFile(fileStoragePath)
                result = jsonAdapter.fromJson(contentFromFile) ?: listOf()
            } catch (e: IOException) {
                Log.e("BaseRepo", "error reading playlist!!!")
            }

        }
        return result
    }

    fun cancelAllJob() {
        coroutineScope.cancel()
    }
}