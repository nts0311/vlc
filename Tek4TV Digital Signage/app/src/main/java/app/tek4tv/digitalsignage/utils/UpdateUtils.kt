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
    /*val uriInstall = FileProvider.getUriForFile(
        applicationContext, "$packageName.provider", File(
            destination
        )
    )*/
    //Uri.parse("file://$destination")

    //Delete update file if exists
    val file = File(destination)
    if (file.exists()) //file.delete() - test this, I think sometimes it doesnt work
        file.delete()
    //set downloadmanager
    val request = DownloadManager.Request(Uri.parse(url))
    request.setDescription("update version")
    request.setTitle("Updating APK...")

    //set destination
    request.setDestinationUri(uri)

    // get download service and enqueue file
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val downloadId = manager.enqueue(request)

    //set BroadcastReceiver to install app when .apk is downloaded
    val finalDestination = destination
    Log.d("apk", finalDestination)


    val onComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctxt: Context?, intent: Intent?) {
            //
            //
            // Install Updated APK
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

                //                    if (proc.exitValue() == 0) {
//                        // Successfully installed updated app
//                        doRestart();
//                    }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
    }
    //register receiver for when .apk download is compete
    context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
}

fun doRestart() {
    //writeToDevice(buildReadMessage(Define.FUNC_WRITE_RESTART_DEVICE, ""))
}