package com.surafel.audio

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.media3.common.Player

/** Base provider for the eight independent launcher widgets. */
open class AudioWidgetProvider(
    private val layout: Int,
    private val fallbackArtwork: Int?,
    private val artworkId: Int?,
    private val titleId: Int?,
    private val artistId: Int?,
    private val actionIds: IntArray,
    private val actionKinds: Array<String>,
    private val playButtonId: Int?,
    private val progressId: Int? = null,
    private val rootId: Int
) : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            AudioWidgetRenderer.render(
                context = context,
                manager = manager,
                appWidgetId = id,
                layout = layout,
                fallbackArtwork = fallbackArtwork,
                artworkId = artworkId,
                titleId = titleId,
                artistId = artistId,
                actionIds = actionIds,
                actionKinds = actionKinds,
                playButtonId = playButtonId,
                progressId = progressId,
                rootId = rootId
            )
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) = onUpdate(context, manager, intArrayOf(appWidgetId))
}

private object WidgetActions {
    const val PLAY_PAUSE = WidgetActionReceiver.ACTION_PLAY_PAUSE
    const val PREVIOUS = WidgetActionReceiver.ACTION_PREVIOUS
    const val NEXT = WidgetActionReceiver.ACTION_NEXT
    const val SHUFFLE = WidgetActionReceiver.ACTION_SHUFFLE
}

private data class WidgetSpec(
    val provider: Class<out AppWidgetProvider>,
    val layout: Int,
    val fallbackArtwork: Int?,
    val artworkId: Int?,
    val titleId: Int?,
    val artistId: Int?,
    val actionIds: IntArray,
    val actionKinds: Array<String>,
    val playButtonId: Int?,
    val progressId: Int?,
    val rootId: Int
)

/** Single renderer used by launcher callbacks and live Media3 updates. */
object AudioWidgetRenderer {
    private val specs by lazy {
        listOf(
            WidgetSpec(AudioWidgetClassicProvider::class.java, R.layout.widget_classic, R.drawable.widget_art_sunset, R.id.classicArtwork, R.id.classicTitle, R.id.classicArtist, intArrayOf(R.id.classicPrev, R.id.classicPlay, R.id.classicNext), arrayOf(WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT), R.id.classicPlay, null, R.id.widgetClassicRoot),
            WidgetSpec(AudioWidgetLiteProvider::class.java, R.layout.widget_lite, null, null, R.id.liteTitle, null, intArrayOf(R.id.liteShuffle, R.id.litePrev, R.id.litePlay, R.id.liteNext), arrayOf(WidgetActions.SHUFFLE, WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT), R.id.litePlay, null, R.id.widgetLiteRoot),
            WidgetSpec(AudioWidgetSimpleProvider::class.java, R.layout.widget_simple, R.drawable.widget_art_ocean, R.id.simpleArtwork, R.id.simpleTitle, R.id.simpleArtist, intArrayOf(R.id.simpleShuffle, R.id.simplePrev, R.id.simplePlay, R.id.simpleNext), arrayOf(WidgetActions.SHUFFLE, WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT), R.id.simplePlay, R.id.simpleProgress, R.id.widgetSimpleRoot),
            WidgetSpec(AudioWidgetMiniProvider::class.java, R.layout.widget_mini, R.drawable.widget_art_neon, R.id.miniArtwork, R.id.miniTitle, null, intArrayOf(R.id.miniPrev, R.id.miniPlay, R.id.miniNext), arrayOf(WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT), R.id.miniPlay, null, R.id.widgetMiniRoot),
            WidgetSpec(AudioWidgetPracticalProvider::class.java, R.layout.widget_practical, R.drawable.bg_reference_sunset, R.id.practicalArtwork, R.id.practicalTitle, R.id.practicalArtist, intArrayOf(R.id.practicalShuffle, R.id.practicalPrev, R.id.practicalPlay, R.id.practicalNext), arrayOf(WidgetActions.SHUFFLE, WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT), R.id.practicalPlay, R.id.practicalProgress, R.id.widgetPracticalRoot),
            WidgetSpec(AudioWidgetFeatureRichProvider::class.java, R.layout.widget_feature_rich, R.drawable.widget_art_neon, R.id.featureArtwork, R.id.featureTitle, R.id.featureArtist, intArrayOf(R.id.featureShuffle, R.id.featurePrev, R.id.featurePlay, R.id.featureNext), arrayOf(WidgetActions.SHUFFLE, WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT), R.id.featurePlay, R.id.featureProgress, R.id.widgetFeatureRoot),
            WidgetSpec(AudioWidgetStandardProvider::class.java, R.layout.widget_standard, R.drawable.widget_art_ocean, R.id.standardArtwork, R.id.standardTitle, R.id.standardArtist, intArrayOf(R.id.standardPrev, R.id.standardPlay, R.id.standardNext), arrayOf(WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT), R.id.standardPlay, R.id.standardProgress, R.id.widgetStandardRoot),
            WidgetSpec(AudioWidgetStylishProvider::class.java, R.layout.widget_stylish, R.drawable.bg_reference_sunset, R.id.stylishArtwork, R.id.stylishTitle, R.id.stylishArtist, intArrayOf(R.id.stylishPrev, R.id.stylishPlay, R.id.stylishNext), arrayOf(WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT), R.id.stylishPlay, null, R.id.widgetStylishRoot)
        )
    }

