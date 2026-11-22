package app.glass.tresor.disguise

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.glass.tresor.MainActivity
import app.glass.tresor.R

/** Wetter-Widget für den Startbildschirm – nur verfügbar, solange die Tarnung „Wetter“ aktiv ist. */
class WeatherWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        render(ctx, mgr, ids)
        val city = WeatherData.city(ctx) ?: return
        val pending = goAsync()
        Thread {
            try {
                WeatherData.fetch(ctx, city)
                render(ctx, mgr, ids)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val city = WeatherData.city(ctx)
            val now = WeatherData.cached(ctx)
            for (id in ids) {
                val v = RemoteViews(ctx.packageName, R.layout.widget_weather)
                v.setTextViewText(R.id.wx_city, city?.name ?: "Wetter")
                if (now != null) {
                    v.setTextViewText(R.id.wx_temp, WeatherData.deg(now.temp))
                    v.setTextViewText(R.id.wx_icon, WeatherData.symbol(now.code, now.day))
                    v.setTextViewText(R.id.wx_desc, WeatherData.text(now.code))
                    val d = now.days.firstOrNull()
                    v.setTextViewText(R.id.wx_range, if (d != null) "H: ${WeatherData.deg(d.max)}  T: ${WeatherData.deg(d.min)}" else "")
                } else {
                    v.setTextViewText(R.id.wx_temp, "–")
                    v.setTextViewText(R.id.wx_icon, "☁")
                    v.setTextViewText(R.id.wx_desc, if (city == null) "Stadt in der App wählen" else "Wird geladen …")
                    v.setTextViewText(R.id.wx_range, "")
                }
                val open = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                v.setOnClickPendingIntent(R.id.wx_root, PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                mgr.updateAppWidget(id, v)
            }
        }

        /** Alle Widgets neu zeichnen (nach dem Laden in der App). */
        fun refreshAll(ctx: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, WeatherWidget::class.java))
                if (ids.isNotEmpty()) render(ctx, mgr, ids)
            } catch (_: Exception) {
            }
        }
    }
}
