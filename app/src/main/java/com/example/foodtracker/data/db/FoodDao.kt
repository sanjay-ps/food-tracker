package com.example.foodtracker.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.example.foodtracker.data.CookingOil
import com.example.foodtracker.data.StyleOption
import com.example.foodtracker.data.Nutrients
import com.example.foodtracker.data.OilAmount
import kotlinx.coroutines.flow.Flow

data class FoodLogWithFood(
    @Embedded val log: FoodLogEntity,
    @Relation(parentColumn = "food_id", entityColumn = "id")
    val food: FoodEntity,
)

/**
 * What this row actually contributed, scaled to the grams logged and prepared
 * the way it was recorded.
 *
 * What the entry says wins, and silence means the food's own default rather
 * than whatever the user happens to prefer today. That distinction is the
 * whole point of storing the choice per row: someone who logs a street samosa
 * fried in palmolein and later starts cooking in coconut oil must not find
 * last Tuesday's samosa quietly re-scored. Rows written before the picker
 * existed are silent on both axes and so keep exactly the numbers they always
 * had.
 */
fun FoodLogWithFood.nutrients(): Nutrients = food.nutrientsPer100g(
    oil = CookingOil.fromName(log.oil) ?: food.defaultOil,
    style = StyleOption.find(food.styleOptions, log.style) ?: food.styleOptions.firstOrNull(),
    // Falls back to what the food remembers, not to Normal: rows written
    // before this column existed belong to a kitchen that has since told us
    // how much oil it uses, and re-pricing them upward would be the same
    // silent rewrite the column exists to prevent.
    oilAmount = OilAmount.fromName(log.oilAmount) ?: food.preselectedOilAmount(),
) * (log.grams / 100.0)

