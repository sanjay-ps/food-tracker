package com.example.foodtracker

import android.app.Application
import com.example.foodtracker.data.AiNutritionApi
import com.example.foodtracker.data.FoodRepository
import com.example.foodtracker.data.OpenFoodFactsApi
import com.example.foodtracker.data.ProfileStore
import com.example.foodtracker.data.UsdaFoodApi
import com.example.foodtracker.data.backup.BackupManager
import com.example.foodtracker.data.backup.BackupScheduler
import com.example.foodtracker.data.db.AppDatabase
import com.example.foodtracker.data.db.IndianFoodSeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class FoodTrackerApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val api: OpenFoodFactsApi by lazy { OpenFoodFactsApi() }
    val usdaApi: UsdaFoodApi by lazy { UsdaFoodApi() }
    val aiApi: AiNutritionApi by lazy { AiNutritionApi() }
    val repository: FoodRepository by lazy {
        FoodRepository(
            foodDao = database.foodDao(),
            foodLogDao = database.foodLogDao(),
            waterLogDao = database.waterLogDao(),
            weightLogDao = database.weightLogDao(),
            planDao = database.planDao(),
            api = api,
            usdaApi = usdaApi,
            aiApi = aiApi,
            // Refresh the home-screen widgets after any log/activity write so
            // they don't drift from the in-app diary (a fresh provideGlance also
            // recomputes "today"). See WidgetData for why this push is needed.
            onWidgetDataChanged = {
                com.example.foodtracker.widget.WidgetUpdater.updateAll(this)
            },
        )
    }
    val profileStore: ProfileStore by lazy {
        ProfileStore(
            context = this,
            // Goal edits change the widgets' target numbers — refresh immediately.
            onProfileChanged = {
                com.example.foodtracker.widget.WidgetUpdater.updateAll(this)
            },
        )
    }
    val reminderStore: com.example.foodtracker.reminder.ReminderStore by lazy {
        com.example.foodtracker.reminder.ReminderStore(this)
    }
    val reminderScheduler: com.example.foodtracker.reminder.ReminderScheduler by lazy {
        com.example.foodtracker.reminder.ReminderScheduler(this)
    }
    val backupManager: BackupManager by lazy { BackupManager(this, database, profileStore) }
    val backupScheduler: BackupScheduler by lazy { BackupScheduler(this) }

    /** Guards [initializeForUi] so repeated Activity launches don't re-run it. */
    private val uiInitStarted = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        // Only work that every entry point needs belongs here — Application
        // .onCreate also runs when the process is spun up purely to deliver a
        // reminder broadcast or run a widget worker. Both of these are cheap
        // and idempotent: ensureChannel returns early once the channel exists,
        // and the worker is enqueued with a KEEP policy.
        com.example.foodtracker.reminder.ReminderScheduler.ensureChannel(this)
        // Daily post-midnight widget refresh so the date rollover lands even
        // when the app is never opened (active-use refreshes go via WidgetUpdater).
        com.example.foodtracker.widget.WidgetRefreshWorker.schedule(this)
    }

    /**
     * Startup work that only matters when there is a UI: seeding the food
     * table, re-arming reminders, and re-establishing the backup job. Called
     * once from [MainActivity].
     *
     * Deliberately out of [onCreate], which also fires on every broadcast- or
     * worker-driven process start — re-seeding and cancelling/re-creating six
     * alarms on each of those wakeups is pure battery and IO cost, and it
     * raced the receiver's own re-arm. Nothing here is load-bearing for those
     * paths: ReminderReceiver re-arms its alarm after each fire and
     * BOOT_COMPLETED reschedules them all, and WorkManager persists the backup
     * job across reboots on its own.
     */
    fun initializeForUi() {
        if (!uiInitStarted.compareAndSet(false, true)) return
        appScope.launch {
            // The hand-tuned seed plus the extended catalogue in assets; also
            // primes the catalogue's in-memory ranks and "how it's made" lines.
            val seed = IndianFoodSeed.ALL + com.example.foodtracker.data.FoodCatalogue.load(this@FoodTrackerApp).foods
            val freshInstall = repository.seedIfEmpty(seed)
            val storedSeedVersion = profileStore.getSeedVersion()
            val refreshed = if (!freshInstall) repository.refreshSeedIfStale(storedSeedVersion, seed) else false
            if (freshInstall || refreshed) {
                profileStore.setSeedVersion(IndianFoodSeed.SEED_VERSION)
            }
            // Re-arm any persisted reminders on cold start so they survive reboots
            // (paired with BOOT_COMPLETED receiver in the manifest).
            reminderScheduler.rescheduleAll(reminderStore.snapshot())
            // Re-establish the periodic backup job using the user's saved cadence.
            backupScheduler.apply(profileStore.backupIntervalHours.first())
        }
    }
}
