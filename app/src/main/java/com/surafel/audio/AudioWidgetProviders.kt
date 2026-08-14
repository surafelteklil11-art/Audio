package com.surafel.audio

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Base provider for eight independent launcher widgets. Every provider uses only IDs from its own layout. */
open class AudioWidgetProvider(
    private val layout: Int,
    private val artwork: Int?,
    private val artworkId: Int?,
    private val titleId: Int?,
    private val artistId: Int?,
    private val actionIds: IntArray,
    private val actionKinds: Array<String>,
    private val progressId: Int? = null,
    private val rootId: Int
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

        if (artwork != null && artworkId != null) {
            views.setImageViewResource(artworkId, artwork)
        }
        titleId?.let { views.setTextViewText(it, "Music Player") }
        artistId?.let { views.setTextViewText(it, "Enjoy Listening") }

        actionIds.forEachIndexed { index, viewId ->
            val kind = actionKinds[index]
            views.setOnClickPendingIntent(viewId, actionPendingIntent(context, id, kind))
        }
        progressId?.let { views.setProgressBar(it, 100, 50, false) }

        views.setOnClickPendingIntent(
            rootId,
            PendingIntent.getActivity(
                context,
                id * 100 + 90,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        manager.updateAppWidget(id, views)
    }

    private fun actionPendingIntent(context: Context, widgetId: Int, kind: String): PendingIntent {
        val actionOffset = when (kind) {
            WidgetActionReceiver.ACTION_PLAY_PAUSE -> 1
            WidgetActionReceiver.ACTION_PREVIOUS -> 2
            WidgetActionReceiver.ACTION_NEXT -> 3
            WidgetActionReceiver.ACTION_SHUFFLE -> 4
            else -> 9
        }
        val intent = Intent(context, WidgetActionReceiver::class.java).setAction(kind)
        return PendingIntent.getBroadcast(
            context,
            widgetId * 100 + actionOffset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
    R.id.classicArtwork,
    R.id.classicTitle,
    R.id.classicArtist,
    intArrayOf(R.id.classicPrev, R.id.classicPlay, R.id.classicNext),
    arrayOf(WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT),
    rootId = R.id.widgetClassicRoot
)

class AudioWidgetLiteProvider : AudioWidgetProvider(
    R.layout.widget_lite,
    null,
    null,
    R.id.liteTitle,
    null,
    intArrayOf(R.id.liteShuffle, R.id.litePrev, R.id.litePlay, R.id.liteNext),
    arrayOf(WidgetActions.SHUFFLE, WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT),
    rootId = R.id.widgetLiteRoot
)

class AudioWidgetSimpleProvider : AudioWidgetProvider(
    R.layout.widget_simple,
    R.drawable.widget_art_ocean,
    R.id.simpleArtwork,
    R.id.simpleTitle,
    R.id.simpleArtist,
    intArrayOf(R.id.simpleShuffle, R.id.simplePrev, R.id.simplePlay, R.id.simpleNext),
    arrayOf(WidgetActions.SHUFFLE, WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT),
    R.id.simpleProgress,
    R.id.widgetSimpleRoot
)

class AudioWidgetMiniProvider : AudioWidgetProvider(
    R.layout.widget_mini,
    R.drawable.widget_art_neon,
    R.id.miniArtwork,
    R.id.miniTitle,
    null,
    intArrayOf(R.id.miniPrev, R.id.miniPlay, R.id.miniNext),
    arrayOf(WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT),
    rootId = R.id.widgetMiniRoot
)

class AudioWidgetPracticalProvider : AudioWidgetProvider(
    R.layout.widget_practical,
    R.drawable.bg_reference_sunset,
    R.id.practicalArtwork,
    R.id.practicalTitle,
    R.id.practicalArtist,
    intArrayOf(R.id.practicalShuffle, R.id.practicalPrev, R.id.practicalPlay, R.id.practicalNext),
    arrayOf(WidgetActions.SHUFFLE, WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT),
    R.id.practicalProgress,
    R.id.widgetPracticalRoot
)

class AudioWidgetFeatureRichProvider : AudioWidgetProvider(
    R.layout.widget_feature_rich,
    R.drawable.widget_art_neon,
    R.id.featureArtwork,
    R.id.featureTitle,
    R.id.featureArtist,
    intArrayOf(R.id.featureShuffle, R.id.featurePrev, R.id.featurePlay, R.id.featureNext),
    arrayOf(WidgetActions.SHUFFLE, WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT),
    R.id.featureProgress,
    R.id.widgetFeatureRoot
)

class AudioWidgetStandardProvider : AudioWidgetProvider(
    R.layout.widget_standard,
    R.drawable.widget_art_ocean,
    R.id.standardArtwork,
    R.id.standardTitle,
    R.id.standardArtist,
    intArrayOf(R.id.standardPrev, R.id.standardPlay, R.id.standardNext),
    arrayOf(WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT),
    R.id.standardProgress,
    R.id.widgetStandardRoot
)

class AudioWidgetStylishProvider : AudioWidgetProvider(
    R.layout.widget_stylish,
    R.drawable.bg_reference_sunset,
    R.id.stylishArtwork,
    R.id.stylishTitle,
    R.id.stylishArtist,
    intArrayOf(R.id.stylishPrev, R.id.stylishPlay, R.id.stylishNext),
    arrayOf(WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT),
    rootId = R.id.widgetStylishRoot
)
