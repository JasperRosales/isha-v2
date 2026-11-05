package dev.jasper.ishav2.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import dev.jasper.ishav2.IshaApp
import dev.jasper.ishav2.MainActivity
import dev.jasper.ishav2.R
import java.time.ZonedDateTime

/**
 * Home-screen widget: the next deadline, what's left of this month's budget, and
 * buttons to talk to isha or start a typed chat. Everything comes from data on the
 * phone, so it works offline. It refreshes when deadlines, budget or the theme
 * change (see AppContainer) and every 30 minutes so "Today"/"Tomorrow" stay right.
 */
class IshaWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        update(context)
    }

    companion object {
        fun update(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(app, IshaWidget::class.java))
            if (ids.isEmpty()) return
            val container = (app as IshaApp).container
            val summary = widgetSummary(
                deadlines = container.deadlines.data.value.items,
                budget = container.budget.data.value,
                now = ZonedDateTime.now()
            )
            manager.updateAppWidget(ids, render(app, summary, container.settings.current.darkTheme))
        }

        private fun render(context: Context, s: WidgetSummary, dark: Boolean): RemoteViews {
            val c = if (dark) DARK else LIGHT
            return RemoteViews(context.packageName, R.layout.widget_isha).apply {
                setInt(R.id.widget_root, "setBackgroundResource", if (dark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light)
                setInt(R.id.widget_ask, "setBackgroundResource", if (dark) R.drawable.widget_button_dark else R.drawable.widget_button_light)
                setTextColor(R.id.widget_name, c.ink)
                setTextColor(R.id.widget_ask_label, c.ink)
                setInt(R.id.widget_ask_icon, "setColorFilter", c.accent)
                setInt(R.id.widget_talk_icon, "setColorFilter", ON_ACCENT)

                setTextViewText(
                    R.id.widget_due_label,
                    if (s.overdue > 0) context.getString(R.string.widget_due_next) + " · ${s.overdue} overdue"
                    else context.getString(R.string.widget_due_next)
                )
                setTextColor(R.id.widget_due_label, if (s.overdue > 0) c.danger else c.muted)
                setTextViewText(R.id.widget_due_title, s.dueTitle)
                setTextColor(R.id.widget_due_title, c.ink)
                setTextViewText(R.id.widget_due_detail, s.dueDetail)
                setTextColor(R.id.widget_due_detail, c.muted)

                setTextColor(R.id.widget_budget_label, c.muted)
                setTextViewText(R.id.widget_budget_value, s.budgetValue)
                setTextColor(R.id.widget_budget_value, if (s.overBudget) c.danger else c.ink)
                setTextViewText(R.id.widget_budget_detail, s.budgetDetail)
                setTextColor(R.id.widget_budget_detail, if (s.overBudget) c.danger else c.muted)

                setOnClickPendingIntent(R.id.widget_header, open(context, null, 0))
                setOnClickPendingIntent(R.id.widget_ask, open(context, MainActivity.ACTION_NEW_CHAT, 1))
                setOnClickPendingIntent(R.id.widget_talk, open(context, MainActivity.ACTION_TALK, 2))
                setOnClickPendingIntent(R.id.widget_due, open(context, MainActivity.ACTION_OPEN_DUE, 3))
                setOnClickPendingIntent(R.id.widget_budget, open(context, MainActivity.ACTION_OPEN_BUDGET, 4))
            }
        }

        private fun open(context: Context, action: String?, requestCode: Int): PendingIntent =
            PendingIntent.getActivity(
                context,
                requestCode,
                MainActivity.actionIntent(context, action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        private class Palette(val ink: Int, val muted: Int, val accent: Int, val danger: Int)

        // Same values as the app theme (ui/theme/Color.kt).
        private val DARK = Palette(0xFFF7EEDD.toInt(), 0xFFA9987F.toInt(), 0xFFFF8A3D.toInt(), 0xFFFF6F61.toInt())
        private val LIGHT = Palette(0xFF241F19.toInt(), 0xFF7C6F5D.toInt(), 0xFFFF6D1F.toInt(), 0xFFB3261E.toInt())
        private const val ON_ACCENT = 0xFF1B1108.toInt()
    }
}
