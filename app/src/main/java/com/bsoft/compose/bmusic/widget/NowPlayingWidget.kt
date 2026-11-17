package com.bsoft.compose.bmusic.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.core.content.edit
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.activities.MainActivity
import com.bsoft.compose.bmusic.services.PlaybackService

/** What the widget shows. */
data class WidgetContent(
    val title: String? = null,
    val artist: String? = null,
    val playing: Boolean = false,
    val art: Bitmap? = null,
)

/**
 * The "Now playing" home-screen widget. The playback service pushes every change with
 * [NowPlayingWidget.update]; the system only asks for a redraw when a widget is added or the
 * launcher restarts, and then the last song is shown from a small saved copy (without art).
 */
class NowPlayingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetManager.updateAppWidget(appWidgetIds, render(context, lastContent(context)))
    }

    companion object {
        private const val PREFS = "now_playing_widget"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"

        // Request codes keep the button PendingIntents distinct (extras don't count).
        private const val REQUEST_OPEN = 0
        private const val REQUEST_PREVIOUS = 1
        private const val REQUEST_PLAY = 2
        private const val REQUEST_PAUSE = 3
        private const val REQUEST_NEXT = 4

        /** Redraws every BMusic widget. Cheap no-op when none is placed. */
        fun update(context: Context, content: WidgetContent) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putString(KEY_TITLE, content.title)
                putString(KEY_ARTIST, content.artist)
            }
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, render(context, content))
        }

        private fun lastContent(context: Context): WidgetContent {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return WidgetContent(title = prefs.getString(KEY_TITLE, null), artist = prefs.getString(KEY_ARTIST, null))
        }

        private fun render(context: Context, content: WidgetContent): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_now_playing).apply {
                setTextViewText(R.id.widget_title, content.title ?: context.getString(R.string.app_name))
                setTextViewText(R.id.widget_artist, content.artist ?: context.getString(R.string.widget_idle))

                if (content.art != null) {
                    setImageViewBitmap(R.id.widget_art, content.art)
                    setViewPadding(R.id.widget_art, 0, 0, 0, 0)
                } else {
                    setImageViewResource(R.id.widget_art, R.drawable.ic_widget_music)
                    val padding = context.resources.getDimensionPixelSize(R.dimen.widget_art_placeholder_padding)
                    setViewPadding(R.id.widget_art, padding, padding, padding, padding)
                }

                setImageViewResource(R.id.widget_play_pause, if (content.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
                setContentDescription(
                    R.id.widget_play_pause,
                    context.getString(if (content.playing) R.string.cd_pause else R.string.cd_play)
                )

                val open = PendingIntent.getActivity(
                    context, REQUEST_OPEN,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                setOnClickPendingIntent(R.id.widget_root, open)
                setOnClickPendingIntent(R.id.widget_previous, mediaButton(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS, REQUEST_PREVIOUS))
                setOnClickPendingIntent(R.id.widget_next, mediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT, REQUEST_NEXT))
                setOnClickPendingIntent(
                    R.id.widget_play_pause,
                    // Explicit play / pause (not play-pause), so Media3's double-tap detection never delays it.
                    if (content.playing) mediaButton(context, KeyEvent.KEYCODE_MEDIA_PAUSE, REQUEST_PAUSE)
                    else mediaButton(context, KeyEvent.KEYCODE_MEDIA_PLAY, REQUEST_PLAY)
                )
            }

        /**
         * A media key sent straight to the playback service, the same way headset buttons
         * reach it. If the app was closed, the service starts and resumes the saved queue.
         */
        private fun mediaButton(context: Context, keyCode: Int, requestCode: Int): PendingIntent {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
                .setClass(context, PlaybackService::class.java)
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            return PendingIntent.getForegroundService(
                context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
