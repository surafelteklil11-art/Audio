package com.surafel.audio

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Base provider for the eight real, independently laid-out launcher widgets. */
open class AudioWidgetProvider(
    private val layout: Int,
    private val artwork: Int,
    private val titleId: Int,
    private val artistId: Int,
    private val actionIds: IntArray,
    private val actionKinds: Array<String>,
    private val progressId: Int? = null
) : AppWidgetProvider() {

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
        val views = RemoteViews(context.packageName, layout)
        views.setImageViewResource(artworkViewId(layout), artwork)
        views.setTextViewText(titleId, "Music Player")
        views.setTextViewText(artistId, "Enjoy Listening")

        actionIds.forEachIndexed { index, viewId ->
            views.setOnClickPendingIntent(
                viewId,
                actionPendingIntent(context, id, actionKinds[index])
            )
        }
        progressId?.let { views.setProgressBar(it, 100, 50, false) }

        views.setOnClickPendingIntent(
            rootId(layout),
            PendingIntent.getActivity(
                context,
                id * 10 + 9,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        manager.updateAppWidget(id, views)
    }

    private fun actionPendingIntent(context: Context, widgetId: Int, kind: String): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).setAction(kind)
        return PendingIntent.getBroadcast(
            context,
            widgetId * 10 + kind.hashCode().and(0x7fffffff) % 10,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun rootId(layout: Int): Int = when (layout) {
        R.layout.widget_classic -> R.id.widgetClassicRoot
        R.layout.widget_lite -> R.id.widgetLiteRoot
        R.layout.widget_simple -> R.id.widgetSimpleRoot
        R.layout.widget_mini -> R.id.widgetMiniRoot
        R.layout.widget_practical -> R.id.widgetPracticalRoot
        R.layout.widget_feature_rich -> R.id.widgetFeatureRoot
        R.layout.widget_standard -> R.id.widgetStandardRoot
        else -> R.id.widgetStylishRoot
    }

    private fun artworkViewId(layout: Int): Int = when (layout) {
        R.layout.widget_classic -> R.id.classicArtwork
        R.layout.widget_simple -> R.id.simpleArtwork
        R.layout.widget_mini -> R.id.miniArtwork
        R.layout.widget_practical -> R.id.practicalArtwork
        R.layout.widget_feature_rich -> R.id.featureArtwork
        R.layout.widget_standard -> R.id.standardArtwork
        else -> R.id.stylishArtwork
    }
}

private object WidgetActions {
    const val PLAY_PAUSE = WidgetActionReceiver.ACTION_PLAY_PAUSE
    const val PREVIOUS = WidgetActionReceiver.ACTION_PREVIOUS
    const val NEXT = WidgetActionReceiver.ACTION_NEXT
    const val SHUFFLE = WidgetActionReceiver.ACTION_SHUFFLE
}

class AudioWidgetClassicProvider : AudioWidgetProvider(
    R.layout.widget_classic,
    R.drawable.widget_art_sunset,
    R.id.classicTitle,
    R.id.classicArtist,
    intArrayOf(R.id.classicPrev, R.id.classicPlay, R.id.classicNext),
    arrayOf(WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT)
)

class AudioWidgetLiteProvider : AudioWidgetProvider(
    R.layout.widget_lite,
    R.drawable.widget_art_ocean,
    R.id.liteTitle,
    R.id.liteTitle,
    intArrayOf(R.id.liteShuffle, R.id.litePrev, R.id.litePlay, R.id.liteNext),
    arrayOf(WidgetActions.SHUFFLE, WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT)
)

class AudioWidgetSimpleProvider : AudioWidgetProvider(
    R.layout.widget_simple,
    R.drawable.widget_art_ocean,
    R.id.simpleTitle,
    R.id.simpleArtist,
    intArrayOf(R.id.simpleShuffle, R.id.simplePrev, R.id.simplePlay, R.id.simpleNext),
    arrayOf(WidgetActions.SHUFFLE, WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT),
    R.id.simpleProgress
)

class AudioWidgetMiniProvider : AudioWidgetProvider(
    R.layout.widget_mini,
    R.drawable.widget_art_neon,
    R.id.miniTitle,
    R.id.miniTitle,
    intArrayOf(R.id.miniPrev, R.id.miniPlay, R.id.miniNext),
    arrayOf(WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT)
)

class AudioWidgetPracticalProvider : AudioWidgetProvider(
    R.layout.widget_practical,
    R.drawable.bg_reference_sunset,
    R.id.practicalTitle,
    R.id.practicalArtist,
    intArrayOf(R.id.practicalShuffle, R.id.practicalPrev, R.id.practicalPlay, R.id.practicalNext),
    arrayOf(WidgetActions.SHUFFLE, WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT),
    R.id.practicalProgress
)

class AudioWidgetFeatureRichProvider : AudioWidgetProvider(
    R.layout.widget_feature_rich,
    R.drawable.widget_art_neon,
    R.id.featureTitle,
    R.id.featureArtist,
    intArrayOf(R.id.featureShuffle, R.id.featurePrev, R.id.featurePlay, R.id.featureNext),
    arrayOf(WidgetActions.SHUFFLE, WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT),
    R.id.featureProgress
)

class AudioWidgetStandardProvider : AudioWidgetProvider(
    R.layout.widget_standard,
    R.drawable.widget_art_ocean,
    R.id.standardTitle,
    R.id.standardArtist,
    intArrayOf(R.id.standardPrev, R.id.standardPlay, R.id.standardNext),
    arrayOf(WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT),
    R.id.standardProgress
)

class AudioWidgetStylishProvider : AudioWidgetProvider(
    R.layout.widget_stylish,
    R.drawable.bg_reference_sunset,
    R.id.stylishTitle,
    R.id.stylishArtist,
    intArrayOf(R.id.stylishPrev, R.id.stylishPlay, R.id.stylishNext),
    arrayOf(WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT)
)
