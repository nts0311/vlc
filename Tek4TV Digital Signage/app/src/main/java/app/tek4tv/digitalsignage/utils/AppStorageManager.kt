package app.tek4tv.digitalsignage.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File


class AppStorageManager(appContext: Context) {

    private val fileDirPath = appContext.filesDir.path
    private val audioDirPath = "$fileDirPath${File.separator}$AUDIO_FOLDER_NAME"
    private val recordedAudioDirPath = "$fileDirPath${File.separator}$RECORDED_AUDIO_FOLDER_NAME"
    private val activityManager =
        appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val memoryInfo = ActivityManager.MemoryInfo()

    init {
        activityManager.getMemoryInfo(memoryInfo)
    }

    fun getTotalMediaSize() = getFolderSize(fileDirPath)
    fun getTotalMusicSize() = getFolderSize(audioDirPath)

    fun getStorageUseByApp() = getTotalMediaSize() + getTotalMusicSize()

    fun deleteAllMusic() {
        deleteAllFileInFolder(audioDirPath)
    }

    fun deleteAllMedia() {
        deleteAllFileInFolder(fileDirPath)
    }

    private fun getTotalStorageInfo(path: String?): Long {
        val statFs = StatFs(path)
        return statFs.totalBytes // remember to convert in GB,MB or KB.
    }

    private fun getUsedStorageInfo(path: String?): Long {
        val statFs = StatFs(path)
        return statFs.totalBytes - statFs.availableBytes // remember to convert in GB,MB or KB.
    }

    fun getTotalRam() = memoryInfo.totalMem
    fun getAvailRam() = memoryInfo.availMem
    fun getUsedRam() = getTotalRam() - getAvailRam()

    fun getTotalRomStorage() = getTotalStorageInfo(Environment.getDataDirectory().path)
    fun getUsedRomStorage() = getUsedStorageInfo(Environment.getDataDirectory().path)

    fun getStorageCapacity(): Long {
        var r = 0L

        try {
            r += getTotalStorageInfo("/system")
            r += getTotalStorageInfo("/data")
            r += getTotalStorageInfo("/cache")
            r += getTotalStorageInfo("/misc")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return r
    }

    fun getAllMusicPath(): List<String> = getAllFilePathInFolder(audioDirPath)

    fun getAllMediaPath() = getAllFilePathInFolder(fileDirPath).filter {
        !it.endsWith(PLAYLIST_FILE_NAME) && !it.endsWith("log.txt")
    }

    fun getAllRecordedAudioPath() = getAllFilePathInFolder(recordedAudioDirPath)
}