package com.example.foodtracker.data

import com.example.foodtracker.data.db.DailyWaterTotal
import com.example.foodtracker.data.db.FoodDao
import com.example.foodtracker.data.db.FoodEntity
import java.util.Locale
import com.example.foodtracker.data.db.FoodLogDao
import com.example.foodtracker.data.db.FoodLogEntity
import com.example.foodtracker.data.db.FoodLogWithFood
import com.example.foodtracker.data.db.IndianFoodSeed
import com.example.foodtracker.data.db.PlanDao
import com.example.foodtracker.data.db.PlanItemEntity
import com.example.foodtracker.data.db.PlanItemWithFood
import com.example.foodtracker.data.db.WaterLogDao
import com.example.foodtracker.data.db.WaterLogEntity
import com.example.foodtracker.data.db.WeightLogDao
import com.example.foodtracker.data.db.WeightLogEntity
import com.example.foodtracker.util.DateUtil
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

/** Source identifier for online search results — shown to the user as a chip. */
enum class OnlineSource(val display: String) {
    OpenFoodFacts("OpenFoodFacts"),
    USDA("USDA");
}

/** A search hit annotated with the API it came from. */
data class OnlineSearchResult(val food: FoodEntity, val source: OnlineSource)

/** Where a barcode hit came from, so we can show it on the resolve panel. */
enum class BarcodeSource(val display: String) {
    OpenFoodFacts("OpenFoodFacts"),
    USDA("USDA"),
    Cached("Local cache"),
}

/**
 * Result of a barcode lookup attempt. Exactly one of [product] / [cached] is set
 * when found:
 *   - [product]: fresh hit from OFF/USDA that still has both bases — UI lets the
 *     user pick which to trust before saving.
 *   - [cached]: prior per-100g row from the local DB, surfaced only when every
 *     online source missed. Already normalised so no basis chip is needed.
 * Both null means the code wasn't recognised — fall through to manual entry.
 */
data class BarcodeLookup(
    val product: BarcodeProduct? = null,
    val cached: FoodEntity? = null,
    val source: BarcodeSource? = null,
)

