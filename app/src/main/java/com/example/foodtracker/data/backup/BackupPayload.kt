package com.example.foodtracker.data.backup

import com.example.foodtracker.data.ActivityLevel
import com.example.foodtracker.data.Goal
import com.example.foodtracker.data.ServingVariant
import com.example.foodtracker.data.Sex
import com.example.foodtracker.data.UserProfile
import com.example.foodtracker.data.WeightChangeRate
import com.example.foodtracker.data.db.FoodEntity
import com.example.foodtracker.data.db.FoodLogEntity
import com.example.foodtracker.data.db.PlanItemEntity
import com.example.foodtracker.data.db.WaterLogEntity
import com.example.foodtracker.data.db.WeightLogEntity
import kotlinx.serialization.Serializable

/**
 * One self-contained snapshot of the user-owned data. Deliberately small: we
 * back up only the *custom* foods that the foodLog references, never seeded
 * rows (those are deterministic per install). Logs are matched back to foods
 * by stable `clientUuid` for customs and `name` for seeded entries — the local
 * row's `id` is never used across installs.
 *
 *  - `customFoods`  : current schema (v2+). Only customs referenced by foodLog.
 *  - `foods`        : legacy schema (v1). Whole foods table — still readable so
 *    old snapshots remain importable; on import we extract customs from it.
 */
@Serializable
data class BackupPayload(
    val schemaVersion: Int = SCHEMA_VERSION,
    val createdAtMillis: Long,
    val appVersion: String,
    val profile: ProfileSnapshot,
    /** Legacy: whole-foods table for schema-1 imports. New saves leave this empty. */
    val foods: List<FoodSnapshot> = emptyList(),
    /** New: only customs referenced by [foodLog]; seeded foods are recreated locally. */
    val customFoods: List<FoodSnapshot> = emptyList(),
    val foodLog: List<FoodLogSnapshot>,
    val waterLog: List<WaterLogSnapshot>,
    val weightLog: List<WeightLogSnapshot>,
    /**
     * The standing plan. Added after SCHEMA_VERSION 2 and defaulted, so older
     * payloads still load — a backup taken before the feature existed restores
     * to an empty plan, which is the truth about that device.
     */
    val plan: List<PlanItemSnapshot> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION = 2
    }
}

@Serializable
data class ProfileSnapshot(
    val name: String = "",
    val ageYears: Int = 25,
    val sex: String = Sex.Male.name,
    val heightCm: Int = 170,
    val weightKg: Double = 65.0,
    val activityLevel: String = ActivityLevel.Moderate.name,
    val goal: String = Goal.Maintain.name,
    val weightChangeRate: String = WeightChangeRate.Standard.name,
    val hapticsEnabled: Boolean = true,
    /**
     * Dead since the app went dark-only, but still written and still read back
     * into nothing. The field stays because the backup format is explicitly out
     * of scope for the UI rewrite: dropping it would make every new export
     * unreadable by an older build for no benefit.
     */
    val themeMode: String = LEGACY_THEME_MODE,
    val goalWeightKg: Double? = null,
    /** Added after SCHEMA_VERSION 2. Defaulted, so older payloads still load. */
    val trainingMix: Double? = null,
) {
    fun toUserProfile() = UserProfile(
        name = name,
        ageYears = ageYears,
        sex = Sex.fromName(sex),
        heightCm = heightCm,
        weightKg = weightKg,
        activityLevel = ActivityLevel.fromName(activityLevel),
        goal = Goal.fromName(goal),
        weightChangeRate = WeightChangeRate.fromName(weightChangeRate),
        goalWeightKg = goalWeightKg,
        trainingMix = trainingMix,
    )

    companion object {
        /** Kept for the dead [themeMode] field — see its docstring. */
        const val LEGACY_THEME_MODE = "System"

        fun from(profile: UserProfile, haptics: Boolean) = ProfileSnapshot(
            name = profile.name,
            ageYears = profile.ageYears,
            sex = profile.sex.name,
            heightCm = profile.heightCm,
            weightKg = profile.weightKg,
            activityLevel = profile.activityLevel.name,
            goal = profile.goal.name,
            weightChangeRate = profile.weightChangeRate.name,
            hapticsEnabled = haptics,
            goalWeightKg = profile.goalWeightKg,
            trainingMix = profile.trainingMix,
        )
    }
}

