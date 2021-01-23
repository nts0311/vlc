package app.tek4tv.digitalsignage.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.os.StrictMode
import android.util.Log
import java.io.File

fun downloadUpdateApk(url: String, context: Context) {
    var destination: String =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .toString() + "/"
    val fileName = "AppName.apk"
    destination += fileName
    val uri = Uri.parse("file://$destination")

    val file = File(destination)
    if (file.exists())
        file.delete()

    val request = DownloadManager.Request(Uri.parse(url))
    request.setDescription("update version")
    request.setTitle("Updating APK...")

    request.setDestinationUri(uri)

    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val downloadId = manager.enqueue(request)

    val finalDestination = destination
    Log.d("apk", finalDestination)


    val onComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctxt: Context?, intent: Intent?) {
            try {
                val builder = StrictMode.VmPolicy.Builder()
                StrictMode.setVmPolicy(builder.build())

                val install = Intent(Intent.ACTION_VIEW)
                install.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                install.setDataAndType(
                    uri,
                    manager.getMimeTypeForDownloadedFile(downloadId)
                )
                context.startActivity(install)

            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
    }
    context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
}

