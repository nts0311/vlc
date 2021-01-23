package app.tek4tv.digitalsignage

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.tek4tv.digitalsignage.ui.MainActivity
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

class CrashHandler(private val activity: Activity) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, ex: Throwable) {

        val appInstance = MyApp.instance

        // TODO : add crash handler to app when building for customer
        if (appInstance != null) {
            logToFile(thread, ex)

            //restartApp(appInstance)
        }
    }

    private fun restartApp(appInstance: MyApp) {
        val intent = Intent(activity, MainActivity::class.java)
        intent.putExtra("crash", true)
        intent.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    or Intent.FLAG_ACTIVITY_NEW_TASK
        )
        val pendingIntent = PendingIntent.getActivity(
            appInstance.baseContext,
            0,
            intent,
            PendingIntent.FLAG_ONE_SHOT
        )
        val mgr = appInstance.baseContext
            .getSystemService(Context.ALARM_SERVICE) as AlarmManager
        mgr[AlarmManager.RTC, System.currentTimeMillis() + 100] = pendingIntent
        activity.finish()
        System.exit(2)
    }

    private fun logToFile(thread: Thread, ex: Throwable) {
        try {
            val logPath = "${activity.applicationContext.filesDir.path}${File.separator}log.txt"
            val out = PrintWriter(BufferedWriter(FileWriter(logPath, true)))
            val df = SimpleDateFormat("yyyy.MM.dd 'at' HH:mm:ss")

            out.println("------------------------")

            out.println("${df.format(Calendar.getInstance().time)}\n")

            out.println("Thread name: ${thread.name}")
            out.println("Message: ${ex.message}")
            out.println("Cause: ${ex.cause}")
            out.println("Stack trace: ")
            ex.printStackTrace(out)

            out.println("------------------------")


            out.close()
        } catch (e: Exception) {

        }
    }
}