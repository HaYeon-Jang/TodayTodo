package com.example.todaytodo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate

class TodoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        widgetIds.forEach { updateWidget(context, manager, it) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TodoWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val unfinished = TodoStore(context.applicationContext).load()
                .filter { it.date == LocalDate.now() && !it.completed }
            val views = RemoteViews(context.packageName, R.layout.todo_widget)
            views.setTextViewText(
                R.id.widget_summary,
                if (unfinished.isEmpty()) "오늘 할 일을 모두 마쳤어요" else "오늘 남은 할 일 ${unfinished.size}개",
            )

            val rowIds = intArrayOf(R.id.widget_todo_1, R.id.widget_todo_2, R.id.widget_todo_3)
            rowIds.forEachIndexed { index, viewId ->
                val todo = unfinished.getOrNull(index)
                views.setViewVisibility(viewId, if (todo == null) View.GONE else View.VISIBLE)
                todo?.let { views.setTextViewText(viewId, "□  ${it.title}") }
            }

            val openApp = PendingIntent.getActivity(
                context,
                2400,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            manager.updateAppWidget(widgetId, views)
        }
    }
}
