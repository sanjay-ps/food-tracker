package com.example.foodtracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "food_log",
    // Two-column indexes that match the queries: a day's entries in time
    // order, and "recent foods" (latest entry per food). The second one still
    // starts with food_id, so the foreign key stays indexed. See MIGRATION_12_13.
    indices = [
        Index(value = ["date_epoch_day", "logged_at_millis"]),
        Index(value = ["food_id", "logged_at_millis"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = FoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["food_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class FoodLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "food_id") val foodId: Long,
    @ColumnInfo(name = "date_epoch_day") val dateEpochDay: Long,
    @ColumnInfo(name = "meal_type") val mealType: String,
    val servings: Double,
    @ColumnInfo(name = "grams") val grams: Double,
    @ColumnInfo(name = "serving_label", defaultValue = "") val servingLabel: String = "",
    /**
     * Name of the [com.example.foodtracker.data.CookingOil] this entry was
     * logged with, or null to follow the food's own default.
     *
     * Stored per entry rather than resolved at display time because the two
     * answer different questions. The food knows how it is usually made; the
     * log has to say what *that* bajji was fried in. Someone who eats a street
     * samosa and later starts cooking in coconut oil must not have last
     * Tuesday's samosa silently re-scored.
     *
     * Null is the honest value for every row written before the picker existed
     * and for foods with no cooking fat at all — those follow the default and
     * cost nothing either way.
     */
    @ColumnInfo(name = "oil") val oil: String? = null,
    /** Label of the [com.example.foodtracker.data.StyleOption] chosen, or null
     *  for the food's default. Stored for the same reason as [oil]: the diary
     *  has to keep saying "ghee roast" after the food's default changes. */
    @ColumnInfo(name = "style") val style: String? = null,
    /**
     * Name of the [com.example.foodtracker.data.OilAmount] this entry was
     * logged with, or null for the food's own.
     *
     * Frozen here for a sharper version of [oil]'s reason. How much oil a
     * kitchen uses is a standing fact, so the food remembers it — which means
     * that without this column, the day the user first taps "Light" would
     * silently re-price every dosa they have ever logged. The diary has to keep
     * saying what each one actually cost.
     */
    @ColumnInfo(name = "oil_amount") val oilAmount: String? = null,
    @ColumnInfo(name = "logged_at_millis") val loggedAtMillis: Long,
)

@Entity(
    tableName = "water_log",
    indices = [Index(value = ["date_epoch_day"])],
)
data class WaterLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date_epoch_day") val dateEpochDay: Long,
    @ColumnInfo(name = "amount_ml") val amountMl: Int,
    @ColumnInfo(name = "logged_at_millis") val loggedAtMillis: Long,
)

@Entity(
    tableName = "weight_log",
    indices = [Index(value = ["date_epoch_day"], unique = true)],
)
data class WeightLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date_epoch_day") val dateEpochDay: Long,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    val note: String? = null,
    @ColumnInfo(name = "logged_at_millis") val loggedAtMillis: Long,
)
