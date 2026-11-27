package com.example.foodtracker.ui.screens.addfood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodtracker.data.AiModel
import com.example.foodtracker.data.AiNutrition
import com.example.foodtracker.data.AiProvider
import com.example.foodtracker.data.FoodCategory
import com.example.foodtracker.data.FoodRepository
import com.example.foodtracker.data.NutritionBasis
import com.example.foodtracker.data.Nutrients
import com.example.foodtracker.data.ProfileStore
import com.example.foodtracker.data.ServingVariant
import com.example.foodtracker.data.db.FoodEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddFoodFormState(
    val name: String = "",
    val brand: String = "",
    val barcode: String? = null,
    val category: FoodCategory = FoodCategory.Custom,
    val servingGrams: String = "100",
    val servingLabel: String = "1 serving (100 g)",
    /**
     * Named sizes for this food, when it has them — from the AI autofill or
     * carried over from a scanned product. Empty is the common case and lets
     * [FoodEntity.fromNutrientsPer100g] build its usual defaults on save.
     */
    val servingVariants: List<ServingVariant> = emptyList(),
    /** Which basis the user is entering values in; we scale to per-100g on save. */
    val basis: NutritionBasis = NutritionBasis.Per100g,
    val calories: String = "",
    val carbs: String = "",
    val sugar: String = "",
    val fiber: String = "",
    val protein: String = "",
    val fat: String = "",
    val saturatedFat: String = "",
    val omega3: String = "",
    val sodium: String = "",
    val cholesterol: String = "",
    val calcium: String = "",
    val iron: String = "",
    val magnesium: String = "",
    val potassium: String = "",
    val zinc: String = "",
    val phosphorus: String = "",
    val selenium: String = "",
    val copper: String = "",
    val manganese: String = "",
    val vitaminA: String = "",
    val vitaminC: String = "",
    val vitaminD: String = "",
    val vitaminE: String = "",
    val vitaminK: String = "",
    val vitaminB12: String = "",
    val folate: String = "",
    val vitaminB1: String = "",
    val vitaminB2: String = "",
    val vitaminB3: String = "",
    val vitaminB6: String = "",
    // Optional detail. Blank is the normal state: most foods have no
    // published breakdown, and a blank saves as 0, which reads as unknown.
    val solubleFiber: String = "",
    val insolubleFiber: String = "",
    val essentialAminoAcids: String = "",
    val leucine: String = "",
    val saved: Boolean = false,
    /** Whether the "Ask AI" affordance is available (global setting is on). */
    val aiEnabled: Boolean = false,
    /** Current stage of the two-pass AI autofill (Idle when not running). */
    val aiStep: AiStep = AiStep.Idle,
    /** Whether this run includes the verify pass (skipped for premium models). */
    val aiVerifies: Boolean = true,
    val aiError: String? = null,
    /**
     * The provider and model this screen would use, mirrored out of settings so
     * the model can be read — and changed — without leaving the form. The
     * provider is shown but not switchable here: a provider needs its own API
     * key, so changing it mid-lookup would just fail differently.
     */
    val aiProvider: AiProvider = AiProvider.Gemini,
    val aiModel: AiModel = AiProvider.Gemini.defaultModel,
    /**
     * When the current lookup started, for the elapsed reading in the progress
     * dialog. Zero while idle. Wall clock, not uptime: it is only ever
     * subtracted from another wall-clock reading taken seconds later.
     */
    val aiStartedAt: Long = 0L,
)

/** Stages of the AI autofill, surfaced as an animated progress dialog. */
enum class AiStep { Idle, Fetching, Verifying, Done }