@Serializable
data class FoodSnapshot(
    val id: Long,
    /** Stable cross-install identity. Null for schema-1 snapshots — the restore
     *  code generates one on insert so the row gets a UUID going forward. */
    val clientUuid: String? = null,
    val name: String,
    val brand: String? = null,
    val category: String,
    val servingGrams: Double,
    val servingLabel: String,
    val oilGrams: Double = 0.0,
    val oilDefault: String = "Palmolein",
    val servingVariants: List<ServingVariant> = emptyList(),
    val barcode: String? = null,
    val isCustom: Boolean = false,
    val isFavourite: Boolean = false,
    val isHidden: Boolean = false,
    val calories: Double = 0.0,
    val carbs: Double = 0.0,
    val sugar: Double = 0.0,
    val fiber: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val saturatedFat: Double = 0.0,
    val omega3: Double = 0.0,
    val cholesterol: Double = 0.0,
    val sodium: Double = 0.0,
    val calcium: Double = 0.0,
    val iron: Double = 0.0,
    val magnesium: Double = 0.0,
    val potassium: Double = 0.0,
    val zinc: Double = 0.0,
    val phosphorus: Double = 0.0,
    val selenium: Double = 0.0,
    val copper: Double = 0.0,
    val manganese: Double = 0.0,
    val vitaminA: Double = 0.0,
    val vitaminC: Double = 0.0,
    val vitaminD: Double = 0.0,
    val vitaminE: Double = 0.0,
    val vitaminK: Double = 0.0,
    val vitaminB12: Double = 0.0,
    val folate: Double = 0.0,
    val vitaminB1: Double = 0.0,
    val vitaminB2: Double = 0.0,
    val vitaminB3: Double = 0.0,
    val vitaminB6: Double = 0.0,
    /** Added after SCHEMA_VERSION 2. Defaulted, so older payloads still load. */
    val solubleFiber: Double = 0.0,
    val insolubleFiber: Double = 0.0,
    val essentialAminoAcids: Double = 0.0,
    val leucine: Double = 0.0,
) {
    /**
     * Build a [FoodEntity] for insert. Local `id` is dropped (Room re-issues
     * one on insert) and missing `clientUuid` is generated so every restored
     * row has a stable identity going forward.
     */
    fun toInsertableEntity() = FoodEntity(
        id = 0,
        clientUuid = clientUuid ?: java.util.UUID.randomUUID().toString(),
        name = name,
        brand = brand,
        category = category,
        servingGrams = servingGrams,
        servingLabel = servingLabel,
        oilGrams = oilGrams,
        oilDefault = oilDefault,
        servingVariants = servingVariants,
        barcode = barcode,
        isCustom = isCustom,
        isFavourite = isFavourite,
        isHidden = isHidden,
        calories = calories,
        carbs = carbs,
        sugar = sugar,
        fiber = fiber,
        protein = protein,
        fat = fat,
        saturatedFat = saturatedFat,
        omega3 = omega3,
        cholesterol = cholesterol,
        sodium = sodium,
        calcium = calcium,
        iron = iron,
        magnesium = magnesium,
        potassium = potassium,
        zinc = zinc,
        phosphorus = phosphorus,
        selenium = selenium,
        copper = copper,
        manganese = manganese,
        vitaminA = vitaminA,
        vitaminC = vitaminC,
        vitaminD = vitaminD,
        vitaminE = vitaminE,
        vitaminK = vitaminK,
        vitaminB12 = vitaminB12,
        folate = folate,
        vitaminB1 = vitaminB1,
        vitaminB2 = vitaminB2,
        vitaminB3 = vitaminB3,
        vitaminB6 = vitaminB6,
        solubleFiber = solubleFiber,
        insolubleFiber = insolubleFiber,
        essentialAminoAcids = essentialAminoAcids,
        leucine = leucine,
    )

    companion object {
        fun from(e: FoodEntity) = FoodSnapshot(
            id = e.id,
            clientUuid = e.clientUuid,
            name = e.name,
            brand = e.brand,
            category = e.category,
            servingGrams = e.servingGrams,
            servingLabel = e.servingLabel,
            oilGrams = e.oilGrams,
            oilDefault = e.oilDefault,
            servingVariants = e.servingVariants,
            barcode = e.barcode,
            isCustom = e.isCustom,
            isFavourite = e.isFavourite,
            isHidden = e.isHidden,
            calories = e.calories,
            carbs = e.carbs,
            sugar = e.sugar,
            fiber = e.fiber,
            protein = e.protein,
            fat = e.fat,
            saturatedFat = e.saturatedFat,
            omega3 = e.omega3,
            cholesterol = e.cholesterol,
            sodium = e.sodium,
            calcium = e.calcium,
            iron = e.iron,
            magnesium = e.magnesium,
            potassium = e.potassium,
            zinc = e.zinc,
            phosphorus = e.phosphorus,
            selenium = e.selenium,
            copper = e.copper,
            manganese = e.manganese,
            vitaminA = e.vitaminA,
            vitaminC = e.vitaminC,
            vitaminD = e.vitaminD,
            vitaminE = e.vitaminE,
            vitaminK = e.vitaminK,
            vitaminB12 = e.vitaminB12,
            folate = e.folate,
            vitaminB1 = e.vitaminB1,
            vitaminB2 = e.vitaminB2,
            vitaminB3 = e.vitaminB3,
            vitaminB6 = e.vitaminB6,
            solubleFiber = e.solubleFiber,
            insolubleFiber = e.insolubleFiber,
            essentialAminoAcids = e.essentialAminoAcids,
            leucine = e.leucine,
        )
    }
}

