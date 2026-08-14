package com.surafel.audio

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

open class AudioWidgetProvider(private val style: Int) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        update(context, manager, appWidgetId)
    }

    private fun update(context: Context, manager: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_audio)

        val groups = intArrayOf(
            R.id.widgetClassic,
            R.id.widgetLite,
            R.id.widgetSimple,
            R.id.widgetMini,
            R.id.widgetPractical,
            R.id.widgetFeatureRich,
            R.id.widgetStandard,
            R.id.widgetStylish
        )
        groups.forEach { views.setViewVisibility(it, View.GONE) }

        val backgrounds = intArrayOf(
            R.drawable.widget_bg_classic,
            R.drawable.widget_bg_lite,
            R.drawable.widget_bg_simple,
            R.drawable.widget_bg_mini,
            R.drawable.widget_bg_practical,
            R.drawable.widget_bg_feature_rich,
            R.drawable.widget_bg_standard,
            R.drawable.widget_bg_stylish
        )
        val safeStyle = style.coerceIn(0, backgrounds.lastIndex)
        views.setInt(R.id.widgetRoot, "setBackgroundResource", backgrounds[safeStyle])
        views.setViewVisibility(groups[safeStyle], View.VISIBLE)

        // Match the artwork language of the supplied references instead of using one generic card.
        when (safeStyle) {
            0 -> views.setImageViewResource(R.id.classicArtwork, R.drawable.widget_art_sunset)
            4 -> views.setImageViewResource(R.id.practicalArtwork, R.drawable.widget_art_sunset)
            5 -> views.setImageViewResource(R.id.featureArtwork, R.drawable.widget_art_neon)
            6 -> views.setImageViewResource(R.id.standardArtwork, R.drawable.ic_audio)
            7 -> views.setImageViewResource(R.id.stylishArtwork, R.drawable.widget_art_ocean)
        }

        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val clickTargets = intArrayOf(
            R.id.widgetRoot,
            R.id.widgetPrev, R.id.widgetPlay, R.id.widgetNext,
            R.id.liteShuffle, R.id.litePrev, R.id.litePlay, R.id.liteNext, R.id.liteHeart,
            R.id.simpleShuffle, R.id.simplePrev, R.id.simplePlay, R.id.simpleNext, R.id.simpleHeart,
            R.id.miniPrev, R.id.miniPlay, R.id.miniNext,
            R.id.practicalShuffle, R.id.practicalPrev, R.id.practicalPlay, R.id.practicalNext, R.id.practicalHeart,
            R.id.featureShuffle, R.id.featurePrev, R.id.featurePlay, R.id.featureNext, R.id.featureHeart,
            R.id.standardPrev, R.id.standardPlay, R.id.standardNext,
            R.id.stylishPrev, R.id.stylishPlay, R.id.stylishNext
        )
        clickTargets.forEach { views.setOnClickPendingIntent(it, open) }

        // Keep progress bars visually consistent with the reference widgets.
        views.setProgressBar(R.id.simpleProgress, 100, 48, false)
        views.setProgressBar(R.id.practicalProgress, 100, 52, false)
        views.setProgressBar(R.id.featureProgress, 100, 46, false)
        views.setProgressBar(R.id.standardProgress, 100, 50, false)

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