class AddFoodViewModel(
    private val repository: FoodRepository,
    private val profileStore: ProfileStore,
) : ViewModel() {

    private val _state = MutableStateFlow(AddFoodFormState())
    val state: StateFlow<AddFoodFormState> = _state.asStateFlow()

    /** The running lookup, so [cancelAi] can stop one that is taking too long. */
    private var aiJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                profileStore.aiEnabled,
                profileStore.aiProvider,
                profileStore.aiModelId,
            ) { enabled, provider, modelId ->
                Triple(enabled, provider, provider.modelFromId(modelId))
            }.collect { (enabled, provider, model) ->
                _state.update {
                    it.copy(aiEnabled = enabled, aiProvider = provider, aiModel = model)
                }
            }
        }
    }

    /**
     * Switch the model the next lookup uses.
     *
     * Written straight through to settings rather than held as a form-local
     * override: a model picked here because the cheap one is rate-limited is a
     * model the user wants next time too, and two sources of truth for one
     * setting is how the add-food screen and the settings sheet end up
     * disagreeing about which model is selected.
     */
    fun setAiModel(model: AiModel) {
        viewModelScope.launch { profileStore.setAiModel(model.id) }
    }

    /**
     * Abandon a lookup in flight.
     *
     * The dialog used to be uncancelable, which assumed the request always
     * ends. It does end — but three attempts against a provider that is
     * shedding load is up to three minutes of a modal the user cannot leave,
     * and the fix for that case is to pick another model, which they cannot
     * reach while it is up. Anything the first pass already filled in stays:
     * it is in the form, not in the job.
     */
    fun cancelAi() {
        aiJob?.cancel()
        aiJob = null
        _state.update { it.copy(aiStep = AiStep.Idle, aiStartedAt = 0L, aiError = null) }
    }

    fun update(transform: (AddFoodFormState) -> AddFoodFormState) {
        _state.update(transform)
    }

    /**
     * Two-pass AI autofill for the food the user named:
     *  1. Fetch — an AI proposes a typical serving + per-serving nutrition, which
     *     is prefilled into the form immediately.
     *  2. Verify — a second AI pass fact-checks and corrects those values; if it
     *     succeeds the form is updated with the verified numbers, otherwise the
     *     prefilled values are kept.
     * Progress is surfaced via [AddFoodFormState.aiStep]. The form stays fully
     * editable throughout. No-op unless the user typed a name / AI is enabled.
     */
    fun autofillFromAi(instructions: String = "") {
        val name = _state.value.name.trim()
        val brand = _state.value.brand.trim()
        if (name.isBlank() || _state.value.aiStep != AiStep.Idle) return
        _state.update {
            it.copy(
                aiStep = AiStep.Fetching,
                aiError = null,
                aiStartedAt = System.currentTimeMillis(),
            )
        }
        aiJob = viewModelScope.launch {
            val provider = profileStore.aiProvider.first()
            val key = profileStore.aiApiKey.first()
            val aiModel = provider.modelFromId(profileStore.aiModelId.first())
            val model = aiModel.id
            // Premium models are strong enough on the first pass — skip the
            // extra verify call to save cost/latency.
            val verifies = !aiModel.premium
            if (key.isNullOrBlank()) {
                _state.update {
                    it.copy(
                        aiStep = AiStep.Idle,
                        aiStartedAt = 0L,
                        aiError = "Add your ${provider.displayName} API key in Settings first.",
                    )
                }
                return@launch
            }
            _state.update { it.copy(aiVerifies = verifies) }

            val first = repository.fetchAiNutrition(name, brand, instructions, provider, model, key)
            if (first == null) {
                _state.update {
                    it.copy(
                        aiStep = AiStep.Idle,
                        aiStartedAt = 0L,
                        aiError = repository.lastAiError()
                            ?: "Couldn't fetch nutrition. Check your key and connection, then try again.",
                    )
                }
                return@launch
            }
            // Prefill immediately, then run the verification pass unless skipped.
            applyResult(first)

            if (verifies) {
                _state.update { it.copy(aiStep = AiStep.Verifying) }
                val verified =
                    repository.verifyAiNutrition(name, brand, first, instructions, provider, model, key)
                if (verified != null) applyResult(verified)
            }

            // Brief "done" beat so the success animation reads before dismissing.
            _state.update { it.copy(aiStep = AiStep.Done) }
            delay(900)
            _state.update { it.copy(aiStep = AiStep.Idle, aiStartedAt = 0L) }
            aiJob = null
        }
    }

    /** Write an AI result into the form on the per-serving basis. */
    private fun applyResult(result: AiNutrition) {
        val n = result.nutrients
        _state.update { s ->
            s.copy(
                aiError = null,
                // AI returns a typical serving + per-serving values; set the
                // serving and enter values on the PerServing basis so the save
                // path scales them to per-100 g for storage.
                category = result.category,
                servingGrams = result.servingGrams.formatGrams(),
                servingLabel = result.servingLabel,
                servingVariants = result.servingVariants,
                basis = NutritionBasis.PerServing,
                calories = n.calories.formatField(),
                carbs = n.carbs.formatField(),
                sugar = n.sugar.formatField(),
                fiber = n.fiber.formatField(),
                protein = n.protein.formatField(),
                fat = n.fat.formatField(),
                saturatedFat = n.saturatedFat.formatField(),
                omega3 = n.omega3.formatField(),
                sodium = n.sodium.formatField(),
                cholesterol = n.cholesterol.formatField(),
                calcium = n.calcium.formatField(),
                iron = n.iron.formatField(),
                magnesium = n.magnesium.formatField(),
                potassium = n.potassium.formatField(),
                zinc = n.zinc.formatField(),
                phosphorus = n.phosphorus.formatField(),
                selenium = n.selenium.formatField(),
                copper = n.copper.formatField(),
                manganese = n.manganese.formatField(),
                vitaminA = n.vitaminA.formatField(),
                vitaminC = n.vitaminC.formatField(),
                vitaminD = n.vitaminD.formatField(),
                vitaminE = n.vitaminE.formatField(),
                vitaminK = n.vitaminK.formatField(),
                vitaminB12 = n.vitaminB12.formatField(),
                folate = n.folate.formatField(),
                vitaminB1 = n.vitaminB1.formatField(),
                vitaminB2 = n.vitaminB2.formatField(),
                vitaminB3 = n.vitaminB3.formatField(),
                vitaminB6 = n.vitaminB6.formatField(),
                solubleFiber = n.solubleFiber.formatField(),
                insolubleFiber = n.insolubleFiber.formatField(),
                essentialAminoAcids = n.essentialAminoAcids.formatField(),
                leucine = n.leucine.formatField(),
            )
        }
    }

    /** Pre-fill just the barcode field — the rest of the form starts blank for manual entry. */
    fun seedBarcode(barcode: String) {
        _state.update { it.copy(barcode = barcode, category = FoodCategory.Packaged) }
    }

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) return
        val servingGrams = s.servingGrams.toDoubleOrNull()?.takeIf { it > 0 } ?: 100.0
        val servingLabel = s.servingLabel.ifBlank { "1 serving (${servingGrams.toInt()} g)" }
        val entered = Nutrients(
            calories = s.calories.toDoubleOrZero(),
            carbs = s.carbs.toDoubleOrZero(),
            sugar = s.sugar.toDoubleOrZero(),
            fiber = s.fiber.toDoubleOrZero(),
            protein = s.protein.toDoubleOrZero(),
            fat = s.fat.toDoubleOrZero(),
            saturatedFat = s.saturatedFat.toDoubleOrZero(),
            omega3 = s.omega3.toDoubleOrZero(),
            sodium = s.sodium.toDoubleOrZero(),
            cholesterol = s.cholesterol.toDoubleOrZero(),
            calcium = s.calcium.toDoubleOrZero(),
            iron = s.iron.toDoubleOrZero(),
            magnesium = s.magnesium.toDoubleOrZero(),
            potassium = s.potassium.toDoubleOrZero(),
            zinc = s.zinc.toDoubleOrZero(),
            phosphorus = s.phosphorus.toDoubleOrZero(),
            selenium = s.selenium.toDoubleOrZero(),
            copper = s.copper.toDoubleOrZero(),
            manganese = s.manganese.toDoubleOrZero(),
            vitaminA = s.vitaminA.toDoubleOrZero(),
            vitaminC = s.vitaminC.toDoubleOrZero(),
            vitaminD = s.vitaminD.toDoubleOrZero(),
            vitaminE = s.vitaminE.toDoubleOrZero(),
            vitaminK = s.vitaminK.toDoubleOrZero(),
            vitaminB12 = s.vitaminB12.toDoubleOrZero(),
            folate = s.folate.toDoubleOrZero(),
            vitaminB1 = s.vitaminB1.toDoubleOrZero(),
            vitaminB2 = s.vitaminB2.toDoubleOrZero(),
            vitaminB3 = s.vitaminB3.toDoubleOrZero(),
            vitaminB6 = s.vitaminB6.toDoubleOrZero(),
            solubleFiber = s.solubleFiber.toDoubleOrZero(),
            insolubleFiber = s.insolubleFiber.toDoubleOrZero(),
            essentialAminoAcids = s.essentialAminoAcids.toDoubleOrZero(),
            leucine = s.leucine.toDoubleOrZero(),
        )
        // Storage is always per-100g — scale up when the user typed in per-serving.
        val per100g = when (s.basis) {
            NutritionBasis.Per100g -> entered
            NutritionBasis.PerServing -> entered * (100.0 / servingGrams)
        }
        viewModelScope.launch {
            repository.upsertFood(
                FoodEntity.fromNutrientsPer100g(
                    name = s.name.trim(),
                    brand = s.brand.trim().takeIf { it.isNotBlank() },
                    barcode = s.barcode?.takeIf { it.isNotBlank() },
                    category = s.category.name,
                    isCustom = true,
                    servingGrams = servingGrams,
                    servingLabel = servingLabel,
                    // Re-sanitized against the serving the user may have edited
                    // since the AI filled it in; [] falls back to the defaults.
                    servingVariants = ServingVariant.sanitize(
                        s.servingVariants,
                        servingGrams,
                        servingLabel,
                    ),
                    n = per100g,
                )
            )
            _state.update { it.copy(saved = true) }
        }
    }
}

private fun String.toDoubleOrZero(): Double = this.toDoubleOrNull() ?: 0.0
private fun Double.formatGrams(): String =
    if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(this)
private fun Double.formatField(): String =
    if (this == 0.0) "" else if (this >= 100) this.toInt().toString() else "%.2f".format(this)
