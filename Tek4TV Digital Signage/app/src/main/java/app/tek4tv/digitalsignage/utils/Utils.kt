package app.tek4tv.digitalsignage.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import androidx.core.content.ContextCompat
import com.squareup.moshi.Moshi
import java.net.NetworkInterface
import java.util.*

class Utils {
    companion object {
        const val STORAGE_INFO = "STORAGE_INFO"
        const val ping = "Ping"
        const val DEVICE_ID = "device_id"
        const val APP_VERSION = "APP_VERSION"
        const val NETWORK_INFO = "NETWORK_INFO"
        const val DEVICE_LOCATION = "DEVICE_LOCATION"
        const val DIRECT_MESSAGE = "DirectMessage"
        const val DEVICE_INFO = "DEVICE_INFO"
        const val GET_AUDIO_PATH = "GET_AUDIO_PATH"
        const val GET_MEDIA_PATH = "GET_MEDIA_PATH"

        @SuppressLint("HardwareIds")
        fun getDeviceId(context: Context): String {
            var deviceId: String? = ConfigUtil.getString(context, DEVICE_ID, null)
            if (deviceId == null) {
                deviceId = getMacAddr()
                ConfigUtil.putString(context, DEVICE_ID, deviceId)
            }
            return deviceId
        }

        fun getMacAddr(): String {
            try {
                val all: List<NetworkInterface> =
                    Collections.list(NetworkInterface.getNetworkInterfaces())
                for (nif in all) {
                    if (!nif.name.equals("wlan0", ignoreCase = true)) continue
                    val macBytes = nif.hardwareAddress ?: return ""
                    val res1 = StringBuilder()
                    for (b in macBytes) {
                        //res1.append(Integer.toHexString(b & 0xFF) + ":");
                        res1.append(String.format("%02X:", b))
                    }
                    if (res1.length > 0) {
                        res1.deleteCharAt(res1.length - 1)
                    }
                    return res1.toString()
                }
            } catch (ex: Exception) {
            }
            return "02:00:00:00:00:00"
        }

        fun getRootDirPath(context: Context): String {
            return if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
                val file = ContextCompat.getExternalFilesDirs(
                    context.applicationContext,
                    null
                )[0]
                file.absolutePath
            } else {
                context.applicationContext.filesDir.absolutePath
            }
        }

        fun <T> toJsonString(moshi: Moshi, type: Class<T>, value: T): String {
            val adapter = moshi.adapter(type)
            return adapter.toJson(value)
        }
    }
}