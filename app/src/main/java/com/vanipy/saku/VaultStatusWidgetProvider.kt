package com.vanipy.saku

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Home-screen widget for the saku demo: locked-vault status plus quick-open.
 * Status text only - no entry counts, strength scores or any other vault
 * metadata, and there is no real vault behind this build. Every string shown
 * is a static resource, so the widget is synthetic by construction.
 */
class VaultStatusWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) updateAppWidget(context, appWidgetManager, id)
    }

    companion object {
        fun openAppIntent(context: Context): Intent = Intent(context, MainActivity::class.java)

        fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_vault_status)
            views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_status_locked))
            val pending = PendingIntent.getActivity(
                context, 0, openAppIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_container, pending)
            return views
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            appWidgetManager.updateAppWidget(appWidgetId, buildViews(context))
        }
    }
}