@Serializable
data class FoodLogSnapshot(
    val id: Long,
    /** Source-device local id. Only useful as a fallback against legacy `foods`. */
    val foodId: Long,
    /**
     * Portable identity for the referenced food.
     *
     * Set for seeded foods as well as customs since the seed pinned literal
     * uuids: a seeded row's uuid is now the same string on every install, so it
     * identifies the food across a restore even if the food has since been
     * renamed. It was custom-only when seeded uuids were minted randomly per
     * install and carried no meaning off the device that wrote them.
     */
    val foodClientUuid: String? = null,
    /**
     * Portable identity fallback — resolves the food by name.
     *
     * Still written, and still the only key that works for a snapshot taken
     * before the uuids were pinned, which is why the restore path keeps trying
     * it. Those payloads carry no seeded uuid and no update to this app can put
     * one in them.
     */
    val foodName: String = "",
    val dateEpochDay: Long,
    val mealType: String,
    val servings: Double,
    val grams: Double,
    val servingLabel: String = "",
    /** Cooking oil recorded on this entry, or null for the food's default. */
    val oil: String? = null,
    /** Style option recorded on this entry, or null for the food's default. */
    val style: String? = null,
    /** Oil amount recorded on this entry, or null for the food's own. */
    val oilAmount: String? = null,
    val loggedAtMillis: Long,
) {
    /** Local id is supplied by the caller after food lookup; mealType etc. are preserved. */
    fun toEntity(localFoodId: Long) = FoodLogEntity(
        id = 0,
        foodId = localFoodId,
        dateEpochDay = dateEpochDay,
        mealType = mealType,
        servings = servings,
        grams = grams,
        servingLabel = servingLabel,
        oil = oil,
        style = style,
        oilAmount = oilAmount,
        loggedAtMillis = loggedAtMillis,
    )

    companion object {
        fun from(e: FoodLogEntity, food: FoodEntity?) = FoodLogSnapshot(
            id = e.id,
            foodId = e.foodId,
            foodClientUuid = food?.clientUuid,
            foodName = food?.name ?: "",
            dateEpochDay = e.dateEpochDay,
            mealType = e.mealType,
            servings = e.servings,
            grams = e.grams,
            servingLabel = e.servingLabel,
            oil = e.oil,
            style = e.style,
            oilAmount = e.oilAmount,
            loggedAtMillis = e.loggedAtMillis,
        )
    }
}

