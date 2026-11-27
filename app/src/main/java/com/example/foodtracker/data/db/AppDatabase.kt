package com.example.foodtracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FoodEntity::class,
        FoodLogEntity::class,
        WaterLogEntity::class,
        WeightLogEntity::class,
        PlanItemEntity::class,
    ],
    version = 13,
    // Exported to app/schemas so future migrations have a reference to diff.
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun foodLogDao(): FoodLogDao
    abstract fun waterLogDao(): WaterLogDao
    abstract fun weightLogDao(): WeightLogDao
    abstract fun planDao(): PlanDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * v4 → v5: adds four mineral columns to `foods` (phosphorus, selenium,
         * copper, manganese). A real ALTER-based migration — NOT destructive —
         * so existing foods, custom foods, and the entire food log survive the
         * update. The columns have no `@ColumnInfo(defaultValue=...)`, so Room
         * doesn't validate their DB default; `DEFAULT 0` just satisfies SQLite's
         * NOT NULL requirement for the backfill of existing rows.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE foods ADD COLUMN phosphorus REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE foods ADD COLUMN selenium REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE foods ADD COLUMN copper REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE foods ADD COLUMN manganese REAL NOT NULL DEFAULT 0")
            }
        }

        /**
         * v5 → v6: adds four B-complex vitamin columns to `foods` (B1/thiamin,
         * B2/riboflavin, B3/niacin, B6). Non-destructive ALTER, same rationale
         * as [MIGRATION_4_5] — all foods and the food log survive the update.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE foods ADD COLUMN vitamin_b1 REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE foods ADD COLUMN vitamin_b2 REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE foods ADD COLUMN vitamin_b3 REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE foods ADD COLUMN vitamin_b6 REAL NOT NULL DEFAULT 0")
            }
        }

        /**
         * v6 → v7: adds the `omega3` column to `foods` (grams of omega-3 fatty
         * acids). Non-destructive ALTER, same rationale as [MIGRATION_4_5] — all
         * foods and the food log survive the update.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE foods ADD COLUMN omega3 REAL NOT NULL DEFAULT 0")
            }
        }

        /**
         * v7 → v8: drops `activity_log`. Exercise logging is gone — the
         * activity multiplier in
         * [com.example.foodtracker.domain.GoalCalculator.tdee] already accounts
         * for training, so logging a workout on top credited the same calories
         * a second time and inflated the day's allowance. Counting it in one
         * place means the table has no reader left.
         *
         * Destructive by design, and the only migration here that discards
         * anything: any exercise a user logged is deleted on update.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS activity_log")
            }
        }


        /**
         * v8 → v9: adds four optional sub-nutrient columns to `foods` — the
         * soluble/insoluble fiber split, total essential amino acids, and
         * leucine.
         * Non-destructive ALTER, same rationale as [MIGRATION_4_5].
         *
         * Every existing row backfills to 0, which the app reads as UNKNOWN
         * rather than "contains none": a food keeps its total fiber and protein
         * exactly as before and simply shows no breakdown. Only the hand-filled
         * seed subset, USDA lookups and new AI fills carry real values, so no
         * custom or previously-AI-filled food is retro-fitted with a guess.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE foods ADD COLUMN soluble_fiber REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE foods ADD COLUMN insoluble_fiber REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE foods ADD COLUMN essential_amino_acids REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE foods ADD COLUMN leucine REAL NOT NULL DEFAULT 0")
            }
        }

        /**
         * v9 → v10: teaches the app how a dish was prepared, not just what it
         * contains.
         *
         * On `foods`: `oil_grams` + `oil_default` (how much added cooking fat
         * per 100 g, and the oil the stored figures were computed in),
         * `style_options` (preparation choices such as plain vs ghee roast),
         * and `last_oil` + `last_style` (what the user picked last, so a food
         * reopens where they left it). On `food_log`: nullable `oil` and
         * `style`, recording what one entry was actually made with.
         * Non-destructive ALTER, same rationale as [MIGRATION_4_5].
         *
         * Every existing food backfills to `oil_grams = 0`, meaning "no
         * cooking fat to reattribute" — so until the seed refresh runs, every
         * food keeps exactly the nutrients it had and shows no oil picker.
         * Every existing log row backfills `oil` and `style` to NULL, which
         * reads as "the food's own default" rather than as a choice the user
         * never made.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE foods ADD COLUMN oil_grams REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE foods ADD COLUMN oil_default TEXT NOT NULL DEFAULT 'Palmolein'")
                db.execSQL("ALTER TABLE foods ADD COLUMN style_options TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE foods ADD COLUMN last_oil TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE foods ADD COLUMN last_style TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE food_log ADD COLUMN oil TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE food_log ADD COLUMN style TEXT DEFAULT NULL")
            }
        }

        /**
         * v10 → v11: adds `plan_items`, the single standing plan.
         *
         * A new table only — nothing existing is read, altered or dropped, so
         * every food, log, water and weight row is untouched and a user who
         * never opens the plan cannot tell the migration ran.
         *
         * The DDL has to match what Room would have generated exactly, down to
         * the index names, or `validateMigration` rejects the database on the
         * next open. `(food_id, meal_type)` is UNIQUE on purpose — see the note
         * on [PlanItemEntity] for why two plan rows for the same food in the
         * same meal would make the tick marks unreadable.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `plan_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `food_id` INTEGER NOT NULL,
                        `meal_type` TEXT NOT NULL,
                        `servings` REAL NOT NULL,
                        `grams` REAL NOT NULL,
                        `serving_label` TEXT NOT NULL,
                        `oil` TEXT,
                        `style` TEXT,
                        `sort_index` INTEGER NOT NULL,
                        FOREIGN KEY(`food_id`) REFERENCES `foods`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_plan_items_food_id_meal_type` " +
                        "ON `plan_items` (`food_id`, `meal_type`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_plan_items_food_id` ON `plan_items` (`food_id`)"
                )
            }
        }

        /**
         * v11 → v12: adds the oil-*amount* axis — `last_oil_amount` on `foods`
         * (what this kitchen uses for that dish), and `oil_amount` on
         * `food_log` and `plan_items` (what one entry or planned line was
         * actually priced at).
         *
         * Three nullable ALTERs, same rationale as [MIGRATION_4_5]. Every
         * existing row backfills to NULL, which reads as "the food's own
         * amount" — and since nothing has chosen one yet, that resolves to the
         * seed's documented figure. So the migration changes no number on any
         * screen; it only makes a choice recordable.
         *
         * The per-entry columns are the point of the migration rather than a
         * convenience: the food remembers the amount, so without them the first
         * tap of "Light" would retroactively re-price every dosa in the diary.
         * See [FoodLogEntity.oilAmount].
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE foods ADD COLUMN last_oil_amount TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE food_log ADD COLUMN oil_amount TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE plan_items ADD COLUMN oil_amount TEXT DEFAULT NULL")
            }
        }

        /**
         * Indexes only — no rows are touched. Names follow Room's
         * `index_<table>_<cols>` scheme exactly, or Room's open-time schema
         * check fails.
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_food_log_date_epoch_day`")
                db.execSQL("DROP INDEX IF EXISTS `index_food_log_food_id`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_food_log_date_epoch_day_logged_at_millis` " +
                        "ON `food_log` (`date_epoch_day`, `logged_at_millis`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_food_log_food_id_logged_at_millis` " +
                        "ON `food_log` (`food_id`, `logged_at_millis`)"
                )
                db.execSQL("DROP INDEX IF EXISTS `index_plan_items_food_id`")
                db.execSQL("DROP INDEX IF EXISTS `index_foods_name`")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "food_tracker.db",
            )
                .addMigrations(
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                )
                // Scoped to the pre-release versions that never had migrations
                // written. Deliberately NOT the blanket
                // fallbackToDestructiveMigration: a *future* forgotten
                // migration must fail loudly rather than silently dropping
                // every food, water, activity and weight log the user has ever
                // recorded. A crash is recoverable by shipping the migration;
                // the data is not.
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2, 3)
                .build()
                .also { instance = it }
        }
    }
}
