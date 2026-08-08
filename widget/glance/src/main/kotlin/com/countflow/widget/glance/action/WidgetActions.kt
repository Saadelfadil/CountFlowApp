package com.countflow.widget.glance.action

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import com.countflow.widget.glance.configuration.WidgetConfigurationActivity

/**
 * Opens the app.
 *
 * Not `actionStartActivity<MainActivity>()` — `:widget:glance` cannot reference `MainActivity`
 * without depending on `:app`, and `:app` depends on `:widget:glance`, not the other way. An
 * [ActionCallback] gets a real [Context] at click time instead, and asks the [android.content.pm.PackageManager]
 * for whatever the launcher would launch — which also means a future `MainActivity` rename
 * cannot silently break this tap target the way a hardcoded component name could.
 */
internal class OpenAppAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

/**
 * Opens widget configuration for the tapped widget.
 *
 * Used for the unconfigured placeholder state and doubles as the "reconfigure" entry point from
 * within the widget itself. [GlanceAppWidgetManager.getAppWidgetId] is what recovers the real,
 * launcher-assigned id from the [GlanceId] Glance hands the callback — the same id
 * [WidgetConfigurationActivity] reads from `EXTRA_APPWIDGET_ID` when the *system* launches it,
 * so both entry points reach the activity through one code path.
 */
internal class OpenConfigurationAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val intent = Intent(context, WidgetConfigurationActivity::class.java).apply {
            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

/** Action for tapping a configured widget: opens the app. */
internal fun actionOpenApp() = actionRunCallback<OpenAppAction>()

/** Action for tapping an unconfigured widget: opens configuration for it. */
internal fun actionOpenConfiguration() = actionRunCallback<OpenConfigurationAction>()
