package app.tek4tv.digitalsignage.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import app.tek4tv.digitalsignage.repo.MediaRepo
import app.tek4tv.digitalsignage.ui.AlarmReceiver
import java.util.*

class MediaScheduler(private val appContext: Context, private val mediaRepo: MediaRepo) {

    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleListAlarms() {
        val scheduledList = mediaRepo.scheduledList

        var maxEndKey = ""
        var maxEnd = 0L

        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)

            val requestCode = mediaRepo.dividers[period]!![0].id

            val t = period.split("-")
            val start = toCalendar(t[0])
            val end = toCalendar(t[1])

            val now = Calendar.getInstance().timeInMillis

            if (now > end.timeInMillis) continue

            if (end.timeInMillis > maxEnd) {
                maxEnd = end.timeInMillis
                maxEndKey = period
            }

            setAlarmForList(period, requestCode, start)
        }

        //setting an alarm when all playlist is played, play nothing
        val requestCodeEnd = mediaRepo.dividers[maxEndKey]!![1].id
        setAlarmForList(maxEndKey, requestCodeEnd,
            Calendar.getInstance().apply { timeInMillis = maxEnd }, true)
    }

    private fun setAlarmForList(
        listKey: String, requestCode: Int, time: Calendar, shouldEndPlaying: Boolean = false
    ) {
        val isAlarmWorking =
            getScheduleListIntent(listKey, requestCode, PendingIntent.FLAG_NO_CREATE,
                shouldEndPlaying) != null

        if (isAlarmWorking) return

        Log.d("alarmx", "Set $listKey - $requestCode - $time")
        val alarmIntent = getScheduleListIntent(listKey, requestCode, 0, shouldEndPlaying)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.timeInMillis,
            alarmIntent)
    }

    fun cancelAllListAlarm() {
        val scheduledList = mediaRepo.scheduledList
        var maxEndKey = ""
        var maxEnd = 0L

        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)

            val requestCode = mediaRepo.dividers[period]!![0].id

            val t = period.split("-")
            val end = toCalendar(t[1])

            if (end.timeInMillis > maxEnd) {
                maxEndKey = period
            }

            cancelListAlarm(period, requestCode)
        }

        val requestCodeEnd = mediaRepo.dividers[maxEndKey]!![1].id
        cancelListAlarm(maxEndKey, requestCodeEnd, true)
    }

    private fun cancelListAlarm(
        listKey: String, requestCode: Int, shouldEndPlaying: Boolean = false
    ) {
        val isAlarmWorking =
            getScheduleListIntent(listKey, requestCode, PendingIntent.FLAG_NO_CREATE,
                shouldEndPlaying) != null

        if (!isAlarmWorking) return

        Log.d("alarmx", "Cancel $listKey - $requestCode")
        val alarmIntent = getScheduleListIntent(listKey, requestCode, 0, shouldEndPlaying)
        alarmManager.cancel(alarmIntent)
        alarmIntent?.cancel()
    }

    private fun getScheduleListIntent(
        listKey: String, requestCode: Int, flag: Int, shouldEndPlaying: Boolean = false
    ): PendingIntent? {

        val alarmAction = if (shouldEndPlaying) AlarmReceiver.ACTION_END_PLAYLIST
        else AlarmReceiver.ACTION_PLAY_MEDIA_LIST

        val intent = Intent(appContext, AlarmReceiver::class.java).apply {
            action = alarmAction
            putExtra(AlarmReceiver.SCHEDULED_LIST_KEY, listKey)
        }

        return PendingIntent.getBroadcast(appContext, requestCode, intent, flag)
    }

    private fun getScheduleMediaIntent(
        mediaUri: String, requestCode: Int, flag: Int
    ): PendingIntent? {
        val intent = Intent(appContext, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_PLAY_MEDIA
            putExtra(AlarmReceiver.SCHEDULED_MEDIA_URL, mediaUri)
        }

        return PendingIntent.getBroadcast(appContext, requestCode, intent, flag)
    }
}