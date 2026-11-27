package com.example.foodtracker.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.foodtracker.FoodTrackerApp

/**
 * Fires periodically when the user has enabled recurring backups (see
 * [BackupScheduler]). Calls [BackupManager.saveLocalBackup] once per run.
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? FoodTrackerApp ?: return Result.success()
        val outcome = app.backupManager.saveLocalBackup()
        return if (outcome.isSuccess) Result.success() else Result.retry()
    }
}
