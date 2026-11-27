package com.example.foodtracker.data.backup

import kotlinx.serialization.Serializable

/**
 * How far back a [AnalysisExport] reaches. Kept small and human-labelled so the
 * Profile UI can offer them directly.
 */
enum class AnalysisRange(val days: Int, val label: String, val fileTag: String) {
    Week(7, "Last week", "1w"),
    Month(30, "Last month", "1m"),
    ThreeMonths(90, "Last 3 months", "3m"),
}

/**
 * A deliberately small, self-describing snapshot of the user's *food logs* over
 * a date range, shaped for feeding into an LLM ("analyse my eating habits").
 *
 * Size is kept down two ways:
 *  1. **Foods are referenced, not repeated.** Each distinct food appears once in
 *     [foods] (keyed by a short id); every entry in [logs] points at it by id.
 *     A food eaten 30 times costs one [AnalysisFood] + 30 tiny [AnalysisLog]s.
 *  2. **Zero nutrients are omitted.** [AnalysisFood.per100g] only lists nutrients
 *     the food actually contains, and values are rounded.
 *
 * This is NOT the backup format ([BackupPayload]) — it is lossy on purpose
 * (no ids/uuids/serving-variant metadata, rounded numbers) and is never
 * imported back. It exists solely to hand to an external AI.
 */
@Serializable
data class AnalysisExport(
    val app: String,
    val schema: String,
    val exportedOn: String,
    /** Plain-English description of the structure so any AI reads it correctly. */
    val readme: String,
    val range: AnalysisRangeInfo,
    /** Nutrient key → unit (e.g. "protein" → "g", "sodium" → "mg"). */
    val units: Map<String, String>,
    val profile: AnalysisProfile,
    /** The user's daily targets, for "am I on track / what to change" analysis. */
    val dailyGoals: Map<String, Double>,
    /** Distinct foods referenced by [logs], keyed by the id used in each log. */
    val foods: Map<String, AnalysisFood>,
    val logs: List<AnalysisLog>,
)

@Serializable
data class AnalysisRangeInfo(
    val label: String,
    val startDate: String,
    val endDate: String,
    val days: Int,
    val entries: Int,
    val distinctFoods: Int,
)

@Serializable
data class AnalysisProfile(
    val sex: String,
    val ageYears: Int,
    val heightCm: Int,
    val weightKg: Double,
    val goal: String,
    val activityLevel: String,
    val goalWeightKg: Double? = null,
)

@Serializable
data class AnalysisFood(
    val name: String,
    val category: String,
    val brand: String? = null,
    /** True only for user-added foods; omitted (⇒ built-in) otherwise. Provenance
     *  doesn't affect analysis, so false is dropped to save space. */
    val custom: Boolean = false,
    /** Per-100 g nutrients; zero-valued nutrients are omitted. Units are in [AnalysisExport.units]. */
    val per100g: Map<String, Double>,
)

@Serializable
data class AnalysisLog(
    /** Date, ISO yyyy-MM-dd. */
    val d: String,
    val meal: String,
    /** Key into [AnalysisExport.foods]. */
    val food: String,
    /** Grams eaten. Multiply the food's per-100 g values by g/100 for this entry. */
    val g: Double,
)
