package com.example.foodtracker.data

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

/**
 * The AI settings live in their own store, apart from everything else in
 * [dataStore].
 *
 * `user_prefs.preferences_pb` is listed in both `backup_rules.xml` and
 * `data_extraction_rules.xml`, so anything kept there is uploaded to Google's
 * cloud backup and copied during device-to-device transfer. The API key is the
 * user's own paid credential and has no business leaving the device, so
 * `ai_prefs.preferences_pb` is deliberately absent from both files — do not add
 * it. [AiPrefsMigration] carries the settings over for installs that predate
 * the split and scrubs the originals out of `user_prefs`.
 */
private val Context.aiDataStore by preferencesDataStore(
    name = "ai_prefs",
    produceMigrations = { context -> listOf(AiPrefsMigration(context.dataStore)) },
)

private object Keys {
    val ONBOARDED = booleanPreferencesKey("onboarded")
    val NAME = stringPreferencesKey("name")
    val AGE = intPreferencesKey("age")
    val SEX = stringPreferencesKey("sex")
    val HEIGHT = intPreferencesKey("height_cm")
    val WEIGHT = doublePreferencesKey("weight_kg")
    val ACTIVITY = stringPreferencesKey("activity_level")
    val GOAL = stringPreferencesKey("goal")
    val WEIGHT_CHANGE_RATE = stringPreferencesKey("weight_change_rate")
    val GOAL_WEIGHT = doublePreferencesKey("goal_weight_kg")
    val TRAINING_MIX = doublePreferencesKey("training_mix")
    val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val APPEARANCE = stringPreferencesKey("appearance")
    val COLOR_PALETTE = stringPreferencesKey("color_palette")
    val SEED_VERSION = intPreferencesKey("seed_version")
    // Backups
    val BACKUP_FOLDER_URI = stringPreferencesKey("backup_folder_uri")
    val BACKUP_INTERVAL_HOURS = intPreferencesKey("backup_interval_hours")
    // AI nutrition autofill (opt-in, user-supplied key) — stored in [aiDataStore]
    val AI_ENABLED = booleanPreferencesKey("ai_enabled")
    val AI_PROVIDER = stringPreferencesKey("ai_provider")
    val AI_API_KEY = stringPreferencesKey("ai_api_key")
    val AI_MODEL = stringPreferencesKey("ai_model")
}

private val AI_KEYS: List<Preferences.Key<*>> =
    listOf(Keys.AI_ENABLED, Keys.AI_PROVIDER, Keys.AI_API_KEY, Keys.AI_MODEL)

/**
 * Moves the AI settings from `user_prefs` into `ai_prefs` on first access, then
 * deletes the originals so the API key stops being swept into cloud backup.
 * Runs at most once per install — afterwards the keys are gone from the source.
 */
private class AiPrefsMigration(
    private val userPrefs: DataStore<Preferences>,
) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        AI_KEYS.none { it in currentData } && AI_KEYS.any { it in userPrefs.data.first() }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val old = userPrefs.data.first()
        return currentData.toMutablePreferences().apply {
            old[Keys.AI_ENABLED]?.let { this[Keys.AI_ENABLED] = it }
            old[Keys.AI_PROVIDER]?.let { this[Keys.AI_PROVIDER] = it }
            old[Keys.AI_API_KEY]?.let { this[Keys.AI_API_KEY] = it }
            old[Keys.AI_MODEL]?.let { this[Keys.AI_MODEL] = it }
        }.toPreferences()
    }

    override suspend fun cleanUp() {
        userPrefs.edit { prefs -> AI_KEYS.forEach { prefs.remove(it) } }
    }
}

