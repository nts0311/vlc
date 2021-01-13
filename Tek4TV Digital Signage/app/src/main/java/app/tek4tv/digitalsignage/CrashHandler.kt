package app.tek4tv.digitalsignage

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.tek4tv.digitalsignage.ui.MainActivity

class CrashHandler(private val activity: Activity) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, ex: Throwable) {

        val appInstance = MyApp.instance

        if (appInstance != null) {
            val intent = Intent(activity, MainActivity::class.java)
            intent.putExtra("crash", true)
            intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        or Intent.FLAG_ACTIVITY_NEW_TASK
            )
            val pendingIntent = PendingIntent.getActivity(
                appInstance.getBaseContext(),
                0,
                intent,
                PendingIntent.FLAG_ONE_SHOT
            )
            val mgr = appInstance.getBaseContext()
                .getSystemService(Context.ALARM_SERVICE) as AlarmManager
            mgr[AlarmManager.RTC, System.currentTimeMillis() + 100] = pendingIntent
            activity.finish()
            System.exit(2)
        }
    }
}