@Dao
interface FoodDao {
    @Query("SELECT * FROM foods WHERE is_hidden = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<FoodEntity>>

    /** The first [limit] foods A–Z: the picker loads the catalogue a page at a time. */
    @Query("SELECT * FROM foods WHERE is_hidden = 0 ORDER BY name COLLATE NOCASE ASC LIMIT :limit")
    fun observeAllPaged(limit: Int): Flow<List<FoodEntity>>

    @Query("SELECT COUNT(*) FROM foods WHERE is_hidden = 0")
    fun observeVisibleCount(): Flow<Int>

    @Query(
        """
        SELECT * FROM foods
        WHERE is_hidden = 0
          AND (name LIKE '%' || :query || '%' COLLATE NOCASE
               OR brand LIKE '%' || :query || '%' COLLATE NOCASE)
        ORDER BY
          CASE WHEN name LIKE :query || '%' THEN 0 ELSE 1 END,
          name COLLATE NOCASE ASC
        LIMIT 100
        """
    )
    fun search(query: String): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods WHERE is_favourite = 1 AND is_hidden = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeFavourites(): Flow<List<FoodEntity>>

    /** Foods the user added by hand or saved from a barcode scan, still active. */
    @Query("SELECT * FROM foods WHERE is_custom = 1 AND is_hidden = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeCustomVisible(): Flow<List<FoodEntity>>

    /** Custom foods the user soft-deleted; surfaced so they can restore or wipe them. */
    @Query("SELECT * FROM foods WHERE is_custom = 1 AND is_hidden = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeCustomHidden(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun getById(id: Long): FoodEntity?

    @Query("SELECT * FROM foods WHERE barcode = :barcode AND is_hidden = 0 LIMIT 1")
    suspend fun findByBarcode(barcode: String): FoodEntity?

    @Query("SELECT * FROM foods WHERE name = :name COLLATE NOCASE AND is_custom = 0 AND is_hidden = 0 LIMIT 1")
    suspend fun findSeededByName(name: String): FoodEntity?

    /**
     * Every row [findSeededByName] could match, in one read — same `is_custom`
     * and `is_hidden` filters.
     *
     * NOT for the seed refresh, which wants [getAllSeeded]. The difference is
     * the hidden rows, and it matters: this answers "which food should I show
     * for this name", where a hidden one rightly doesn't count, whereas the
     * refresh asks "does a row for this seed entry already exist", where it
     * does. Missing a hidden row there inserts a duplicate rather than
     * updating it.
     */
    @Query("SELECT * FROM foods WHERE is_custom = 0 AND is_hidden = 0")
    suspend fun getVisibleSeeded(): List<FoodEntity>

    /** Stable cross-install lookup for backup/restore merge logic. */
    @Query("SELECT * FROM foods WHERE client_uuid = :uuid LIMIT 1")
    suspend fun findByClientUuid(uuid: String): FoodEntity?

    @Query("SELECT * FROM foods WHERE is_custom = 1")
    suspend fun getAllCustom(): List<FoodEntity>

    @Query("SELECT * FROM foods WHERE is_custom = 0")
    suspend fun getAllSeeded(): List<FoodEntity>

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun count(): Int

    // NOTE: must be @Upsert, NOT @Insert(REPLACE). food_log has an onDelete=CASCADE
    // foreign key to foods, and SQLite's INSERT-OR-REPLACE deletes the conflicting
    // row before re-inserting — which cascades and wipes every log entry pointing at
    // that food. @Upsert performs a real UPDATE on conflict, so logs are preserved
    // when seed refreshes / barcode re-saves touch an existing food row.
    @Upsert
    suspend fun upsert(food: FoodEntity): Long

    @Upsert
    suspend fun upsertAll(foods: List<FoodEntity>)

    @Update
    suspend fun update(food: FoodEntity)

    @Query("UPDATE foods SET is_favourite = :favourite WHERE id = :id")
    suspend fun setFavourite(id: Long, favourite: Boolean)

    /**
     * Remember the preparation the user last picked for this food.
     *
     * Deliberately fired on selection rather than on log, so backing out of the
     * sheet still teaches the app something. Cheap enough to do on every chip
     * tap: one row, two text columns, no index touched.
     */
    @Query(
        "UPDATE foods SET last_oil = :oil, last_style = :style, " +
            "last_oil_amount = :oilAmount WHERE id = :id"
    )
    suspend fun rememberVariant(id: Long, oil: String?, style: String?, oilAmount: String?)

    @Query("DELETE FROM foods WHERE id = :id")
    suspend fun delete(id: Long)

    /** Soft-delete: row stays so historical food_log entries can still join to it. */
    @Query("UPDATE foods SET is_hidden = 1, is_favourite = 0 WHERE id = :id")
    suspend fun hide(id: Long)

    /** Reverse of [hide] — bring a soft-deleted custom food back into pickers. */
    @Query("UPDATE foods SET is_hidden = 0 WHERE id = :id")
    suspend fun unhide(id: Long)

    /** Hard-delete every custom food. Cascades via FK to wipe their logs. */
    @Query("DELETE FROM foods WHERE is_custom = 1")
    suspend fun deleteAllCustom()

    /** Soft-delete every custom food, preserving log history. */
    @Query("UPDATE foods SET is_hidden = 1, is_favourite = 0 WHERE is_custom = 1")
    suspend fun hideAllCustom()

    @Query("SELECT * FROM foods")
    suspend fun getAll(): List<FoodEntity>

    @Query("DELETE FROM foods")
    suspend fun deleteAll()
}

@Dao
interface FoodLogDao {
    /** Every day with at least one entry, oldest first — streaks read this. */
    @Query("SELECT DISTINCT date_epoch_day FROM food_log ORDER BY date_epoch_day ASC")
    fun observeLoggedDays(): Flow<List<Long>>

    @Transaction
    @Query("SELECT * FROM food_log WHERE date_epoch_day = :epochDay ORDER BY logged_at_millis ASC")
    fun observeForDay(epochDay: Long): Flow<List<FoodLogWithFood>>

    /** Read-after-write consistent version of [observeForDay]; see FoodDao.getRecentFoods. */
    @Transaction
    @Query("SELECT * FROM food_log WHERE date_epoch_day = :epochDay ORDER BY logged_at_millis ASC")
    suspend fun getForDay(epochDay: Long): List<FoodLogWithFood>

    @Transaction
    @Query("SELECT * FROM food_log WHERE date_epoch_day BETWEEN :startEpochDay AND :endEpochDay ORDER BY date_epoch_day ASC, logged_at_millis ASC")
    fun observeForRange(startEpochDay: Long, endEpochDay: Long): Flow<List<FoodLogWithFood>>

    /** One-shot range read (with joined food) — used by the AI-analysis export. */
    @Transaction
    @Query("SELECT * FROM food_log WHERE date_epoch_day BETWEEN :startEpochDay AND :endEpochDay ORDER BY date_epoch_day ASC, logged_at_millis ASC")
    suspend fun getForRange(startEpochDay: Long, endEpochDay: Long): List<FoodLogWithFood>

    /** Recently logged foods, most recent first, deduplicated. */
    @Transaction
    @Query(
        """
        SELECT f.* FROM foods f
        INNER JOIN (
            SELECT food_id, MAX(logged_at_millis) AS last_used
            FROM food_log
            GROUP BY food_id
        ) recent ON recent.food_id = f.id
        WHERE f.is_hidden = 0
        ORDER BY recent.last_used DESC
        LIMIT :limit
        """
    )
    fun observeRecentFoods(limit: Int): Flow<List<FoodEntity>>

    /**
     * Read-after-write consistent version of [observeRecentFoods]. Glance
     * widgets read this directly instead of `.first()`-ing the flow so the
     * snapshot reflects the latest write, not the pre-write state (Room's
     * InvalidationTracker emits asynchronously, which races with widget
     * refresh callbacks).
     */
    @Transaction
    @Query(
        """
        SELECT f.* FROM foods f
        INNER JOIN (
            SELECT food_id, MAX(logged_at_millis) AS last_used
            FROM food_log
            GROUP BY food_id
        ) recent ON recent.food_id = f.id
        WHERE f.is_hidden = 0
        ORDER BY recent.last_used DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentFoods(limit: Int): List<FoodEntity>

    /** Most-logged foods over the last `days` days. */
    @Transaction
    @Query(
        """
        SELECT f.* FROM foods f
        INNER JOIN (
            SELECT food_id, COUNT(*) AS uses
            FROM food_log
            WHERE date_epoch_day >= :sinceEpochDay
            GROUP BY food_id
        ) freq ON freq.food_id = f.id
        WHERE f.is_hidden = 0
        ORDER BY freq.uses DESC, f.name COLLATE NOCASE ASC
        LIMIT :limit
        """
    )
    fun observeFrequentFoods(sinceEpochDay: Long, limit: Int): Flow<List<FoodEntity>>

    @Insert
    suspend fun insert(entry: FoodLogEntity): Long

    /**
     * Edit a logged entry in place.
     *
     * This is the **only** data-layer change in the whole UI rewrite, and it is
     * not a schema change: [FoodLogEntity] already has every column an edit
     * touches (`grams`, `servings`, `servingLabel`, `mealType`, `oil`, `style`,
     * `dateEpochDay`). No Room version bump, no migration.
     *
     * Its absence was the biggest behavioural hole in the app: the diary had
     * `insert` and `delete` but no update, so a wrong serving could only be
     * fixed by deleting the row and logging the food again.
     */
    @Update
    suspend fun update(entry: FoodLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<FoodLogEntity>)

    @Query("DELETE FROM food_log WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM food_log")
    suspend fun getAll(): List<FoodLogEntity>

    @Query("DELETE FROM food_log")
    suspend fun deleteAll()
}

@Dao
interface WaterLogDao {
    @Query("SELECT * FROM water_log WHERE date_epoch_day = :epochDay ORDER BY logged_at_millis ASC")
    fun observeForDay(epochDay: Long): Flow<List<WaterLogEntity>>

    @Query("SELECT COALESCE(SUM(amount_ml), 0) FROM water_log WHERE date_epoch_day = :epochDay")
    fun observeTotalForDay(epochDay: Long): Flow<Int>

    @Query("SELECT date_epoch_day AS dateEpochDay, COALESCE(SUM(amount_ml), 0) AS totalMl FROM water_log WHERE date_epoch_day BETWEEN :start AND :end GROUP BY date_epoch_day")
    fun observeTotalsForRange(start: Long, end: Long): Flow<List<DailyWaterTotal>>

    @Insert
    suspend fun insert(entry: WaterLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<WaterLogEntity>)

    @Query("DELETE FROM water_log WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM water_log")
    suspend fun getAll(): List<WaterLogEntity>

    @Query("DELETE FROM water_log")
    suspend fun deleteAll()
}

data class DailyWaterTotal(val dateEpochDay: Long, val totalMl: Int)

@Dao
interface WeightLogDao {
    @Query("SELECT * FROM weight_log ORDER BY date_epoch_day DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<WeightLogEntity>>

    @Query("SELECT * FROM weight_log WHERE date_epoch_day BETWEEN :start AND :end ORDER BY date_epoch_day ASC")
    fun observeForRange(start: Long, end: Long): Flow<List<WeightLogEntity>>

    @Query("SELECT * FROM weight_log ORDER BY date_epoch_day DESC LIMIT 1")
    fun observeLatest(): Flow<WeightLogEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WeightLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<WeightLogEntity>)

    @Query("DELETE FROM weight_log WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM weight_log")
    suspend fun getAll(): List<WeightLogEntity>

    @Query("DELETE FROM weight_log")
    suspend fun deleteAll()
}
