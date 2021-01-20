package app.tek4tv.digitalsignage.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*

const val PLAYLIST_FILE_NAME = "playlist.json"
const val AUDIO_FOLDER_NAME = "audios"
const val AUDIO_LIST_FILE_NAME = "audio_list.json"

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

@Throws(IOException::class)
fun createFolder(storagePath: String, folderName: String): Boolean {
    val folder = File("$storagePath${File.separator}$folderName")
    var success = false

    if (!folder.exists()) {
        success = folder.mkdir()
    }

    return success
}

@Throws(IOException::class)
fun isFolderExisted(storagePath: String, folderName: String): Boolean {
    val folder = File("$storagePath${File.separator}$folderName")
    return folder.exists() && folder.isDirectory
}

@Throws(IOException::class)
fun getAllFileNameInFolder(storagePath: String): List<String> {
    val folder = File(storagePath)
    val audio = listOf("mp3")
    return folder.listFiles().filter { file -> file.isFile && audio.any { file.name.endsWith(it) } }
        .map { it.name }
}

fun getFolderSize(path: String): Long {
    val folder = File(path)

    return try {
        var size = 0L

        for (f in folder.listFiles()) {
            if (f.isFile)
                size += f.length()

        }

        size
    } catch (e: Exception) {
        -1L
    }
}

