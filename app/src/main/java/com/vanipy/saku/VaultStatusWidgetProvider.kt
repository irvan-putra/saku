package com.vanipy.saku

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.SizeF
import android.widget.RemoteViews

/**
 * Home-screen widget for the saku demo: locked-vault status plus quick-open.
 * Status only - no entry counts, strength scores or any other vault metadata,
 * and there is no real vault behind this build. Every string shown is a static
 * resource, so the widget is synthetic by construction.
 *
 * Four size modes (size-mapped RemoteViews on API 31+, compact fallback
 * below): 1x1 mini badge (lock only), one-row compact, square badge-over-
 * status, expanded card. All are static and tap-through to MainActivity.
 * Widget faces carry no demo wording; demo mode stays marked inside the app.
 */
class VaultStatusWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) updateAppWidget(context, appWidgetManager, id)
    }

    companion object {
        fun openAppIntent(context: Context): Intent = Intent(context, MainActivity::class.java)

        private fun pending(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        private fun views(context: Context, layout: Int, withStatus: Boolean): RemoteViews {
            val v = RemoteViews(context.packageName, layout)
            if (withStatus) v.setTextViewText(R.id.widget_status, context.getString(R.string.widget_status_locked))
            v.setOnClickPendingIntent(R.id.widget_container, pending(context))
            return v
        }

        fun buildViews(context: Context): RemoteViews {
            val compact = views(context, R.layout.widget_vault_status_compact, withStatus = true)
            if (Build.VERSION.SDK_INT < 31) return compact
            return RemoteViews(linkedMapOf(
                SizeF(40f, 40f) to views(context, R.layout.widget_vault_status_mini, withStatus = false),
                SizeF(110f, 48f) to compact,
                SizeF(110f, 110f) to views(context, R.layout.widget_vault_status_square, withStatus = true),
                SizeF(180f, 110f) to views(context, R.layout.widget_vault_status, withStatus = true),
            ))
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            appWidgetManager.updateAppWidget(appWidgetId, buildViews(context))
        }
    }
}
