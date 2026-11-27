package com.example.foodtracker.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Refreshes the widgets once per day, right after midnight, so the date
 * rollover lands even when the user never opens the app. Active-use refreshes
 * (logging food, opening the app) are pushed separately via [WidgetUpdater];
 * this worker exists purely to cover the "widget sits idle across midnight"
 * gap — without it the widget would keep showing yesterday until the next
 * write or app launch.
 */
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        WidgetUpdater.updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "widget-daily-refresh"

        /**
         * Schedule the daily refresh, first firing shortly after the next local
         * midnight. KEEP policy so we don't reset the schedule on every app
         * start (which would push the first run a full day out each time).
         */
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            // A few minutes past midnight — gives the clock time to actually
            // cross the date boundary before we read todayEpochDay().
            val nextRun = LocalDate.now().plusDays(1).atTime(LocalTime.of(0, 5))
            val initialDelayMin = java.time.Duration.between(now, nextRun).toMinutes()
                .coerceAtLeast(1)

            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
                1, TimeUnit.DAYS,
            )
                .setInitialDelay(initialDelayMin, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
