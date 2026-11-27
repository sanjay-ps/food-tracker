package com.example.foodtracker.ui.screens.barcode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodtracker.data.BarcodeProduct
import com.example.foodtracker.data.BarcodeSource
import com.example.foodtracker.data.FoodRepository
import com.example.foodtracker.data.MealType
import com.example.foodtracker.data.NutritionBasis
import com.example.foodtracker.data.db.FoodEntity
import com.example.foodtracker.data.db.FoodLogEntity
import com.example.foodtracker.util.DateUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BarcodeUiState(
    val scanning: Boolean = true,
    val lastBarcode: String? = null,
    /** Fresh hit from OFF/USDA — both bases available, user picks via [basis]. */
    val product: BarcodeProduct? = null,
    /** Stale cached row from the local DB; shown only when every online source missed. */
    val cached: FoodEntity? = null,
    val resolvedSource: BarcodeSource? = null,
    val basis: NutritionBasis = NutritionBasis.Per100g,
    val notFound: Boolean = false,
    val error: String? = null,
    val isLoading: Boolean = false,
) {
    /** True once a scan has produced something to log. */
    val resolved: Boolean get() = product != null || cached != null
}

/**
 * The resolved product as a food the serving sheet can price.
 *
 * **This row is deliberately not saved.** It is built in memory, at the user's
 * currently chosen basis, purely so the standard sheet has a [FoodEntity] to
 * work from; the insert happens in [BarcodeViewModel.logResolved] when the user
 * actually commits. Persisting on resolve would put a row in the user's Custom
 * foods list for every packet they pointed the camera at and then backed out
 * of, and — because the basis is still editable at that point — would have to
 * be rewritten on every basis tap.
 *
 * The caller must `remember` the result: [FoodEntity] mints a fresh
 * `clientUuid` on construction, so rebuilding it every recomposition would
 * hand the sheet a new identity on every frame.
 */
fun BarcodeUiState.previewFood(): FoodEntity? = when {
    product != null -> FoodEntity.fromNutrientsPer100g(
        name = product.name,
        brand = product.brand,
        barcode = product.barcode,
        category = product.category,
        n = product.toPerHundredG(basis),
        servingGrams = product.servingGrams,
        servingLabel = product.servingLabel,
        isCustom = true,
    )
    else -> cached
}

class BarcodeViewModel(private val repository: FoodRepository) : ViewModel() {

    private val _state = MutableStateFlow(BarcodeUiState())
    val state: StateFlow<BarcodeUiState> = _state.asStateFlow()

    fun onBarcode(barcode: String) {
        if (!_state.value.scanning) return
        _state.update {
            it.copy(
                scanning = false,
                lastBarcode = barcode,
                isLoading = true,
                error = null,
                notFound = false,
            )
        }
        viewModelScope.launch {
            try {
                val lookup = repository.fetchByBarcode(barcode)
                when {
                    lookup.product != null -> _state.update {
                        it.copy(
                            isLoading = false,
                            product = lookup.product,
                            cached = null,
                            resolvedSource = lookup.source,
                            basis = lookup.product.defaultBasis,
                        )
                    }
                    lookup.cached != null -> _state.update {
                        it.copy(
                            isLoading = false,
                            product = null,
                            cached = lookup.cached,
                            resolvedSource = lookup.source,
                        )
                    }
                    else -> _state.update { it.copy(isLoading = false, notFound = true) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Lookup failed") }
            }
        }
    }

    fun setBasis(basis: NutritionBasis) {
        _state.update { it.copy(basis = basis) }
    }

    fun retry() {
        _state.update { BarcodeUiState() }
    }

    /**
     * Commit the scan: save the product at the chosen basis, then log the
     * portion the serving sheet composed.
     *
     * **The portion no longer rewrites the food.** The old flow pushed an
     * edited serving size back onto the `foods` row and rebuilt its label, so
     * eating half a packet redefined what a serving of that product *is* for
     * every future scan and every past entry's label. The pack's serving is a
     * fact about the pack; how much of it you ate is a fact about the entry,
     * and it belongs on the log row — which is exactly what the serving sheet
     * already produces, for packaged food and dosas alike.
     *
     * A cached hit is already in the database, so it only gets the log row.
     */
    fun logResolved(
        meal: MealType,
        grams: Double,
        servings: Double,
        servingLabel: String,
        onDone: () -> Unit,
    ) {
        val s = _state.value
        viewModelScope.launch {
            val food = when {
                s.product != null -> repository.commitBarcodeProduct(
                    product = s.product,
                    basis = s.basis,
                )
                s.cached != null -> s.cached
                else -> return@launch
            }
            repository.addFoodLog(
                FoodLogEntity(
                    foodId = food.id,
                    dateEpochDay = DateUtil.todayEpochDay(),
                    mealType = meal.name,
                    servings = servings,
                    grams = grams,
                    servingLabel = servingLabel,
                    loggedAtMillis = System.currentTimeMillis(),
                )
            )
            onDone()
        }
    }
}
