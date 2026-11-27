package com.example.foodtracker.ui.screens.log

import java.time.LocalTime
import com.example.foodtracker.domain.MealTiming
import kotlinx.coroutines.flow.first
import com.example.foodtracker.domain.GoalCalculator
import com.example.foodtracker.domain.NutrientDensity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodtracker.data.FoodRepository
import com.example.foodtracker.data.CookingOil
import com.example.foodtracker.data.StyleOption
import com.example.foodtracker.data.MealType
import com.example.foodtracker.data.OilAmount
import com.example.foodtracker.data.ProfileStore
import com.example.foodtracker.data.OnlineSearchResult
import com.example.foodtracker.data.ServingVariant
import com.example.foodtracker.data.db.FoodEntity
import com.example.foodtracker.data.db.FoodLogEntity
import com.example.foodtracker.util.DateUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Which set of foods the picker is showing. The tab picks the set; the search
 * field narrows it.
 *
 * Favourites covers what "recent/often" used to: the foods you come back to.
 * [Ranked] is every food, most nutrition-rich first (see `NutrientDensity`).
 */
enum class LogTab { Favourites, All, Ranked, Custom }

/** Sub-filter inside the Custom tab — choose between active and soft-deleted rows. */
enum class CustomFilter { Visible, Hidden }

enum class OnlineSearchPhase { Idle, Loading, Empty, Error }

data class LogUiState(
    val query: String = "",
    val tab: LogTab = LogTab.All,
    /** Foods in nutrient-density order (rank 1 first), narrowed by the query. */
    val ranked: List<FoodEntity> = emptyList(),
    val favourites: List<FoodEntity> = emptyList(),
    val all: List<FoodEntity> = emptyList(),
    val customVisible: List<FoodEntity> = emptyList(),
    val customHidden: List<FoodEntity> = emptyList(),
    val customFilter: CustomFilter = CustomFilter.Visible,
    val mealType: MealType = MealType.Breakfast,
    val selected: FoodEntity? = null,
    val selectedVariant: ServingVariant = ServingVariant.Default,
    val selectedServings: Double = 1.0,
    val selectedOil: CookingOil? = null,
    val selectedStyle: StyleOption? = null,
    val selectedOilAmount: OilAmount? = null,
    val onlineResults: List<OnlineSearchResult> = emptyList(),
    val onlinePhase: OnlineSearchPhase = OnlineSearchPhase.Idle,
    /** Every visible food, for the search placeholder and the All count; the list itself is paged. */
    val totalFoods: Int = 0,
    val rankedTotal: Int = 0,
    val moreAll: Boolean = false,
    val moreRanked: Boolean = false,
    /** True until the first lists arrive, so the grid shows placeholders rather than "no foods". */
    val loading: Boolean = false,
    /** Each food's place in the Ranked order (1 = most nutrition-rich), by id. */
    val ranks: Map<Long, Int> = emptyMap(),
) {
    /** Whether scrolling to the end of this tab should load another page. */
    val canLoadMore: Boolean
        get() = when (tab) {
            LogTab.All -> moreAll
            LogTab.Ranked -> moreRanked
            else -> false
        }

    /** The count to show above the grid: the whole tab, not just the loaded page. */
    val listCount: Int
        get() = when {
            query.isNotBlank() -> visibleList.size
            tab == LogTab.All -> maxOf(totalFoods, all.size)
            tab == LogTab.Ranked -> maxOf(rankedTotal, ranked.size)
            else -> visibleList.size
        }

    /** The list the active tab is showing, so the screen does not re-derive it. */
    val visibleList: List<FoodEntity>
        get() = when (tab) {
            LogTab.Favourites -> favourites
            LogTab.All -> all
            LogTab.Ranked -> ranked
            LogTab.Custom -> if (customFilter == CustomFilter.Hidden) customHidden else customVisible
        }
}

/**
 * Matches the DAO's `search` predicate — name or brand, substring,
 * case-insensitive.
 *
 * The four short lists are filtered here rather than in SQL because they are
 * already in memory and at most a few dozen rows: a round trip to Room per
 * keystroke, per tab, to re-filter twelve recent foods would be slower than the
 * filter. Only the All and Ranked tabs, which page through the 500-food
 * catalogue (and are `LIMIT 100` in SQL when searching), stay queries.
 */