class ProfileStore(
    private val context: Context,
    /**
     * Called after [save] changes the profile, which changes the widgets'
     * goal numbers ([com.example.foodtracker.domain.GoalCalculator]). Wired in
     * [com.example.foodtracker.FoodTrackerApp] to push a widget refresh. Plain
     * lambda so this data class stays decoupled from the widget/UI layer.
     */
    private val onProfileChanged: suspend () -> Unit = {},
) {

    val profile: Flow<UserProfile> = context.dataStore.data.map { p ->
        UserProfile(
            name = p[Keys.NAME] ?: "",
            ageYears = p[Keys.AGE] ?: 25,
            sex = Sex.fromName(p[Keys.SEX] ?: Sex.Male.name),
            heightCm = p[Keys.HEIGHT] ?: 170,
            weightKg = p[Keys.WEIGHT] ?: 65.0,
            activityLevel = ActivityLevel.fromName(p[Keys.ACTIVITY] ?: ActivityLevel.Moderate.name),
            goal = Goal.fromName(p[Keys.GOAL] ?: Goal.Maintain.name),
            weightChangeRate = WeightChangeRate.fromName(p[Keys.WEIGHT_CHANGE_RATE]),
            goalWeightKg = p[Keys.GOAL_WEIGHT],
            trainingMix = p[Keys.TRAINING_MIX],
        )
    }

    val isOnboarded: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDED] == true }

    /** Default true — opt-out, not opt-in, since haptics enhance the core taps. */
    val hapticsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.HAPTICS_ENABLED] ?: true }

    /**
     * Light / Dark / Match system. A new key rather than the old THEME_MODE,
     * so a value left over from a much older build cannot override the
     * light default; the two dead keys are removed on the first write.
     */
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { ThemeMode.fromName(it[Keys.APPEARANCE]) }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { p ->
            p[Keys.APPEARANCE] = mode.name
            p.remove(Keys.THEME_MODE)
            p.remove(Keys.COLOR_PALETTE)
        }
    }

    /** Persisted SAF tree URI for backups, or null when no custom folder is set. */
    val backupFolderUri: Flow<String?> = context.dataStore.data.map { it[Keys.BACKUP_FOLDER_URI] }

    /** Recurring backup cadence in hours. 0 = off. */
    val backupIntervalHours: Flow<Int> = context.dataStore.data.map { it[Keys.BACKUP_INTERVAL_HOURS] ?: 0 }

    /** AI nutrition autofill. Default false — privacy-first, opt-in only. */
    val aiEnabled: Flow<Boolean> = context.aiDataStore.data.map { it[Keys.AI_ENABLED] ?: false }

    val aiProvider: Flow<AiProvider> =
        context.aiDataStore.data.map { AiProvider.fromName(it[Keys.AI_PROVIDER]) }

    /** User's own API key for the selected provider, or null when unset. */
    val aiApiKey: Flow<String?> = context.aiDataStore.data.map { it[Keys.AI_API_KEY] }

    /**
     * Stored model id. Resolved against the current provider's list at read time
     * (see [AiProvider.modelFromId]), so switching providers falls back to that
     * provider's cheapest default automatically.
     */
    val aiModelId: Flow<String?> = context.aiDataStore.data.map { it[Keys.AI_MODEL] }

    suspend fun setBackupFolderUri(uri: String?) {
        context.dataStore.edit { p ->
            if (uri == null) p.remove(Keys.BACKUP_FOLDER_URI) else p[Keys.BACKUP_FOLDER_URI] = uri
        }
    }

    suspend fun setBackupIntervalHours(hours: Int) {
        context.dataStore.edit { p -> p[Keys.BACKUP_INTERVAL_HOURS] = hours }
    }

    suspend fun getSeedVersion(): Int = context.dataStore.data.map { it[Keys.SEED_VERSION] ?: 0 }.first()

    suspend fun setSeedVersion(version: Int) {
        context.dataStore.edit { p -> p[Keys.SEED_VERSION] = version }
    }

    suspend fun save(profile: UserProfile, markOnboarded: Boolean = true) {
        context.dataStore.edit { p ->
            p[Keys.NAME] = profile.name
            p[Keys.AGE] = profile.ageYears
            p[Keys.SEX] = profile.sex.name
            p[Keys.HEIGHT] = profile.heightCm
            p[Keys.WEIGHT] = profile.weightKg
            p[Keys.ACTIVITY] = profile.activityLevel.name
            p[Keys.GOAL] = profile.goal.name
            p[Keys.WEIGHT_CHANGE_RATE] = profile.weightChangeRate.name
            val goalWeight = profile.goalWeightKg
            if (goalWeight == null) p.remove(Keys.GOAL_WEIGHT) else p[Keys.GOAL_WEIGHT] = goalWeight
            val mix = profile.trainingMix
            if (mix == null) p.remove(Keys.TRAINING_MIX) else p[Keys.TRAINING_MIX] = mix
            if (markOnboarded) p[Keys.ONBOARDED] = true
        }
        // Goals are derived from the profile, so a save changes what the
        // widgets show — refresh them now rather than waiting for the next
        // log write or app foreground.
        onProfileChanged()
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { p -> p[Keys.HAPTICS_ENABLED] = enabled }
    }

    suspend fun setAiEnabled(enabled: Boolean) {
        context.aiDataStore.edit { p -> p[Keys.AI_ENABLED] = enabled }
    }

    suspend fun setAiProvider(provider: AiProvider) {
        context.aiDataStore.edit { p -> p[Keys.AI_PROVIDER] = provider.name }
    }

    suspend fun setAiApiKey(key: String?) {
        context.aiDataStore.edit { p ->
            if (key.isNullOrBlank()) p.remove(Keys.AI_API_KEY) else p[Keys.AI_API_KEY] = key.trim()
        }
    }

    suspend fun setAiModel(modelId: String) {
        context.aiDataStore.edit { p -> p[Keys.AI_MODEL] = modelId }
    }
}