class FoodRepository(
    private val foodDao: FoodDao,
    private val foodLogDao: FoodLogDao,
    private val waterLogDao: WaterLogDao,
    private val weightLogDao: WeightLogDao,
    private val planDao: PlanDao,
    private val api: OpenFoodFactsApi,
    private val usdaApi: UsdaFoodApi,
    private val aiApi: AiNutritionApi = AiNutritionApi(),
    /**
     * Called after any write that changes what the home-screen widgets show
     * (today's food log / activity). Wired in [com.example.foodtracker.FoodTrackerApp]
     * to push a [com.example.foodtracker.widget.WidgetUpdater] refresh. Kept as a
     * plain lambda so the data layer stays decoupled from the widget/UI layer.
     */
    private val onWidgetDataChanged: suspend () -> Unit = {},
) {

    // ----- Foods -----
    fun observeAllFoods(): Flow<List<FoodEntity>> = foodDao.observeAll()
    fun observeFoodsPage(limit: Int): Flow<List<FoodEntity>> = foodDao.observeAllPaged(limit)
    fun observeVisibleFoodCount(): Flow<Int> = foodDao.observeVisibleCount()
    fun searchFoods(query: String): Flow<List<FoodEntity>> = foodDao.search(query)
    fun observeFavouriteFoods(): Flow<List<FoodEntity>> = foodDao.observeFavourites()
    suspend fun upsertFood(food: FoodEntity): Long = foodDao.upsert(food)

    /**
     * Opt-in AI nutrition lookup for the add-food form. Delegates to
     * [AiNutritionApi]; returns per-100 g [Nutrients] or null on any failure.
     * Only called when the user has enabled AI and supplied their own key.
     */
    suspend fun fetchAiNutrition(
        name: String,
        brand: String,
        instructions: String,
        provider: AiProvider,
        model: String,
        apiKey: String,
    ): AiNutrition? = aiApi.fetchNutrition(name, brand, instructions, provider, model, apiKey)

    /** Reason for the most recent AI failure, or null. See [AiNutritionApi.lastError]. */
    fun lastAiError(): String? = aiApi.lastError

    /** Second-pass verification of a prior AI result. See [AiNutritionApi.verifyNutrition]. */
    suspend fun verifyAiNutrition(
        name: String,
        brand: String,
        candidate: AiNutrition,
        instructions: String,
        provider: AiProvider,
        model: String,
        apiKey: String,
    ): AiNutrition? =
        aiApi.verifyNutrition(name, brand, candidate, instructions, provider, model, apiKey)
    suspend fun setFoodFavourite(id: Long, favourite: Boolean) = foodDao.setFavourite(id, favourite)

    /** Persist the oil / style the user last chose for a food. See the DAO. */
    suspend fun rememberVariant(foodId: Long, oil: String?, style: String?, oilAmount: String?) =
        foodDao.rememberVariant(foodId, oil, style, oilAmount)
    suspend fun deleteFood(id: Long) = foodDao.delete(id)

    /**
     * Delete a single custom food the user added. Two modes:
     *  - [cascadeLogs] = true → hard-delete the food row. FK CASCADE on
     *    `food_log.food_id` wipes every historical log that referenced it.
     *  - [cascadeLogs] = false → soft-delete (set is_hidden = 1). The food no
     *    longer surfaces in pick lists, but historical logs still resolve to it
     *    so the diary keeps rendering correctly.
     */
    suspend fun deleteCustomFood(id: Long, cascadeLogs: Boolean) {
        if (cascadeLogs) {
            // FK CASCADE takes the plan lines with the food row.
            foodDao.delete(id)
        } else {
            // A hidden food leaves the picker, so it has to leave the plan too:
            // a planned line the user can no longer add is a row that can only
            // disappoint. Dropped rather than kept-and-filtered, so restoring
            // the food later does not silently put a line back into a plan the
            // user has since rebuilt without it.
            planDao.deleteForFood(id)
            foodDao.hide(id)
        }
    }

    /** Bulk version of [deleteCustomFood] — affects every `is_custom = 1` row. */
    suspend fun deleteAllCustomFoods(cascadeLogs: Boolean) {
        if (cascadeLogs) {
            foodDao.deleteAllCustom()
        } else {
            planDao.deleteForAllCustomFoods()
            foodDao.hideAllCustom()
        }
    }

    /** Custom foods still in the picker. */
    fun observeCustomVisibleFoods(): Flow<List<FoodEntity>> = foodDao.observeCustomVisible()

    /** Custom foods the user soft-deleted (kept only so old logs render). */
    fun observeCustomHiddenFoods(): Flow<List<FoodEntity>> = foodDao.observeCustomHidden()

    /** Bring a soft-deleted custom food back into the picker. */
    suspend fun restoreCustomFood(id: Long) = foodDao.unhide(id)

    /**
     * Online search for foods missing from the local database. Fans out in
     * parallel to:
     *   - OpenFoodFacts        — packaged products + some generic items
     *   - USDA FoodData Central — raw ingredients + branded products
     * Results are merged, de-duplicated by `name|brand`, and returned.
     * Nothing is persisted until the user picks a result via [saveOnlineResult].
     */
    suspend fun searchOnline(query: String, perSourceLimit: Int = 12): List<OnlineSearchResult> = coroutineScope {
        if (query.isBlank()) return@coroutineScope emptyList()
        val offDeferred = async {
            runCatching { api.searchByName(query, perSourceLimit) }.getOrDefault(emptyList())
                .map { OnlineSearchResult(it, OnlineSource.OpenFoodFacts) }
        }
        val usdaDeferred = async {
            runCatching { usdaApi.searchByName(query, perSourceLimit) }.getOrDefault(emptyList())
                .map { OnlineSearchResult(it, OnlineSource.USDA) }
        }
        val combined = listOf(offDeferred, usdaDeferred).awaitAll().flatten()
        val seenKeys = HashSet<String>()
        combined.filter { result ->
            val key = (result.food.name + "|" + (result.food.brand ?: "")).lowercase()
            seenKeys.add(key)
        }
    }

    /**
     * Persist an online-search result so it appears in subsequent local searches,
     * favourites, etc. Returns the saved row with its assigned [id]. If a row with
     * the same barcode already exists we return that instead of inserting a duplicate.
     */
    suspend fun saveOnlineResult(food: FoodEntity): FoodEntity {
        food.barcode?.let { code ->
            foodDao.findByBarcode(code)?.let { return it }
        }
        val id = foodDao.upsert(food.copy(id = 0))
        return food.copy(id = id)
    }

    /**
     * Always-online barcode resolver. Nutrition info on packaged products
     * does change over time, so we re-fetch on every scan rather than
     * trusting whatever stale row may sit in the local cache.
     *
     * Sources are fanned out in parallel and reconciled in priority order:
     *   1. OpenFoodFacts — community-maintained, widest coverage of EAN/UPC
     *      (and the strongest source for Indian packaged products)
     *   2. USDA FoodData Central — authoritative US-branded products
     *
     * If every source misses, we fall back to whatever the local DB has
     * (so an earlier scan that did resolve still works offline). Returning
     * `food = null` from this method means: no source recognised the code,
     * the user should be offered manual entry.
     */
    suspend fun fetchByBarcode(barcode: String): BarcodeLookup = coroutineScope {
        if (barcode.isBlank()) return@coroutineScope BarcodeLookup()

        val offDeferred = async { runCatching { api.lookupBarcode(barcode) }.getOrNull() }
        val usdaDeferred = async { runCatching { usdaApi.lookupBarcode(barcode) }.getOrNull() }

        listOf(offDeferred, usdaDeferred).awaitAll()
        val off = offDeferred.await()
        val usda = usdaDeferred.await()

        val (product, source) = when {
            off != null -> off to BarcodeSource.OpenFoodFacts
            usda != null -> usda to BarcodeSource.USDA
            else -> null to null
        }
        if (product != null) {
            return@coroutineScope BarcodeLookup(product = product, source = source)
        }
        // All online sources missed — surface the cached row if we have one,
        // tagged as Cached so the UI can warn the user it might be stale.
        val cached = foodDao.findByBarcode(barcode)
        BarcodeLookup(cached = cached, source = cached?.let { BarcodeSource.Cached })
    }

    /**
     * Persist a barcode product the user has confirmed — they've picked the basis
     * (per-100g vs per-serving) on the resolved panel, and we normalize to per-100g
     * here for uniform downstream logging math.
     *
     * Marked `is_custom = 1` so barcode-scanned foods live alongside hand-entered
     * customs (visible in the Custom Foods list, deletable in bulk). Upserts by
     * barcode so future offline browses still find it, and so the freshest values
     * overwrite any earlier cached row.
     *
     * [servingGrams] / [servingLabel] override the source-reported serving so the
     * user can edit it on the resolve panel before saving; pass null to keep the
     * scan defaults.
     */
    suspend fun commitBarcodeProduct(
        product: BarcodeProduct,
        basis: NutritionBasis,
        servingGrams: Double? = null,
        servingLabel: String? = null,
    ): FoodEntity {
        val per100g = product.toPerHundredG(basis)
        val existing = foodDao.findByBarcode(product.barcode)
        val finalServingGrams = servingGrams ?: product.servingGrams
        val finalServingLabel = servingLabel ?: product.servingLabel
        val row = FoodEntity.fromNutrientsPer100g(
            name = product.name,
            brand = product.brand,
            barcode = product.barcode,
            category = product.category,
            n = per100g,
            servingGrams = finalServingGrams,
            servingLabel = finalServingLabel,
            isCustom = true,
            isFavourite = existing?.isFavourite ?: false,
        ).copy(id = existing?.id ?: 0)
        val id = foodDao.upsert(row)
        return row.copy(id = id)
    }

    /** Returns true if the seed was just inserted (i.e. table was empty). */
    /** [seed] is [IndianFoodSeed.ALL] plus the [FoodCatalogue] foods. */
    suspend fun seedIfEmpty(seed: List<FoodEntity>): Boolean {
        if (foodDao.count() != 0) return false
        foodDao.upsertAll(seed)
        return true
    }

    /**
     * Update existing seeded rows in place when [IndianFoodSeed.SEED_VERSION] bumps.
     *
     * For each food in the current seed: if a non-custom row with the same name exists,
     * we copy the new nutrient values over while preserving the user-owned fields
     * (id, isFavourite, custom servingVariants if any). Foods missing from the DB
     * are inserted. The user's food logs, weights, and custom additions are untouched.
     */
    suspend fun refreshSeedIfStale(storedVersion: Int, seed: List<FoodEntity>): Boolean {
        if (storedVersion >= IndianFoodSeed.SEED_VERSION) return false
        // One read and one batched write, not two queries per seeded food. This
        // runs on the first launch after every SEED_VERSION bump, and looking
        // ~166 foods up one at a time meant ~332 separate transactions before
        // the user's first frame.
        //
        // The map is folded by hand rather than with associateBy because
        // associateBy keeps the LAST row for a duplicate name while
        // findSeededByName's LIMIT 1 took the first. Locale.ROOT because
        // SQLite's COLLATE NOCASE is ASCII-only folding — under a Turkish
        // default locale, lowercase('I') is 'ı' and the row would be missed.
        //
        // getAllSeeded, NOT getVisibleSeeded: this read must see hidden rows.
        // The refresh needs different semantics from the findSeededByName it
        // replaced — that lookup answers "which food should I show for this
        // name", where a hidden row rightly doesn't count, while this one asks
        // "does a row for this seed entry already exist", where it does. Read
        // through the visible-only filter, a hidden food looks absent, falls
        // to `return@map fresh`, and is inserted again as a brand-new visible
        // row with a new id and uuid: @Upsert finds no primary-key or
        // client_uuid conflict to update against. Hiding a seeded food would
        // therefore un-hide it as a duplicate on the next SEED_VERSION bump.
        val seededRows = foodDao.getAllSeeded()
        val existingByUuid = seededRows.associateBy { it.clientUuid }
        val existingByName = HashMap<String, FoodEntity>()
        for (row in seededRows) {
            existingByName.putIfAbsent(row.name.lowercase(Locale.ROOT), row)
        }

        // Rows already spoken for by a uuid match, so the name pass below
        // cannot hijack one of them. Two fresh entries landing on the same
        // local id would have `upsertAll` write one over the other and insert
        // neither of the foods that were missing — the kind of silent loss that
        // only shows up as a food having vanished.
        val claimedIds = HashSet<Long>()
        for (fresh in seed) {
            existingByUuid[fresh.clientUuid]?.let { claimedIds.add(it.id) }
        }

        val updated = seed.map { fresh ->
            // **uuid first, name only as a fallback.** The uuid is this entry's
            // permanent identity, so matching on it is what lets a seeded food
            // be *renamed*: the existing row is found, its name updated, and no
            // duplicate inserted. The name fallback carries the one run where
            // that cannot work — the first launch after uuids were pinned,
            // when every local row still holds the random uuid it was seeded
            // with and has to be re-keyed to the literal.
            val existing = existingByUuid[fresh.clientUuid]
                ?: existingByName[fresh.name.lowercase(Locale.ROOT)]
                    ?.takeIf { it.id !in claimedIds }
                ?: return@map fresh
            fresh.copy(
                id = existing.id,
                // Carried explicitly: `fresh` defaults it to false, so without
                // this the wider read above would un-hide every hidden food
                // instead of duplicating it — the same bug wearing a different
                // hat. Hiding is the user's decision about a seeded food, and
                // a nutrient correction is not a reason to overturn it.
                isHidden = existing.isHidden,
                // `fresh.clientUuid` deliberately wins — it is the pinned
                // literal from the seed, and adopting it is what re-keys an
                // install that was seeded before the uuids existed. This used
                // to carry `existing.clientUuid` forward, correctly, because
                // back then a fresh seed list minted a new random uuid on every
                // construction and keeping it would have broken restore. Now
                // the seed is the authority on what a seeded food's uuid is.
                isFavourite = existing.isFavourite,
                // The preparation this user last chose is theirs, not the
                // seed's. A nutrient correction must not reset their dosa back
                // to plain any more than it may un-favourite it.
                lastOil = existing.lastOil,
                lastStyle = existing.lastStyle,
                lastOilAmount = existing.lastOilAmount,
                // Keep the user's custom variants if they edited them; otherwise
                // adopt the fresh seed's variants so renamed labels, new gram
                // refs, and new size options (e.g. banana Small/Medium/Large)
                // propagate to already-installed apps. We treat the row as
                // "not customized" when its variants are still exactly the
                // auto-generated defaults for its serving.
                servingVariants = when {
                    existing.servingVariants.isEmpty() -> fresh.servingVariants
                    existing.servingVariants == ServingVariant.defaultVariantsFor(
                        existing.servingGrams, existing.servingLabel,
                    ) -> fresh.servingVariants
                    // Also adopt fresh variants when the existing ones are still
                    // exactly the auto-derived Small/Medium/Large presets — those
                    // aren't user customization either, so a corrected seed (e.g.
                    // real egg sizes replacing the ×0.7/×1.4 scale) must propagate.
                    existing.servingVariants == IndianFoodSeed.sizeVariantsFor(
                        existing.servingGrams, existing.servingLabel,
                    ) -> fresh.servingVariants
                    else -> existing.servingVariants
                },
            )
        }
        foodDao.upsertAll(updated)
        return true
    }

    // ----- Food log -----
    fun observeLogForDay(epochDay: Long): Flow<List<FoodLogWithFood>> =
        foodLogDao.observeForDay(epochDay)

    fun observeLoggedDays(): Flow<List<Long>> = foodLogDao.observeLoggedDays()

    fun observeLogForRange(start: Long, end: Long): Flow<List<FoodLogWithFood>> =
        foodLogDao.observeForRange(start, end)

    fun observeRecentFoods(limit: Int = 12): Flow<List<FoodEntity>> =
        foodLogDao.observeRecentFoods(limit)

    /** Direct (non-Flow) reads — used by widget rendering so we get the
     *  latest state immediately after a write, dodging the Room
     *  InvalidationTracker propagation lag. */
    suspend fun getLogForDay(epochDay: Long): List<FoodLogWithFood> =
        foodLogDao.getForDay(epochDay)
    suspend fun getRecentFoods(limit: Int): List<FoodEntity> =
        foodLogDao.getRecentFoods(limit)

    fun observeFrequentFoods(days: Int = 30, limit: Int = 12): Flow<List<FoodEntity>> =
        foodLogDao.observeFrequentFoods(DateUtil.todayEpochDay() - days, limit)

    suspend fun addFoodLog(entry: FoodLogEntity): Long =
        foodLogDao.insert(entry).also { onWidgetDataChanged() }

    /**
     * Edit an entry in place. Takes the whole entity rather than a field set
     * because every edit path — serving, preparation, meal, day — comes from
     * the serving sheet holding a complete row it has already recomputed.
     */
    suspend fun updateFoodLog(entry: FoodLogEntity) {
        foodLogDao.update(entry)
        onWidgetDataChanged()
    }

    suspend fun deleteFoodLog(id: Long) {
        foodLogDao.delete(id)
        onWidgetDataChanged()
    }

    // ----- Plan -----
    //
    // One plan, no list, no active flag — see [PlanItemEntity]. Everything the
    // feature needs is "what is on it" and "put this on it".

    fun observePlan(): Flow<List<PlanItemWithFood>> = planDao.observeAll()

    fun observePlanItemCount(): Flow<Int> = planDao.observeCount()

    /**
     * Put a food on the plan at an amount — adding it, changing its amount, or
     * moving it to another meal, depending on what is already there.
     *
     * The branching is not ceremony: `(food_id, meal_type)` is unique, so a
     * blind insert throws and a blind replace loses the row's place in the
     * order. The case that actually bites is the third one — editing a line and
     * changing its meal. Written naively that leaves the original row behind
     * and the food is then planned twice, which is precisely the state the
     * unique index exists to prevent. Adding dosa to breakfast when it is
     * already there is the user saying "actually, three", not "two dosas and
     * also three dosas".
     */
    suspend fun putPlanItem(item: PlanItemEntity) {
        val inSlot = planDao.find(item.foodId, item.mealType)
        when {
            // Editing a line that is staying where it is.
            inSlot != null && inSlot.id == item.id ->
                planDao.update(item.copy(sortIndex = inSlot.sortIndex))
            // The slot already holds this food: fold into that line, and drop
            // the one we came from if we were editing rather than adding.
            inSlot != null -> {
                planDao.update(item.copy(id = inSlot.id, sortIndex = inSlot.sortIndex))
                if (item.id != 0L) planDao.delete(item.id)
            }
            // Free slot — move the line we were editing into it...
            item.id != 0L -> planDao.update(item)
            // ...or append a new one.
            else -> planDao.insert(item.copy(sortIndex = planDao.maxSortIndex() + 1))
        }
    }

    suspend fun deletePlanItem(id: Long) = planDao.delete(id)

    suspend fun clearPlan() = planDao.deleteAll()

    // ----- Water -----
    fun observeWaterForDay(epochDay: Long): Flow<List<WaterLogEntity>> =
        waterLogDao.observeForDay(epochDay)
    fun observeWaterTotalForDay(epochDay: Long): Flow<Int> =
        waterLogDao.observeTotalForDay(epochDay)
    fun observeWaterTotalsForRange(start: Long, end: Long): Flow<List<DailyWaterTotal>> =
        waterLogDao.observeTotalsForRange(start, end)
    suspend fun addWater(entry: WaterLogEntity): Long = waterLogDao.insert(entry)
    suspend fun deleteWater(id: Long) = waterLogDao.delete(id)

    // ----- Weight -----
    fun observeRecentWeights(limit: Int = 60): Flow<List<WeightLogEntity>> =
        weightLogDao.observeRecent(limit)
    fun observeWeightsForRange(start: Long, end: Long): Flow<List<WeightLogEntity>> =
        weightLogDao.observeForRange(start, end)
    fun observeLatestWeight(): Flow<WeightLogEntity?> = weightLogDao.observeLatest()
    suspend fun upsertWeight(entry: WeightLogEntity): Long = weightLogDao.upsert(entry)
    suspend fun deleteWeight(id: Long) = weightLogDao.delete(id)
}