/**
 * One planned line, made portable the same way a log entry is: by carrying the
 * referenced food's `clientUuid` (for customs) and its name (for seeded rows)
 * rather than the local Room id, which re-numbers on a fresh install. A plan
 * restored by id would point every line at whatever food happened to land on
 * that row number.
 */
@Serializable
data class PlanItemSnapshot(
    val id: Long,
    /** Source-device local id. Only useful as a fallback against legacy `foods`. */
    val foodId: Long,
    /** Portable identity for the referenced food, seeded or custom. */
    val foodClientUuid: String? = null,
    /** Portable identity fallback, for payloads predating the pinned seed uuids. */
    val foodName: String = "",
    val mealType: String,
    val servings: Double,
    val grams: Double,
    val servingLabel: String = "",
    val oil: String? = null,
    val style: String? = null,
    val oilAmount: String? = null,
    val sortIndex: Int = 0,
) {
    fun toEntity(localFoodId: Long) = PlanItemEntity(
        id = 0,
        foodId = localFoodId,
        mealType = mealType,
        servings = servings,
        grams = grams,
        servingLabel = servingLabel,
        oil = oil,
        style = style,
        oilAmount = oilAmount,
        sortIndex = sortIndex,
    )

    companion object {
        fun from(e: PlanItemEntity, food: FoodEntity?) = PlanItemSnapshot(
            id = e.id,
            foodId = e.foodId,
            foodClientUuid = food?.clientUuid,
            foodName = food?.name ?: "",
            mealType = e.mealType,
            servings = e.servings,
            grams = e.grams,
            servingLabel = e.servingLabel,
            oil = e.oil,
            style = e.style,
            oilAmount = e.oilAmount,
            sortIndex = e.sortIndex,
        )
    }
}

@Serializable
data class WaterLogSnapshot(
    val id: Long,
    val dateEpochDay: Long,
    val amountMl: Int,
    val loggedAtMillis: Long,
) {
    fun toEntity() = WaterLogEntity(
        id = id,
        dateEpochDay = dateEpochDay,
        amountMl = amountMl,
        loggedAtMillis = loggedAtMillis,
    )

    companion object {
        fun from(e: WaterLogEntity) = WaterLogSnapshot(
            id = e.id,
            dateEpochDay = e.dateEpochDay,
            amountMl = e.amountMl,
            loggedAtMillis = e.loggedAtMillis,
        )
    }
}

@Serializable
data class WeightLogSnapshot(
    val id: Long,
    val dateEpochDay: Long,
    val weightKg: Double,
    val note: String? = null,
    val loggedAtMillis: Long,
) {
    fun toEntity() = WeightLogEntity(
        id = id,
        dateEpochDay = dateEpochDay,
        weightKg = weightKg,
        note = note,
        loggedAtMillis = loggedAtMillis,
    )

    companion object {
        fun from(e: WeightLogEntity) = WeightLogSnapshot(
            id = e.id,
            dateEpochDay = e.dateEpochDay,
            weightKg = e.weightKg,
            note = e.note,
            loggedAtMillis = e.loggedAtMillis,
        )
    }
}