    fun updateAll(context: Context, player: Player, artwork: Bitmap? = null) {
        val manager = AppWidgetManager.getInstance(context)
        val title = player.currentMediaItem?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() } ?: "Nothing playing"
        val artist = player.currentMediaItem?.mediaMetadata?.artist?.toString()?.takeIf { it.isNotBlank() } ?: "Choose a song"
        val position = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration
        specs.forEach { spec ->
            val ids = manager.getAppWidgetIds(ComponentName(context, spec.provider))
            ids.forEach { id ->
                render(context, manager, id, spec.layout, spec.fallbackArtwork, spec.artworkId, spec.titleId, spec.artistId, spec.actionIds, spec.actionKinds, spec.playButtonId, spec.progressId, spec.rootId, player.isPlaying, title, artist, artwork, position, duration)
            }
        }
    }

    fun render(context: Context, manager: AppWidgetManager, appWidgetId: Int, layout: Int, fallbackArtwork: Int?, artworkId: Int?, titleId: Int?, artistId: Int?, actionIds: IntArray, actionKinds: Array<String>, playButtonId: Int?, progressId: Int?, rootId: Int, isPlaying: Boolean = false, title: String = "Music Player", artist: String = "Enjoy Listening", artwork: Bitmap? = null, position: Long = 0L, duration: Long = -1L) {
        val views = RemoteViews(context.packageName, layout)
        if (artworkId != null) {
            if (artwork != null) views.setImageViewBitmap(artworkId, artwork) else if (fallbackArtwork != null) views.setImageViewResource(artworkId, fallbackArtwork)
        }
        titleId?.let { views.setTextViewText(it, title) }
        artistId?.let { views.setTextViewText(it, artist) }
        playButtonId?.let { views.setTextViewText(it, if (isPlaying) "Ⅱ" else "▶") }
        actionIds.forEachIndexed { index, viewId -> views.setOnClickPendingIntent(viewId, actionPendingIntent(context, appWidgetId, actionKinds[index])) }
        progressId?.let { views.setProgressBar(it, 100, if (duration > 0L) ((position * 100L) / duration).toInt().coerceIn(0, 100) else 0, false) }
        views.setOnClickPendingIntent(rootId, PendingIntent.getActivity(context, appWidgetId * 100 + 90, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        manager.updateAppWidget(appWidgetId, views)
    }

    private fun actionPendingIntent(context: Context, widgetId: Int, kind: String): PendingIntent {
        val offset = when (kind) { WidgetActions.PLAY_PAUSE -> 1; WidgetActions.PREVIOUS -> 2; WidgetActions.NEXT -> 3; WidgetActions.SHUFFLE -> 4; else -> 9 }
        return PendingIntent.getBroadcast(context, widgetId * 100 + offset, Intent(context, WidgetActionReceiver::class.java).setAction(kind), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

class AudioWidgetClassicProvider : AudioWidgetProvider(R.layout.widget_classic, R.drawable.widget_art_sunset, R.id.classicArtwork, R.id.classicTitle, R.id.classicArtist, intArrayOf(R.id.classicPrev, R.id.classicPlay, R.id.classicNext), arrayOf(WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT), R.id.classicPlay, null, R.id.widgetClassicRoot)
class AudioWidgetLiteProvider : AudioWidgetProvider(R.layout.widget_lite, null, null, R.id.liteTitle, null, intArrayOf(R.id.liteShuffle, R.id.litePrev, R.id.litePlay, R.id.liteNext), arrayOf(WidgetActions.SHUFFLE, WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT), R.id.litePlay, null, R.id.widgetLiteRoot)
class AudioWidgetSimpleProvider : AudioWidgetProvider(R.layout.widget_simple, R.drawable.widget_art_ocean, R.id.simpleArtwork, R.id.simpleTitle, R.id.simpleArtist, intArrayOf(R.id.simpleShuffle, R.id.simplePrev, R.id.simplePlay, R.id.simpleNext), arrayOf(WidgetActions.SHUFFLE, WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT), R.id.simplePlay, R.id.simpleProgress, R.id.widgetSimpleRoot)
class AudioWidgetMiniProvider : AudioWidgetProvider(R.layout.widget_mini, R.drawable.widget_art_neon, R.id.miniArtwork, R.id.miniTitle, null, intArrayOf(R.id.miniPrev, R.id.miniPlay, R.id.miniNext), arrayOf(WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT), R.id.miniPlay, null, R.id.widgetMiniRoot)
class AudioWidgetPracticalProvider : AudioWidgetProvider(R.layout.widget_practical, R.drawable.bg_reference_sunset, R.id.practicalArtwork, R.id.practicalTitle, R.id.practicalArtist, intArrayOf(R.id.practicalShuffle, R.id.practicalPrev, R.id.practicalPlay, R.id.practicalNext), arrayOf(WidgetActions.SHUFFLE, WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT), R.id.practicalPlay, R.id.practicalProgress, R.id.widgetPracticalRoot)
class AudioWidgetFeatureRichProvider : AudioWidgetProvider(R.layout.widget_feature_rich, R.drawable.widget_art_neon, R.id.featureArtwork, R.id.featureTitle, R.id.featureArtist, intArrayOf(R.id.featureShuffle, R.id.featurePrev, R.id.featurePlay, R.id.featureNext), arrayOf(WidgetActions.SHUFFLE, WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT), R.id.featurePlay, R.id.featureProgress, R.id.widgetFeatureRoot)
class AudioWidgetStandardProvider : AudioWidgetProvider(R.layout.widget_standard, R.drawable.widget_art_ocean, R.id.standardArtwork, R.id.standardTitle, R.id.standardArtist, intArrayOf(R.id.standardPrev, R.id.standardPlay, R.id.standardNext), arrayOf(WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT), R.id.standardPlay, R.id.standardProgress, R.id.widgetStandardRoot)
class AudioWidgetStylishProvider : AudioWidgetProvider(R.layout.widget_stylish, R.drawable.bg_reference_sunset, R.id.stylishArtwork, R.id.stylishTitle, R.id.stylishArtist, intArrayOf(R.id.stylishPrev, R.id.stylishPlay, R.id.stylishNext), arrayOf(WidgetActions.PREVIOUS, WidgetActions.PLAY_PAUSE, WidgetActions.NEXT), R.id.stylishPlay, null, R.id.widgetStylishRoot)