private fun List<FoodEntity>.matching(query: String): List<FoodEntity> {
    if (query.isBlank()) return this
    val q = query.trim()
    return filter {
        it.name.contains(q, ignoreCase = true) || it.brand?.contains(q, ignoreCase = true) == true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LogFoodViewModel(
    private val repository: FoodRepository,
    private val profileStore: ProfileStore,
    initialMeal: MealType = MealType.Breakfast,
    /** The day entries go into. */
    val day: Long = DateUtil.todayEpochDay(),
) : ViewModel() {

    /** A time the user picked for the entry being composed; null = the default for the meal. */
    val eatenAtOverride = MutableStateFlow<LocalTime?>(null)

    /** When the entry being composed was eaten — the user's pick, else the meal's default. */
    fun eatenAt(meal: MealType): LocalTime = eatenAtOverride.value ?: MealTiming.defaultFor(
        meal, day, DateUtil.todayEpochDay(), LocalTime.now(), MealType.currentForTime(),
    )

    private val _query = MutableStateFlow("")

    /**
     * The search field's text, as Compose state: it updates in the same frame
     * as the keystroke. Reading the field back from [state] (a flow round trip)
     * dropped and reordered characters when typing fast.
     */
    var queryText by mutableStateOf("")
        private set
    private val _tab = MutableStateFlow(LogTab.All)

    /**
     * Where the picker opened, decided once: Favourites when there are at
     * least [FAVS_TO_LAND] of them, otherwise All. Null until decided. The
     * screen focuses the search field when this is All.
     */
    val landing = MutableStateFlow<LogTab?>(null)
    private val _customFilter = MutableStateFlow(CustomFilter.Visible)
    private val _mealType = MutableStateFlow(initialMeal)
    private val _selected = MutableStateFlow<FoodEntity?>(null)
    private val _variant = MutableStateFlow(ServingVariant.Default)
    private val _servings = MutableStateFlow(1.0)
    /** Oil chosen for the entry being composed. Null = the food's own default. */
    private val _oil = MutableStateFlow<CookingOil?>(null)
    private val _style = MutableStateFlow<StyleOption?>(null)
    private val _oilAmount = MutableStateFlow<OilAmount?>(null)
    private val _onlineResults = MutableStateFlow<List<OnlineSearchResult>>(emptyList())
    private val _onlinePhase = MutableStateFlow(OnlineSearchPhase.Idle)

    /**
     * Rows loaded so far on the All and Ranked tabs. Each starts at [PAGE] and
     * grows by a page when the grid nears its end ([loadMore]), so opening the
     * picker reads 40 foods, not 500. A search is already `LIMIT 100` in SQL.
     */
    private val _allLimit = MutableStateFlow(PAGE)
    private val _rankedLimit = MutableStateFlow(PAGE)

    private data class Pages(
        val all: List<FoodEntity>,
        val ranked: List<FoodEntity>,
        val total: Int,
        val moreAll: Boolean,
        val moreRanked: Boolean,
        val ranks: Map<Long, Int>,
        val rankedTotal: Int,
    )

    private data class RankedPage(val foods: List<FoodEntity>, val ranks: Map<Long, Int>, val more: Boolean, val total: Int)

    private val allPageFlow = combine(_query, _allLimit) { q, lim -> q to lim }.flatMapLatest { (q, lim) ->
        if (q.isBlank()) repository.observeFoodsPage(lim).map { it to (it.size >= lim) }
        else repository.searchFoods(q).map { it to false }
    }

    /**
     * Every visible food, most nutrition-rich first ([NutrientDensity], against
     * the user's own targets). Scoring needs every row, so this reads the whole
     * catalogue once — off the main thread — and the tab pages through it.
     */
    private val byDensityFlow = combine(
        repository.observeAllFoods(),
        profileStore.profile.map(GoalCalculator::goalsFor),
    ) { foods, goals ->
        foods.map { it to NutrientDensity.score(it.nutrientsPer100g(), goals) }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private val rankedPageFlow = combine(_query, _rankedLimit, byDensityFlow) { q, lim, ranked ->
        RankedPage(
            foods = if (q.isBlank()) ranked.take(lim) else ranked.matching(q),
            ranks = ranked.withIndex().associate { (i, f) -> f.id to i + 1 },
            more = q.isBlank() && lim < ranked.size,
            total = ranked.size,
        )
    }

    // Built off the main thread, and only when a page or the query changes, so
    // the serving sheet's slider ticks no longer re-sort the catalogue.
    private val pagesFlow = combine(allPageFlow, rankedPageFlow, repository.observeVisibleFoodCount()) { a, r, total ->
        Pages(
            all = a.first, ranked = r.foods, total = total, moreAll = a.second, moreRanked = r.more,
            ranks = r.ranks, rankedTotal = r.total,
        )
    }.flowOn(Dispatchers.Default)
    private val favouritesFlow = repository.observeFavouriteFoods()
    private val customVisibleFlow = repository.observeCustomVisibleFoods()
    private val customHiddenFlow = repository.observeCustomHiddenFoods()


    private data class Lists(
        val favourites: List<FoodEntity>,
        val customVisible: List<FoodEntity>,
        val customHidden: List<FoodEntity>,
    )

    private val listsFlow = combine(
        favouritesFlow, customVisibleFlow, customHiddenFlow,
    ) { favourites, customVisible, customHidden ->
        Lists(favourites, customVisible, customHidden)
    }

    private data class Selection(
        val food: FoodEntity?,
        val variant: ServingVariant,
        val servings: Double,
        val oil: CookingOil?,
        val style: StyleOption?,
        val oilAmount: OilAmount?,
    )

    /**
     * The three preparation axes, folded into one flow.
     *
     * Not for tidiness: `combine`'s typed overloads stop at five sources, and
     * the selection needs six. Folding the axes that always travel together is
     * the fold that costs nothing to read.
     */
    private val prepFlow = combine(_oil, _style, _oilAmount) { o, s, a -> Triple(o, s, a) }

    private val selectionFlow = combine(
        _selected,
        _variant,
        _servings,
        prepFlow,
    ) { sel, v, s, (pickedOil, pickedStyle, pickedAmount) ->
        // Fall back to what this food remembers, so the sheet opens on the
        // preparation the user last chose rather than on nothing.
        Selection(
            food = sel,
            variant = v,
            servings = s,
            oil = sel?.let { pickedOil ?: it.preselectedOil() },
            style = sel?.let { pickedStyle ?: it.preselectedStyle() },
            oilAmount = sel?.let { pickedAmount ?: it.preselectedOilAmount() },
        )
    }

    private data class Online(
        val results: List<OnlineSearchResult>,
        val phase: OnlineSearchPhase,
    )

    private val onlineFlow = combine(_onlineResults, _onlinePhase) { r, p -> Online(r, p) }

    private data class Head(
        val query: String,
        val tab: LogTab,
        val meal: MealType,
        val customFilter: CustomFilter,
    )

    private val headFlow = combine(_query, _tab, _mealType, _customFilter) { q, t, m, cf ->
        Head(q, t, m, cf)
    }

    val state: StateFlow<LogUiState> = combine(
        headFlow,
        pagesFlow,
        listsFlow,
        selectionFlow,
        onlineFlow,
    ) { head, pages, lists, sel, online ->
        // The query narrows every tab, not just one. The All tab is already
        // filtered by SQL; the rest are filtered here against the same
        // predicate so "dos" means the same thing on every tab.
        LogUiState(
            query = head.query,
            tab = head.tab,
            ranked = pages.ranked,
            favourites = lists.favourites.matching(head.query),
            all = pages.all,
            customVisible = lists.customVisible.matching(head.query),
            customHidden = lists.customHidden.matching(head.query),
            customFilter = head.customFilter,
            mealType = head.meal,
            selected = sel.food,
            selectedVariant = sel.variant,
            selectedServings = sel.servings,
            selectedOil = sel.oil,
            selectedStyle = sel.style,
            selectedOilAmount = sel.oilAmount,
            onlineResults = online.results,
            onlinePhase = online.phase,
            totalFoods = pages.total,
            rankedTotal = pages.rankedTotal,
            ranks = pages.ranks,
            moreAll = pages.moreAll,
            moreRanked = pages.moreRanked,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        // First frame uses the constructor's initial meal — otherwise the
        // title briefly reads Breakfast (the data-class default) before the
        // combine flow's first emission lands, producing a visible "morph"
        // when the user opened Log Food from a non-Breakfast meal row.
        LogUiState(mealType = initialMeal, loading = true),
    )

    /**
     * Typing no longer moves the user to another tab.
     *
     * It used to force a jump to the Search tab, which is what made "search my
     * favourites" impossible: the moment you typed, you were not on favourites
     * any more. The tab is the user's choice about *which set*; the query is
     * their choice about *which rows*, and the two are independent.
     */
    fun setQuery(q: String) {
        val openingASearch = _query.value.isBlank() && q.isNotBlank()
        queryText = q
        _query.update { q }
        // A cleared search starts the catalogue again from its first page.
        _allLimit.value = PAGE
        _rankedLimit.value = PAGE
        // Starting to type says "it is not in this short list" — so move to the
        // one list that holds everything. See [autoAllArmed] for why this is
        // not the tab-jumping bug the doc above describes.
        if (openingASearch && autoAllArmed) _tab.update { LogTab.All }
        // Any new keystroke invalidates the previous online search.
        if (_onlinePhase.value != OnlineSearchPhase.Idle) {
            _onlineResults.update { emptyList() }
            _onlinePhase.update { OnlineSearchPhase.Idle }
        }
    }

    /**
     * Fetch results from OpenFoodFacts + USDA for the current query. Manual —
     * triggered by the "Search online" button, never automatic. Local DB
     * remains untouched until the user picks a result.
     */
    fun searchOnline() {
        val q = _query.value.trim()
        if (q.isBlank()) return
        _onlinePhase.update { OnlineSearchPhase.Loading }
        _onlineResults.update { emptyList() }
        viewModelScope.launch {
            val results = runCatching { repository.searchOnline(q) }.getOrNull()
            if (results == null) {
                _onlinePhase.update { OnlineSearchPhase.Error }
            } else {
                _onlineResults.update { results }
                _onlinePhase.update { if (results.isEmpty()) OnlineSearchPhase.Empty else OnlineSearchPhase.Idle }
            }
        }
    }

    /**
     * Persist an online-search result and immediately open the serving sheet for it.
     */
    fun pickOnline(result: OnlineSearchResult) {
        viewModelScope.launch {
            val saved = repository.saveOnlineResult(result.food)
            _selected.update { saved }
            _variant.update { saved.variantsOrDefault().firstOrNull() ?: ServingVariant.Default }
            _servings.update { 1.0 }
            _oil.value = null
            _style.value = null
            _oilAmount.value = null
            _onlineResults.update { emptyList() }
            _onlinePhase.update { OnlineSearchPhase.Idle }
        }
    }
    /**
     * Whether a first search may still move the picker to [LogTab.All].
     *
     * Favourites holds a handful of rows; All holds the whole catalogue.
     * Opening on Favourites is right when there are enough of them — the food
     * is usually right there — but it makes the *first* keystroke misleading,
     * because searching five favourites for something that is not among them
     * returns nothing and looks like the food is missing.
     *
     * This is not a return to the behaviour [setQuery]'s doc warns about. That
     * version jumped on every keystroke, from any tab, which is what made
     * "search my favourites" impossible. This one fires only on the blank →
     * non-blank edge, and only until the user touches a tab themselves: picking
     * a tab is an explicit statement about which set they want to search, and
     * it disarms the jump for good. Tapping All disarms it too, harmlessly —
     * the jump would be a no-op there anyway, and one rule is easier to hold
     * than two.
     *
     * Plain `var`, not state: nothing renders it, and it is only ever touched
     * from main-thread UI callbacks. It outlives rotation with the ViewModel,
     * which is correct — the user's choice should not be undone by a turn of
     * the phone.
     */
    private var autoAllArmed = true

    init {
        viewModelScope.launch {
            val favs = favouritesFlow.first()
            val land = if (favs.size >= FAVS_TO_LAND) LogTab.Favourites else LogTab.All
            // Only if the user has not already picked a tab or started typing.
            if (autoAllArmed && _query.value.isBlank()) _tab.value = land
            landing.value = _tab.value
        }
    }

    fun setTab(tab: LogTab) {
        autoAllArmed = false
        _tab.update { tab }
    }
    fun setCustomFilter(filter: CustomFilter) = _customFilter.update { filter }

    /** Loads the next page of the current tab; the screen calls this as the grid nears its end. */
    fun loadMore() {
        when (state.value.tab) {
            LogTab.All -> _allLimit.update { it + PAGE }
            LogTab.Ranked -> _rankedLimit.update { it + PAGE }
            else -> Unit
        }
    }
    fun setMealType(type: MealType) = _mealType.update { type }
    fun select(food: FoodEntity?) {
        eatenAtOverride.value = null
        _oil.value = null
        _style.value = null
        _oilAmount.value = null
        _selected.update { food }
        _variant.update { food?.variantsOrDefault()?.firstOrNull() ?: ServingVariant.Default }
        _servings.update { 1.0 }
    }
    fun setVariant(variant: ServingVariant) = _variant.update { variant }

    fun setOil(oil: CookingOil) {
        _oil.update { oil }
        rememberVariant()
    }

    fun setStyle(style: StyleOption) {
        _style.update { style }
        rememberVariant()
    }

    fun setOilAmount(amount: OilAmount) {
        _oilAmount.update { amount }
        rememberVariant()
    }

    /**
     * Write the current picks back to the food so the next visit opens on
     * them. Fired on selection, not on log — see [FoodDao.rememberVariant].
     */
    private fun rememberVariant() {
        val food = _selected.value ?: return
        val oil = _oil.value?.name
        val style = _style.value?.label
        val oilAmount = _oilAmount.value?.name
        viewModelScope.launch { repository.rememberVariant(food.id, oil, style, oilAmount) }
    }
    fun setServings(value: Double) = _servings.update { value.coerceAtLeast(0.0) }

    fun toggleFavourite(food: FoodEntity) {
        viewModelScope.launch {
            repository.setFoodFavourite(food.id, !food.isFavourite)
        }
    }

    /**
     * Remove a custom food from the user's catalog. [cascadeLogs] = true also
     * wipes every historical food-log entry that referenced it; false soft-hides
     * the row so the diary keeps rendering past entries.
     */
    fun deleteCustomFood(food: FoodEntity, cascadeLogs: Boolean) {
        if (!food.isCustom) return
        viewModelScope.launch {
            repository.deleteCustomFood(food.id, cascadeLogs)
        }
    }

    /** Bring a soft-deleted custom food back into the picker. */
    fun restoreCustomFood(food: FoodEntity) {
        if (!food.isCustom || !food.isHidden) return
        viewModelScope.launch {
            repository.restoreCustomFood(food.id)
        }
    }

    fun confirmLog(onDone: () -> Unit) {
        val food = _selected.value ?: return
        val variant = _variant.value
        val servings = _servings.value
        if (servings <= 0.0) return
        // Only record an oil for foods it actually applies to — a log row for
        // a banana claiming "Palmolein" would be noise in the diary and in any
        // later export.
        val oil = if (food.usesCookingOil()) state.value.selectedOil else null
        val style = if (food.hasStyleAxis()) state.value.selectedStyle else null
        val oilAmount = if (food.usesOilAmountAxis()) state.value.selectedOilAmount else null
        viewModelScope.launch {
            repository.addFoodLog(
                FoodLogEntity(
                    foodId = food.id,
                    dateEpochDay = day,
                    mealType = _mealType.value.name,
                    servings = servings,
                    grams = servings * variant.grams,
                    servingLabel = variant.label,
                    oil = oil?.name,
                    style = style?.label,
                    oilAmount = oilAmount?.name,
                    // "Eaten at": the day being logged plus the chosen time.
                    loggedAtMillis = MealTiming.toMillis(day, eatenAt(_mealType.value), DateUtil.zoneId()),
                )
            )
            _selected.update { null }
            onDone()
        }
    }
}

/** Fewer favourites than this and the picker opens on All, ready to type. */
private const val FAVS_TO_LAND = 5

/** Foods per page on the All and Ranked tabs: about three screens of the 2-column grid. */
private const val PAGE = 40
