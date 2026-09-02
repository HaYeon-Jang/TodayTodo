package com.example.todaytodo

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.ZonedDateTime

internal object TodoReminder {
    private const val CHANNEL_ID = "unfinished_todos"
    private const val ALARM_REQUEST_CODE = 2300
    private const val NOTIFICATION_ID = 2301
    private const val EXTRA_TODO_DATE = "todo_date"
    private const val REMINDER_ACTION = "com.example.todaytodo.DAILY_REMINDER"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "미완료 할 일 알림",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "매일 오후 11시에 완료하지 않은 할 일을 알려줍니다."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun scheduleNext(context: Context) {
        val now = ZonedDateTime.now()
        var target = now.withHour(23).withMinute(0).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)

        val intent = Intent(context, TodoReminderReceiver::class.java).apply {
            action = REMINDER_ACTION
            putExtra(EXTRA_TODO_DATE, target.toLocalDate().toString())
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            target.toInstant().toEpochMilli(),
            pendingIntent,
        )
    }

    fun handleReminder(context: Context, dateText: String?) {
        createNotificationChannel(context)
        val reminderDate = runCatching { LocalDate.parse(dateText) }.getOrDefault(LocalDate.now())
        val unfinished = TodoStore(context.applicationContext).load()
            .filter { it.date == reminderDate && !it.completed }

        if (unfinished.isNotEmpty()) {
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val preview = unfinished.take(3).joinToString(", ") { it.title }
            val notification = android.app.Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.example.todaytodo.R.drawable.ic_notification_check)
                .setContentTitle("아직 완료하지 않은 할 일이 ${unfinished.size}개 있어요")
                .setContentText(preview)
                .setStyle(android.app.Notification.BigTextStyle().bigText(preview))
                .setContentIntent(openAppPendingIntent)
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification)
        }
        scheduleNext(context)
    }

    fun isReminderAction(action: String?): Boolean = action == REMINDER_ACTION
    fun reminderDate(intent: Intent): String? = intent.getStringExtra(EXTRA_TODO_DATE)
}

class TodoReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            TodoReminder.scheduleNext(context)
        } else if (TodoReminder.isReminderAction(intent.action)) {
            TodoReminder.handleReminder(context, TodoReminder.reminderDate(intent))
        }
    }
}
