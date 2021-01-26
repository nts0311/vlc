package app.tek4tv.digitalsignage.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*

const val PLAYLIST_FILE_NAME = "playlist.json"
const val AUDIO_FOLDER_NAME = "audios"

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
    val folder = File(storagePath)
    val audio = listOf("mp3")
    return folder.listFiles().filter { file -> file.isFile && audio.any { file.name.endsWith(it) } }
        .map { it.name }
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

