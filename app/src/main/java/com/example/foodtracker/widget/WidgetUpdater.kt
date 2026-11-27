package com.example.foodtracker.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single entry point for refreshing every home-screen widget.
 *
 * We deliberately do NOT use the `GlanceAppWidget.updateAll(context)` extension.
 * That helper resolves "which appWidgetIds belong to this widget" through
 * Glance's own internal provider→id mapping (a DataStore rebuilt from disk on a
 * cold process). After the app is killed (swiped from recents) and then
 * cold-started by tapping a widget, that mapping could resolve the wrong ids, so
 * `GoalLeftWidget().updateAll()` would push the calories layout onto the Macros
 * widget's id — the "macros becomes the calorie widget" bug, which only ever
 * reproduced on a fresh process.
 *
 * Instead we pair each [GlanceAppWidget] with its OWN receiver's appWidgetIds,
 * read from the platform [AppWidgetManager] keyed by [ComponentName]. That
 * mapping is authoritative and cannot cross-wire two providers, so a widget's
 * content can only ever land on its own instances.
 *
 * Invoked from: food-log / activity writes ([com.example.foodtracker.data.FoodRepository]),
 * app foreground ([com.example.foodtracker.MainActivity]), and the daily
 * date-rollover worker ([WidgetRefreshWorker]).
 */
object WidgetUpdater {
    // Serializes the fan-out so two providers never translate/apply RemoteViews
    // at the same time. Rapid triggers (every app foreground + every food write
    // + tapping a widget, which itself foregrounds the app) can otherwise start
    // overlapping update passes. Cheap insurance — updates are fast and
    // infrequent, so contention is negligible and there's no battery cost.
    private val mutex = Mutex()

    suspend fun updateAll(context: Context) {
        val app = context.applicationContext
        mutex.withLock {
            updateProvider(app, GoalLeftWidgetReceiver::class.java, GoalLeftWidget())
            updateProvider(app, MacrosWidgetReceiver::class.java, MacrosWidget())
        }
    }

    /**
     * Updates [widget] on exactly the appWidgetIds the platform says are bound to
     * [receiver] — no Glance internal id mapping involved, so cross-wiring between
     * providers is impossible.
     */
    private suspend fun updateProvider(
        context: Context,
        receiver: Class<*>,
        widget: GlanceAppWidget,
    ) {
        val ids = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, receiver))
        if (ids.isEmpty()) return
        val manager = GlanceAppWidgetManager(context)
        for (appWidgetId in ids) {
            try {
                widget.update(context, manager.getGlanceIdBy(appWidgetId))
            } catch (e: IllegalArgumentException) {
                // getGlanceIdBy throws if the id isn't (yet) a Glance-managed
                // widget — skip it rather than abort the whole fan-out.
                Log.w("WidgetUpdater", "skipping appWidgetId=$appWidgetId for ${receiver.simpleName}", e)
            }
        }
    }
}
