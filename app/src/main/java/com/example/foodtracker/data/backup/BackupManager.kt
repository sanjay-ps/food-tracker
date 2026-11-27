package com.example.foodtracker.data.backup

import kotlinx.serialization.json.decodeFromStream
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.example.foodtracker.BuildConfig
import com.example.foodtracker.data.AllNutrientFields
import com.example.foodtracker.data.AllNutrientSubFields
import com.example.foodtracker.data.Nutrients
import com.example.foodtracker.data.ProfileStore
import com.example.foodtracker.data.db.AppDatabase
import com.example.foodtracker.domain.GoalCalculator
import com.example.foodtracker.util.DateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reads / writes [BackupPayload]s in two flavours — both produce a plain
 * JSON file in a location any other app on the phone can see:
 *
 *  - **Export / Import** — user-picked [Uri] via SAF
 *    (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`). Useful for handing
 *    a snapshot to a charting app, a cloud-sync drop folder, etc.
 *
 *  - **In-app backup** — automatic write to `Downloads/FoodTracker/`
 *    via MediaStore. Discoverable by every file manager, survives uninstall
 *    (Downloads is user storage), restorable from the in-app list.
 */
private const val BACKUP_PREFIX = "foodtracker-backup-"

class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val profileStore: ProfileStore,
) {

    private val json = Json {
        // Compact: ~30% smaller per snapshot, and five are kept. Older
        // pretty-printed files still parse.
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Compact serializer for the AI-analysis export: no pretty-printing and
     * `encodeDefaults = false` so `null` brands, `custom = false`, and other
     * defaults drop out — keeps the file small (its whole point).
     */
    private val analysisJson = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val backupCollection: Uri
        get() = MediaStore.Downloads.EXTERNAL_CONTENT_URI

    /** `Downloads/FoodTracker/` — where local backups land. */
    private val relativeDir = "${Environment.DIRECTORY_DOWNLOADS}/FoodTracker/"

    companion object {
        /** Most recent N backups to retain; older snapshots are deleted automatically. */
        const val MAX_LOCAL_BACKUPS = 5
    }

    // ─── Snapshot construction ────────────────────────────────────────────

    suspend fun buildSnapshot(): BackupPayload = withContext(Dispatchers.IO) {
        val profile = profileStore.profile.first()
        val haptics = profileStore.hapticsEnabled.first()
        val foodDao = database.foodDao()
        val foodLogDao = database.foodLogDao()

        // Build the food log first so we know which foods to include. We attach
        // each log's source food (when still resolvable) so the snapshot carries
        // the portable `foodClientUuid` + `foodName` for cross-install lookup.
        val foodsById = foodDao.getAll().associateBy { it.id }
        val foodLogs = foodLogDao.getAll()
        val foodLogSnaps = foodLogs.map { FoodLogSnapshot.from(it, foodsById[it.foodId]) }
        val planItems = database.planDao().getAll()
        val planSnaps = planItems.map { PlanItemSnapshot.from(it, foodsById[it.foodId]) }

        // Custom foods to include = only those referenced by at least one log
        // entry or planned line. Seeded foods are reproduced from
        // `IndianFoodSeed` on the target install, so we don't ship them inside
        // the backup payload.
        //
        // The plan half of that union is load-bearing: a custom food that is
        // planned but has never actually been eaten appears in no log, so
        // without it the payload would carry a plan line pointing at a food it
        // did not ship, and the restore would silently drop that line.
        val referencedFoodIds = buildSet {
            foodLogs.mapTo(this) { it.foodId }
            planItems.mapTo(this) { it.foodId }
        }
        val customs = foodDao.getAllCustom().filter { it.id in referencedFoodIds }

        BackupPayload(
            createdAtMillis = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            profile = ProfileSnapshot.from(profile, haptics),
            customFoods = customs.map(FoodSnapshot::from),
            foodLog = foodLogSnaps,
            waterLog = database.waterLogDao().getAll().map(WaterLogSnapshot::from),
            weightLog = database.weightLogDao().getAll().map(WeightLogSnapshot::from),
            plan = planSnaps,
        )
    }

    // ─── AI-analysis log export ───────────────────────────────────────────

    /**
     * Build a compact, self-describing snapshot of the user's food logs over the
     * last [range] days — foods de-duplicated into a reference map, zero
     * nutrients dropped, numbers rounded — for handing to an external AI. See
     * [AnalysisExport]. Read-only; never written back to the DB.
     */
    suspend fun buildAnalysisExport(range: AnalysisRange): AnalysisExport = withContext(Dispatchers.IO) {
        val today = DateUtil.todayEpochDay()
        val start = today - range.days + 1
        val end = today

        val profile = profileStore.profile.first()
        val goals = GoalCalculator.goalsFor(profile)
        val rows = database.foodLogDao().getForRange(start, end)

        // Distinct foods referenced in-range, keyed by local id (as a string).
        val foods = rows.associate { it.food.id.toString() to it.food }
            .mapValues { (_, f) ->
                AnalysisFood(
                    name = f.name,
                    category = f.category,
                    brand = f.brand,
                    custom = f.isCustom,
                    per100g = compactNutrients(f.nutrientsPer100g()),
                )
            }

        val logs = rows.map { row ->
            AnalysisLog(
                d = DateUtil.epochDayToDate(row.log.dateEpochDay).toString(),
                meal = row.log.mealType,
                food = row.food.id.toString(),
                g = round2(row.log.grams),
            )
        }

        AnalysisExport(
            app = "FoodTracker",
            schema = "food-log-analysis/1",
            exportedOn = DateUtil.epochDayToDate(today).toString(),
            readme = "Food-log export for nutrition analysis. `foods` lists each " +
                "distinct food once, keyed by an id; nutrient values in `per100g` are " +
                "PER 100 GRAMS (units are in `units`). Each entry in `logs` is one logged " +
                "food: d=date, meal=meal, food=id into `foods`, g=grams eaten. To get " +
                "nutrients actually consumed for an entry, multiply that food's per100g " +
                "values by g/100. `dailyGoals` are the user's daily targets (same " +
                "keys/units) for gauging whether intake is on track. " +
                "Conventions: a nutrient absent from a food's `per100g` (or from " +
                "`dailyGoals`) means that value is ~0, NOT unknown. The four " +
                "exceptions are solubleFiber, insolubleFiber, essentialAminoAcids " +
                "and leucine: most foods have no published figure for these, so " +
                "absent means UNKNOWN, not zero — do not read it as the food " +
                "containing none, and do not sum them across foods as if the " +
                "missing ones were 0. `brand` is present only when the food " +
                "carries one.",
            range = AnalysisRangeInfo(
                label = range.label,
                startDate = DateUtil.epochDayToDate(start).toString(),
                endDate = DateUtil.epochDayToDate(end).toString(),
                days = range.days,
                entries = logs.size,
                distinctFoods = foods.size,
            ),
            units = AllNutrientFields.associate { it.key to it.unit.symbol } +
                AllNutrientSubFields.associate { it.key to "g" },
            profile = AnalysisProfile(
                sex = profile.sex.name,
                ageYears = profile.ageYears,
                heightCm = profile.heightCm,
                weightKg = round2(profile.weightKg),
                goal = profile.goal.name,
                activityLevel = profile.activityLevel.name,
                goalWeightKg = profile.goalWeightKg?.let(::round2),
            ),
            dailyGoals = AllNutrientFields
                .associate { it.key to round2(it.goal(goals)) }
                .filterValues { it != 0.0 },
            foods = foods,
            logs = logs,
        )
    }

    /**
     * Serialise a [buildAnalysisExport] to the user-picked [target] URI.
     * Returns the number of log entries written so the caller can confirm it.
     */
    suspend fun exportAnalysisTo(target: Uri, range: AnalysisRange): Result<Int> = runCatching {
        val payload = buildAnalysisExport(range)
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(target, "w").use { stream ->
                requireNotNull(stream) { "Cannot open output stream for $target" }
                stream.writer(Charsets.UTF_8).use { writer ->
                    writer.write(analysisJson.encodeToString(AnalysisExport.serializer(), payload))
                    writer.flush()
                }
            }
        }
        payload.logs.size
    }

    /**
     * Per-100 g nutrients as a compact map, rounded, with zero values dropped.
     *
     * The optional sub-nutrients ride along on the same rule: a food without a
     * published breakdown holds 0 for them and so contributes no key at all,
     * which is exactly the right signal — absent means unknown here, and an
     * explicit 0 would tell a reader the food contains none.
     */
    private fun compactNutrients(n: Nutrients): Map<String, Double> =
        (AllNutrientFields.map { it.key to it.read(n) } +
            AllNutrientSubFields.map { it.key to it.read(n) })
            .mapNotNull { (key, raw) ->
                val v = round2(raw)
                if (v == 0.0) null else key to v
            }.toMap()

    // ─── External export / import (Storage Access Framework) ──────────────

    /**
     * Serialise the current DB+profile to the user-picked [target] URI.
     * Used by the "Export data" button — the resulting file is a plain
     * JSON file the user can keep, share, or feed into another app.
     */
    suspend fun exportTo(target: Uri): Result<Unit> = runCatching {
        val payload = buildSnapshot()
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(target, "w").use { stream ->
                requireNotNull(stream) { "Cannot open output stream for $target" }
                writePayload(stream, payload)
            }
        }
    }

    /**
     * Read a previously-exported JSON file from [source] and replace all
     * local data with it. Returns the restored payload's `createdAtMillis`
     * on success so the caller can confirm what just landed.
     */
    suspend fun importFrom(source: Uri): Result<Long> = runCatching {
        val payload = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(source).use { stream ->
                requireNotNull(stream) { "Cannot open input stream for $source" }
                readPayload(stream)
            }
        }
        restoreFromPayload(payload)
        payload.createdAtMillis
    }

    // ─── In-app local backup slots ────────────────────────────────────────
    //
    // Destination = the user's custom SAF tree URI when one is set,
    // otherwise `Downloads/FoodTracker/` via MediaStore. Both paths produce
    // a JSON file that any other app on the device can read.

    /**
     * Snapshot the database+profile and write a timestamped JSON file to
     * either the user-picked folder or `Downloads/FoodTracker/`. Returns
     * a [LocalBackup] describing what was saved so the caller can surface
     * the path in the UI.
     */
    suspend fun saveLocalBackup(): Result<LocalBackup> = runCatching {
        val payload = buildSnapshot()
        val customUri = profileStore.backupFolderUri.first()?.let(Uri::parse)
        val saved = withContext(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val name = "$BACKUP_PREFIX$timestamp.json"
            if (customUri != null) writeToCustomFolder(customUri, name, payload)
            else writeToDownloads(name, payload)
        }
        // Auto-prune: keep only the newest MAX_LOCAL_BACKUPS files. Any older
        // snapshots get deleted so the backup folder doesn't grow forever.
        // Failures here are non-fatal — we already wrote the new backup.
        runCatching { pruneOldBackups() }
        saved
    }

    private suspend fun pruneOldBackups() {
        val all = listBackups()
        if (all.size <= MAX_LOCAL_BACKUPS) return
        all.drop(MAX_LOCAL_BACKUPS).forEach { stale ->
            runCatching { deleteLocalBackup(stale) }
        }
    }

    private fun writeToCustomFolder(treeUri: Uri, name: String, payload: BackupPayload): LocalBackup {
        val dir = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Picked folder is no longer accessible. Pick again from Profile → Data.")
        val file = dir.createFile("application/json", name)
            ?: error("Couldn't create $name in the picked folder")
        try {
            context.contentResolver.openOutputStream(file.uri, "w").use { stream ->
                requireNotNull(stream) { "Cannot open output stream for ${file.uri}" }
                writePayload(stream, payload)
            }
        } catch (e: Exception) {
            runCatching { file.delete() }
            throw e
        }
        return LocalBackup(
            uri = file.uri,
            displayName = file.name ?: name,
            createdAtMillis = System.currentTimeMillis(),
            sizeBytes = file.length(),
            origin = LocalBackup.Origin.CustomFolder,
        )
    }

    private fun writeToDownloads(name: String, payload: BackupPayload): LocalBackup {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(backupCollection, values)
            ?: error("MediaStore refused to create backup entry")
        try {
            context.contentResolver.openOutputStream(uri, "w").use { stream ->
                requireNotNull(stream) { "Cannot open output stream for $uri" }
                writePayload(stream, payload)
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        } catch (e: Exception) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            throw e
        }
        return LocalBackup(
            uri = uri,
            displayName = name,
            createdAtMillis = System.currentTimeMillis(),
            sizeBytes = sizeOf(uri),
            origin = LocalBackup.Origin.Downloads,
        )
    }

    /**
     * List every JSON backup the app can find. If the user has picked a
     * custom folder we enumerate that; otherwise we fall back to
     * `Downloads/FoodTracker/` via MediaStore. Newest first.
     */
    suspend fun listBackups(): List<LocalBackup> = withContext(Dispatchers.IO) {
        val customUri = profileStore.backupFolderUri.first()?.let(Uri::parse)
        (if (customUri != null) listFromCustomFolder(customUri) else listFromDownloads())
            // Only our own snapshots. Pruning deletes from this list, and a
            // user-picked folder may hold other JSON — including this app's
            // own manual and analysis exports — which must never be touched.
            .filter { it.displayName.startsWith(BACKUP_PREFIX) }
    }

    private fun listFromCustomFolder(treeUri: Uri): List<LocalBackup> {
        val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            .asSequence()
            .filter { it.isFile && it.name?.endsWith(".json") == true }
            .map {
                LocalBackup(
                    uri = it.uri,
                    displayName = it.name ?: "backup.json",
                    createdAtMillis = it.lastModified(),
                    sizeBytes = it.length(),
                    origin = LocalBackup.Origin.CustomFolder,
                )
            }
            .sortedByDescending { it.createdAtMillis }
            .toList()
    }

    private fun listFromDownloads(): List<LocalBackup> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE,
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND " +
            "${MediaStore.MediaColumns.MIME_TYPE} = ?"
        val args = arrayOf(relativeDir, "application/json")
        val sort = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

        val results = mutableListOf<LocalBackup>()
        context.contentResolver.query(backupCollection, projection, selection, args, sort)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                results += LocalBackup(
                    uri = android.content.ContentUris.withAppendedId(backupCollection, id),
                    displayName = cursor.getString(nameCol),
                    // MediaStore stores DATE_MODIFIED in seconds since epoch.
                    createdAtMillis = cursor.getLong(dateCol) * 1000L,
                    sizeBytes = cursor.getLong(sizeCol),
                    origin = LocalBackup.Origin.Downloads,
                )
            }
        }
        return results
    }

    /** Restore the database+profile from a previously-saved local backup. */
    suspend fun restoreFrom(backup: LocalBackup): Result<Unit> = runCatching {
        val payload = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(backup.uri).use { stream ->
                requireNotNull(stream) { "Cannot read backup ${backup.displayName}" }
                readPayload(stream)
            }
        }
        restoreFromPayload(payload)
    }

    /** Delete a single local backup file. Works for both MediaStore and SAF entries. */
    suspend fun deleteLocalBackup(backup: LocalBackup): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val ok = when (backup.origin) {
                LocalBackup.Origin.Downloads ->
                    context.contentResolver.delete(backup.uri, null, null) > 0
                LocalBackup.Origin.CustomFolder ->
                    DocumentFile.fromSingleUri(context, backup.uri)?.delete() == true
            }
            if (!ok) error("Could not delete ${backup.displayName}")
        }
    }

    // ─── Custom backup folder management (SAF tree URI) ───────────────────

    /**
     * Persist the picked tree URI and take long-term read/write permission
     * so we can read and write inside it across app restarts.
     */
    suspend fun setCustomFolder(treeUri: Uri) {
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(treeUri, flags) }
        profileStore.setBackupFolderUri(treeUri.toString())
    }

    /** Forget the custom folder — future backups go to Downloads/FoodTracker again. */
    suspend fun clearCustomFolder() {
        profileStore.backupFolderUri.first()?.let { uriString ->
            runCatching {
                val uri = Uri.parse(uriString)
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.releasePersistableUriPermission(uri, flags)
            }
        }
        profileStore.setBackupFolderUri(null)
    }

    /**
     * Human-friendly display path for the configured folder (Settings UI).
     * Returns null when no custom folder is set.
     */
    suspend fun currentFolderLabel(): String? {
        val uriString = profileStore.backupFolderUri.first() ?: return null
        val treeUri = Uri.parse(uriString)
        return treeUri.lastPathSegment?.substringAfter(":")?.takeIf { it.isNotBlank() }
            ?: DocumentsContract.getTreeDocumentId(treeUri)
    }

    // ─── Internal plumbing ────────────────────────────────────────────────

    private fun sizeOf(uri: Uri): Long {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { return it.statSize }
        return 0L
    }

    private fun writePayload(out: OutputStream, payload: BackupPayload) {
        out.writer(Charsets.UTF_8).use { writer ->
            writer.write(json.encodeToString(BackupPayload.serializer(), payload))
            writer.flush()
        }
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private fun readPayload(input: InputStream): BackupPayload =
        input.use { json.decodeFromStream(BackupPayload.serializer(), it) }

    /**
     * Deletes every food, water and weight entry, in one transaction. Profile,
     * targets, custom foods, favourites and the plan stay.
     */
    suspend fun clearDiary(): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                database.foodLogDao().deleteAll()
                database.waterLogDao().deleteAll()
                database.weightLogDao().deleteAll()
            }
        }
        com.example.foodtracker.widget.WidgetUpdater.updateAll(context)
    }

    /**
     * Merge-style restore.
     *
     * Foods (customs):
     *  - Existing local customs are NEVER overwritten. We match by
     *    `clientUuid` first; if a row with that UUID already exists locally,
     *    we reuse it and skip the insert.
     *  - Customs in the backup that have no local match are inserted with a
     *    fresh local id but the original `clientUuid` preserved.
     *  - Seeded foods are not in the payload (or are skipped if they are):
     *    they're reproduced from `IndianFoodSeed` on install and matched by
     *    name when resolving log entries.
     *
     * Logs (food/water/activity/weight) and profile are replaced wholesale.
     * Each log entry's `foodId` is rewritten to the resolved local id before
     * insert; entries that can't be resolved (e.g. seeded name no longer in
     * the DB) are dropped.
     */
    private suspend fun restoreFromPayload(payload: BackupPayload) = withContext(Dispatchers.IO) {
        // One transaction around every Room write. A restore deletes the user's
        // whole log before re-inserting it, so without this a failure partway -
        // a malformed row in a hand-edited JSON, a full disk, process death -
        // commits the deletes and loses the data the user was trying to get
        // back. runCatching in restoreFrom/importFrom reports the failure but
        // cannot undo it. The profileStore writes stay outside: DataStore is
        // not part of the transaction and could not roll back anyway.
        database.withTransaction { restoreRoomTables(payload) }

        // Restore profile + settings. We mark onboarded so the user lands
        // on Home directly after a restore.
        profileStore.save(payload.profile.toUserProfile(), markOnboarded = true)
        profileStore.setHapticsEnabled(payload.profile.hapticsEnabled)
        // payload.profile.themeMode is deliberately dropped: the app is
        // dark-only, so there is no mode for a restore to put back.
        // No explicit widget refresh needed here: the profileStore.save() above
        // runs after all log/activity rows are restored and fires its
        // onProfileChanged hook → WidgetUpdater.updateAll with the final data.
    }

    private suspend fun restoreRoomTables(payload: BackupPayload) {
        val foodDao = database.foodDao()
        val foodLogDao = database.foodLogDao()
        val waterDao = database.waterLogDao()
        val weightDao = database.weightLogDao()

        // Custom-food merge — never touch an existing local custom; only add
        // missing ones. Matching priority: clientUuid → barcode → (name, brand).
        val backupCustoms = if (payload.customFoods.isNotEmpty()) {
            payload.customFoods
        } else {
            // Schema-1 fallback: the legacy `foods` list mixed seeded + customs.
            payload.foods.filter { it.isCustom }
        }

        // Index every food by old-snapshot-id so we can resolve schema-1 logs
        // whose `foodId` is the only pointer back to the source food.
        val byOldId: Map<Long, FoodSnapshot> =
            (payload.customFoods + payload.foods).associateBy { it.id }

        // oldFoodId → local FoodEntity.id (for log resolution).
        val oldIdToLocalId = mutableMapOf<Long, Long>()

        for (food in backupCustoms) {
            val existing = food.clientUuid?.let { foodDao.findByClientUuid(it) }
                ?: food.barcode?.let { foodDao.findByBarcode(it) }
                ?: existingCustomByNameBrand(foodDao, food)
            val localId = if (existing != null) {
                existing.id // reuse — do NOT overwrite
            } else {
                foodDao.upsert(food.toInsertableEntity())
            }
            oldIdToLocalId[food.id] = localId
        }

        // Seeded foods present in a schema-1 payload — resolve by name only,
        // never insert (the local install already seeded them).
        val seededByName: Map<String, Long> =
            foodDao.getAllSeeded().associate { it.name.lowercase() to it.id }
        for (food in payload.foods) {
            if (food.isCustom) continue
            val localId = seededByName[food.name.lowercase()] ?: continue
            oldIdToLocalId[food.id] = localId
        }

        // Replace foodLog wholesale (it's user-owned data and a backup is a
        // point-in-time copy — we don't try to dedupe entries).
        foodLogDao.deleteAll()
        val resolvedLogs = payload.foodLog.mapNotNull { log ->
            val localFoodId = resolveLocalFoodId(
                foodClientUuid = log.foodClientUuid,
                foodName = log.foodName,
                sourceFoodId = log.foodId,
                foodDao = foodDao,
                byOldId = byOldId,
                oldIdToLocalId = oldIdToLocalId,
                seededByName = seededByName,
                backupCustoms = backupCustoms,
            ) ?: return@mapNotNull null
            log.toEntity(localFoodId)
        }
        foodLogDao.insertAll(resolvedLogs)

        // Same treatment for the plan, and for the same reason: it is
        // user-owned data and a backup is a point-in-time copy of it, so the
        // local plan is replaced rather than merged. A line whose food cannot
        // be resolved on this install is dropped — better a shorter plan than
        // a row that renders as a blank.
        val planDao = database.planDao()
        planDao.deleteAll()
        val resolvedPlan = payload.plan.mapNotNull { item ->
            val localFoodId = resolveLocalFoodId(
                foodClientUuid = item.foodClientUuid,
                foodName = item.foodName,
                sourceFoodId = item.foodId,
                foodDao = foodDao,
                byOldId = byOldId,
                oldIdToLocalId = oldIdToLocalId,
                seededByName = seededByName,
                backupCustoms = backupCustoms,
            ) ?: return@mapNotNull null
            item.toEntity(localFoodId)
        }
        // Distinct by (food, meal): `plan_items` has a unique index on that
        // pair, and two snapshot lines can collapse onto it when two different
        // source foods resolve to the same local row — a custom the target
        // already had under a different uuid, say. Letting the insert fold them
        // silently would keep an arbitrary one of the two amounts, so the first
        // is taken deliberately.
        planDao.insertAll(resolvedPlan.distinctBy { it.foodId to it.mealType })

        // The rest of the logs don't reference foods — wipe and re-insert.
        waterDao.deleteAll()
        weightDao.deleteAll()
        waterDao.insertAll(payload.waterLog.map { it.toEntity().copy(id = 0) })
        weightDao.upsertAll(payload.weightLog.map { it.toEntity().copy(id = 0) })
    }

    private suspend fun existingCustomByNameBrand(
        foodDao: com.example.foodtracker.data.db.FoodDao,
        food: FoodSnapshot,
    ): com.example.foodtracker.data.db.FoodEntity? {
        // Last-resort match for backups that predate clientUuid. Conservative:
        // only matches when both name and brand line up exactly (case-insensitive).
        return foodDao.getAllCustom().firstOrNull {
            it.name.equals(food.name, ignoreCase = true) &&
                (it.brand ?: "").equals(food.brand ?: "", ignoreCase = true)
        }
    }

    /**
     * Point a snapshot's food reference at a row on *this* install.
     *
     * Takes the three portable fields rather than a snapshot type, because log
     * entries and plan lines carry exactly the same reference and resolving
     * them two different ways is how the two would drift.
     */
    private suspend fun resolveLocalFoodId(
        foodClientUuid: String?,
        foodName: String,
        sourceFoodId: Long,
        foodDao: com.example.foodtracker.data.db.FoodDao,
        byOldId: Map<Long, FoodSnapshot>,
        oldIdToLocalId: Map<Long, Long>,
        seededByName: Map<String, Long>,
        backupCustoms: List<FoodSnapshot>,
    ): Long? {
        // Preferred: the row carries a clientUuid for the referenced custom.
        foodClientUuid?.let { uuid ->
            foodDao.findByClientUuid(uuid)?.let { return it.id }
            // UUID not local yet — maybe the custom was just inserted.
            backupCustoms.firstOrNull { it.clientUuid == uuid }
                ?.let { return oldIdToLocalId[it.id] }
        }
        // Fallback: the row's name resolves to a seeded food.
        if (foodName.isNotBlank()) {
            seededByName[foodName.lowercase()]?.let { return it }
        }
        // Last resort (schema-1 logs): map via old foodId.
        oldIdToLocalId[sourceFoodId]?.let { return it }
        // Schema-1 + seeded: look the source food up by id and resolve its name.
        val sourceFood = byOldId[sourceFoodId] ?: return null
        if (!sourceFood.isCustom) {
            seededByName[sourceFood.name.lowercase()]?.let { return it }
        }
        return null
    }
}

/** Round to 2 decimals — trims export size without losing meaningful precision. */
private fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0

/**
 * Lightweight view of a backup file the app can read/delete. `uri` works
 * for both MediaStore-backed (`Downloads/FoodTracker/`) and SAF-backed
 * (user-picked folder) entries; `origin` tells us which delete API to call.
 */
data class LocalBackup(
    val uri: Uri,
    val displayName: String,
    val createdAtMillis: Long,
    val sizeBytes: Long,
    val origin: Origin = Origin.Downloads,
) {
    enum class Origin { Downloads, CustomFolder }
}
