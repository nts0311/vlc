package app.tek4tv.digitalsignage.utils

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import kotlin.jvm.Throws

const val PLAYLIST_FILE_NAME = "playlist.json"
const val AUDIO_LIST_FILE_NAME = "audio_list.json"
const val AUDIO_FOLDER_NAME = "audios"
const val RECORDED_AUDIO_FOLDER_NAME = "recorded"

fun isFileExisted(storagePath: String, fileName: String): Boolean {
    val file = File(storagePath, fileName)
    return file.exists() && file.isFile
}


@Throws(IOException::class)
suspend fun readFile(filePath: String): String {
    return withContext(Dispatchers.IO)
    {
        val br = BufferedReader(FileReader(filePath))

        val res = br.lineSequence().toList().joinToString(separator = "")
        br.close()
        res
    }
}

@Throws(IOException::class)
suspend fun writeFile(filePath: String, content: String) {
    withContext(Dispatchers.IO)
    {
        val bw = BufferedWriter(FileWriter(filePath))
        bw.write(content)
        bw.close()
    }
}

fun createFolder(storagePath: String): Boolean {
    val folder = File(storagePath)
    var success = false

    if (!folder.exists()) {
        success = folder.mkdir()
    }

    return success
}

fun isFolderExisted(storagePath: String): Boolean {
    val folder = File(storagePath)
    return folder.exists() && folder.isDirectory
}

fun getAllFileNameInFolder(storagePath: String): List<String> {
    return try {
        Log.d("filex",storagePath)
        val folder = File(storagePath)
        val audio = listOf("mp3")
        folder.listFiles().filter { file -> file.isFile && audio.any { file.name.endsWith(it) } }
            .map { it.name }
    } catch (e: Exception) {
        e.printStackTrace()
        listOf()
    }

}

fun getAllFilePathInFolder(storagePath: String): List<String> {
    val folder = File(storagePath)
    return folder.listFiles().filter { it.isFile }.map { it.path }
}

fun getFolderSize(path: String): Long {
    val folder = File(path)

    return try {
        var size = 0L

        for (f in folder.listFiles()) {
            if (f.isFile) size += f.length()
        }

        size
    } catch (e: Exception) {
        e.printStackTrace()
        -1L
    }
}

fun deleteAllFileInFolder(path: String) {
    val folder = File(path)
    try {
        for (f in folder.listFiles()) {
            if (f.isFile) f.delete()
        }

    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun getFileNameFromUrl(url: String): String {
    var fileName = File(Uri.parse(url).path).name
    val slash = fileName.lastIndexOf("\\")

    if (slash != -1) {
        fileName = fileName.substring(slash + 1)
    }
    return fileName
}

