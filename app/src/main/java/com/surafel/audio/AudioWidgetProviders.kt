package com.surafel.audio

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews

open class AudioWidgetProvider(private val style: Int) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { ids.forEach { update(context, manager, it) } }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) { update(context, manager, appWidgetId) }

    private fun update(context: Context, manager: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_audio)
        val colors = intArrayOf(Color.rgb(31,18,60), Color.rgb(10,104,128), Color.rgb(32,35,86), Color.rgb(60,28,74), Color.rgb(93,40,82), Color.rgb(38,22,92), Color.rgb(31,64,155), Color.rgb(101,39,104))
        views.setInt(R.id.widgetRoot, "setBackgroundColor", colors[style.coerceIn(0, colors.lastIndex)])
        views.setTextViewText(R.id.widgetTitle, "Music Player")
        views.setTextViewText(R.id.widgetSubtitle, if (style >= 4) "Enjoy Listening" else "")
        views.setViewVisibility(R.id.widgetSubtitle, if (style >= 4) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(R.id.widgetProgress, if (style == 2 || style >= 4) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(R.id.widgetExtra, if (style == 5) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(R.id.widgetShuffle, if (style >= 4) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(R.id.widgetHeart, if (style >= 1) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(R.id.widgetArtwork, if (style >= 2) android.view.View.VISIBLE else android.view.View.GONE)
        views.setTextViewText(R.id.widgetExtra, "1. Love song\n2. Dancing with your ghost")
        val open = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        listOf(R.id.widgetRoot, R.id.widgetPlay, R.id.widgetNext, R.id.widgetPrev, R.id.widgetShuffle, R.id.widgetHeart).forEach { views.setOnClickPendingIntent(it, open) }
        manager.updateAppWidget(id, views)
    }
}

class AudioWidgetClassicProvider : AudioWidgetProvider(0)
class AudioWidgetLiteProvider : AudioWidgetProvider(1)
class AudioWidgetSimpleProvider : AudioWidgetProvider(2)
class AudioWidgetMiniProvider : AudioWidgetProvider(3)
class AudioWidgetPracticalProvider : AudioWidgetProvider(4)
class AudioWidgetFeatureRichProvider : AudioWidgetProvider(5)
class AudioWidgetStandardProvider : AudioWidgetProvider(6)
class AudioWidgetStylishProvider : AudioWidgetProvider(7)
