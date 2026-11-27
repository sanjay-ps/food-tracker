package com.example.foodtracker.ui.screens.log

import com.example.foodtracker.util.DateUtil
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.example.foodtracker.ui.components.FoodCard
import com.example.foodtracker.ui.components.atoms.AppTextField
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Package
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.FlaskConical
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ScanBarcode
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Star
import androidx.compose.material.icons.rounded.Star
import com.example.foodtracker.ui.theme.MacroCarbs
import com.composables.icons.lucide.X
import com.example.foodtracker.ui.components.ExclusiveButtonGroup
import com.example.foodtracker.ui.components.atoms.RoundIconButton
import com.example.foodtracker.ui.components.atoms.cardColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.example.foodtracker.ui.components.atoms.AppModalSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.foodtracker.data.MealType
import com.example.foodtracker.data.OnlineSource
import com.example.foodtracker.data.db.FoodEntity
import com.example.foodtracker.ui.appViewModel
import com.example.foodtracker.ui.components.EmptyState
import com.example.foodtracker.ui.components.FoodCardPlaceholder
import com.example.foodtracker.ui.components.FoodRow
import com.example.foodtracker.ui.components.HapticFilterChip
import com.example.foodtracker.ui.components.atoms.AppDivider
import com.example.foodtracker.ui.components.atoms.AppOutlinedButton
import com.example.foodtracker.ui.components.atoms.AppTextButton
import com.example.foodtracker.ui.components.atoms.AppTopBar
import com.example.foodtracker.ui.components.atoms.IconCircle
import com.example.foodtracker.ui.components.atoms.Spinner
import com.example.foodtracker.ui.components.serving.ServingSheet
import com.example.foodtracker.ui.rememberAppHaptics
import com.example.foodtracker.ui.components.atoms.Section
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Spacing

/**
 * The log picker.
 *
 * The constraint on this screen was that it mostly works and should survive;
 * three things changed, each with a reason.
 *
 * **One search model.** `Search` used to be a tab *as well as* a field, so the
 * field narrowed that one tab and nothing else — searching your own favourites
 * was not expressible. Now the tab picks the set and the field narrows whatever
 * set you are on. The tab also stopped being stolen out from under you the
 * moment you typed.
 *
 * **Four tabs, opening on the one you want.** Recent and Frequent merged
 * (decision 2), and the landing tab is that merged list rather than all 166
 * foods in alphabetical order, which was the least likely thing anyone wanted
 * to see first.
 *
 * **Online search is a pinned footer.** It used to be the last item of the
 * local results list, i.e. discoverable only by scrolling past everything you
 * already had. It is now a bar across the bottom of the screen that appears as
 * soon as there is a query to send, and the online results land in the list
 * above it with their source tag.
 *
 * Everything else is deliberately unchanged: the star toggle, the Custom tab's
 * "Show hidden" chip and its two sheets, the `AddCustomFoodRow`, long-press to
 * delete a custom food, and the per-tab scroll reset.
 *
 * **The editorial pass (docs/ui-house-rules.md) changed the furniture, not the
 * flow.** Three filled shapes were carrying the top of this screen: a row of
 * four accent-filled tab pills, a 28dp search capsule, and two outlined cards
 * sitting in a list that is otherwise plain rows. Together they were most of
 * what made the rewrite read as the old app — a stack of rounded blocks with a
 * mint one selected. They are now a rule-marked tab row (rule 3, the same
 * language `DayStrip` speaks), a [Radius.md] field (rule 6), and rows divided
 * by hairlines (rule 1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogFoodScreen(
    initialMeal: MealType,
    epochDay: Long,
    onBack: () -> Unit,
    onScanBarcode: () -> Unit,
    onAddCustomFood: () -> Unit,
    onOpenRichFoods: () -> Unit,
    /** False while the screen is still sliding in. */
    entered: Boolean = true,
) {
    // Food cards are the costly part of this screen to build, so they wait for
    // the slide-in to finish (placeholders show meanwhile): building them
    // mid-animation is what made opening stutter. Latched, so the exit
    // animation never swaps them back.
    var ready by remember { mutableStateOf(false) }
    if (entered) ready = true
    val vm: LogFoodViewModel = appViewModel { LogFoodViewModel(it.repository, it.profileStore, initialMeal, epochDay) }
    val eatenAtOverride by vm.eatenAtOverride.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberAppHaptics()
    var pendingDelete by remember { mutableStateOf<FoodEntity?>(null) }
    var pendingHidden by remember { mutableStateOf<FoodEntity?>(null) }
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val landing by vm.landing.collectAsStateWithLifecycle()
    // The keyboard waits for the cards too: its animation resizes the grid.
    LaunchedEffect(landing, ready && !state.loading) {
        if (landing == LogTab.All && ready && !state.loading) {
            searchFocus.requestFocus()
            keyboard?.show()
        }
    }

    val isHiddenView = state.tab == LogTab.Custom && state.customFilter == CustomFilter.Hidden
    val listForTab = state.visibleList
    val showCustomAddRow = state.tab == LogTab.Custom && !isHiddenView

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Add to ${state.mealType.displayName}",
                // Say which day when it is not today — back-filling is easy to forget.
                subtitle = if (epochDay != DateUtil.todayEpochDay()) DateUtil.formatHeader(epochDay) else null,
                onBack = onBack,
                actions = {
                    RoundIconButton(Lucide.FlaskConical, "Find foods rich in…", onOpenRichFoods)
                    RoundIconButton(Lucide.ScanBarcode, "Scan barcode", onScanBarcode)
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Not auto-focused, deliberately: the keyboard would cover the tab
            // you landed on, and the common case is tapping a food you can
            // already see rather than typing its name.
            AppTextField(
                value = vm.queryText,
                focusRequester = searchFocus,
                onValueChange = vm::setQuery,
                // The count is only ever read while the field is empty — which
                // is exactly when `all` holds the unfiltered catalogue.
                placeholder = { Text(if (state.totalFoods > 0) "Search ${state.totalFoods} foods…" else "Search foods…", style = AppText.body) },
                leadingIcon = { Icon(Lucide.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (vm.queryText.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) {
                            Icon(Lucide.X, contentDescription = "Clear search", modifier = Modifier.size(20.dp))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.gutter)
                    .padding(bottom = Spacing.m),
                // Rule 6: the 28dp capsule is down to Radius.md. A search field
                // is a box you type in, not a button — the capsule was shape
                // borrowed from the control below it, and two pill silhouettes
                // stacked at the top of the screen were the first thing the eye
                // met on the way into the picker.
                shape = RoundedCornerShape(Radius.pill),
                singleLine = true,
            )

            LogTabRow(
                selected = state.tab,
                onSelect = vm::setTab,
                modifier = Modifier.padding(horizontal = Spacing.gutter),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (state.loading) "Loading foods…" else countCaption(state.listCount),
                    style = AppText.eyebrow,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                // Visible/Hidden is a binary toggle for one rare workflow, so a
                // single chip reads more naturally than a two-way control.
                if (state.tab == LogTab.Custom) {
                    HapticFilterChip(
                        selected = isHiddenView,
                        onClick = {
                            vm.setCustomFilter(
                                if (isHiddenView) CustomFilter.Visible else CustomFilter.Hidden
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Lucide.EyeOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        label = { Text("Show hidden") },
                    )
                }
            }

            // One LazyListState is reused across tabs, so without this reset a
            // deep scroll on All carries over to Often and the user lands
            // mid-list. Keyed on everything that swaps the list's contents.
            val listState = rememberLazyGridState()
            LaunchedEffect(state.tab, state.customFilter, state.query) {
                listState.scrollToItem(0)
            }

            // All and Ranked load a page at a time: fetch the next one while the
            // last few cards of this one are still on their way into view.
            val nearEnd by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                    info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_AHEAD
                }
            }
            // Only once the real cards show: six placeholders always look "near the end".
            val showingCards = ready && !state.loading
            LaunchedEffect(nearEnd, state.canLoadMore, listForTab.size, showingCards) {
                if (showingCards && nearEnd && state.canLoadMore) vm.loadMore()
            }

            // Two columns of food tiles; headers, hints and empty states span both.
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.xs, bottom = Spacing.xxl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                if (showCustomAddRow) {
                    item("add-custom", span = { GridItemSpan(maxLineSpan) }) { AddCustomFoodRow(onClick = onAddCustomFood) }
                }
                items(listForTab, key = { it.id }) { food ->
                    FoodCard(
                        food = food,
                        rank = if (state.tab == LogTab.Ranked) state.ranks[food.id] else null,
                        onClick = {
                            haptics.light()
                            if (isHiddenView) pendingHidden = food else vm.select(food)
                        },
                        onLongClick = food.takeIf { it.isCustom && !isHiddenView }?.let {
                            {
                                haptics.confirm()
                                pendingDelete = it
                            }
                        },
                        badge = {
                            if (isHiddenView) {
                                CardBadge("Hidden")
                            } else {
                                // Filled against outline carries the state on
                                // its own, so the accent comes off the star:
                                // rule 3 spends `primary` on readings and the
                                // primary action, and a mint glyph repeated
                                // down a list of favourites is the largest
                                // single use of it anywhere in the app.
                                IconButton(onClick = {
                                    haptics.light()
                                    vm.toggleFavourite(food)
                                }) {
                                    if (food.isFavourite) {
                                        Icon(
                                            Icons.Rounded.Star,
                                            contentDescription = "Unfavourite",
                                            tint = MacroCarbs,
                                        )
                                    } else {
                                        Icon(
                                            Lucide.Star,
                                            contentDescription = "Favourite",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
                if (state.loading || !ready) {
                    items(PLACEHOLDER_CARDS, key = { "placeholder-$it" }) { FoodCardPlaceholder() }
                } else if (listForTab.isEmpty()) {
                    item("empty", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyTabHint(
                            tab = state.tab,
                            isHiddenView = isHiddenView,
                            hasQuery = state.query.isNotBlank(),
                            onAddCustomFood = onAddCustomFood,
                        )
                    }
                }
                if (state.onlineResults.isNotEmpty()) {
                    item("online-header", span = { GridItemSpan(maxLineSpan) }) {
                        // A standing head with a rule under it, like every
                        // other section break in the app: these rows are a
                        // different *kind* of thing from the catalogue above
                        // them — not saved yet, and tapping one writes a food —
                        // so the break has to be legible without being a box
                        // around half the list. The count rides the head's
                        // trailing slot, which is where every section in the
                        // app states its total.
                        Column(Modifier.padding(top = Spacing.s)) {
                            Section(
                                "Online",
                                trailing = {
                                    Text(
                                        countCaption(state.onlineResults.size),
                                        style = AppText.bodyS,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            ) {
                                Text(
                                    "Tap one to save it and log it",
                                    style = AppText.bodyS,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    // No key: online hits are transient, not yet in the
                    // database, and two sources returning the same product
                    // name would make a stable key a duplicate-key crash.
                    items(state.onlineResults) { result ->
                        FoodCard(
                            food = result.food,
                            onClick = {
                                haptics.light()
                                vm.pickOnline(result)
                            },
                            badge = { CardBadge(result.source.display) },
                        )
                    }
                }
            }

            if (state.query.isNotBlank()) {
                AppDivider()
                OnlineSearchBar(
                    phase = state.onlinePhase,
                    hasResults = state.onlineResults.isNotEmpty(),
                    onSearch = {
                        haptics.light()
                        vm.searchOnline()
                    },
                )
            }
        }
    }

    state.selected?.let { food ->
        ServingSheet(
            food = food,
            meal = state.mealType,
            eatenAt = eatenAtOverride ?: vm.eatenAt(state.mealType),
            onEatenAtChange = { vm.eatenAtOverride.value = it },
            variant = state.selectedVariant,
            servings = state.selectedServings,
            onVariantChange = vm::setVariant,
            onServingsChange = vm::setServings,
            onMealChange = vm::setMealType,
            onConfirm = {
                haptics.confirm()
                vm.confirmLog(onDone = onBack)
            },
            onDismiss = { vm.select(null) },
            oil = state.selectedOil,
            onOilChange = vm::setOil,
            style = state.selectedStyle,
            onStyleChange = vm::setStyle,
            oilAmount = state.selectedOilAmount,
            onOilAmountChange = vm::setOilAmount,
        )
    }

    pendingDelete?.let { target ->
        AppModalSheet(
            onDismissRequest = { pendingDelete = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
        ) {
            DeleteCustomFoodSheet(
                food = target,
                onCascade = {
                    vm.deleteCustomFood(target, cascadeLogs = true)
                    pendingDelete = null
                },
                onKeepHistory = {
                    vm.deleteCustomFood(target, cascadeLogs = false)
                    pendingDelete = null
                },
                onCancel = { pendingDelete = null },
            )
        }
    }

    pendingHidden?.let { target ->
        AppModalSheet(
            onDismissRequest = { pendingHidden = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
        ) {
            HiddenCustomFoodSheet(
                food = target,
                onRestore = {
                    vm.restoreCustomFood(target)
                    pendingHidden = null
                },
                onDeleteForever = {
                    vm.deleteCustomFood(target, cascadeLogs = true)
                    pendingHidden = null
                },
                onCancel = { pendingHidden = null },
            )
        }
    }
}

/** Short enough to fit four across a 412dp screen without wrapping. */
private fun LogTab.label(): String = when (this) {
    LogTab.Favourites -> "Favs"
    LogTab.All -> "All"
    LogTab.Ranked -> "Ranked"
    LogTab.Custom -> "Custom"
}

/** The selected tab's underline. 2dp, as on `DayStrip` — see [LogTabRow]. */
private val TAB_RULE = 2.dp

/**
 * The four sets — house rule 3, built to match `today/components/DayStrip.kt`.
 *
 * It was `ExclusiveButtonGroup`: four pills, the selected one a filled
 * `primaryContainer` block. That is the rule's exact prohibition — the accent
 * has to keep meaning *on track*, and it cannot while it is also the colour of
 * "this is the tab you are on". It was also the loudest shape on a screen whose
 * whole job is to let you scan a list of food names underneath it.
 *
 * A tab is a label with a rule under it now: the chosen one is `onSurface` over
 * a 2dp `onSurface` bar, the rest are `onSurfaceVariant` over nothing. The
 * group's own hairline runs the full width beneath all four, so the selected
 * rule reads as a thickening of a line that is already there rather than as a
 * new object — the same construction `DayStrip` uses for the selected day, and
 * for the same reason: it marks the selection in the language the rest of the
 * page already speaks.
 *
 * Not `ExclusiveButtonGroup` with different colours, because that control is
 * shared with seven other screens and is genuinely a segmented *button* group
 * everywhere else. A tab strip is a different thing that happens to have four
 * entries, so it is forty lines here rather than a `variant` flag there.
 *
 * The haptic matches that component's contract exactly: a tick only on a real
 * change of selection, never on re-tapping the tab you are on.
 */
@Composable
private fun LogTabRow(
    selected: LogTab,
    onSelect: (LogTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    ExclusiveButtonGroup(
        options = LogTab.entries,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier.fillMaxWidth(),
        trackColor = cardColor(),
        label = { it.label() },
    )
}

/** Stand-in cards while the first page loads: enough to fill the screen. */
private const val PLACEHOLDER_CARDS = 6

/** Cards from the end of the loaded page at which the next page is fetched. */
private const val LOAD_AHEAD = 12

private fun countCaption(size: Int): String =
    if (size == 1) "1 food" else "$size foods"

/**
 * The pinned online-search bar.
 *
 * It lives here, across the bottom, instead of at the end of the results list,
 * because the moment you need it is the moment the local list has failed you —
 * and asking a user to scroll past 166 foods they have already rejected to
 * find the escape hatch is asking them to give up.
 *
 * The three loading phases stay exactly as they were; only their home changed.
 * The spinner is the app's [Spinner], not Material's `LoadingIndicator`, which
 * was a shape-morphing animation the motion rules ban.
 */
@Composable
private fun OnlineSearchBar(
    phase: OnlineSearchPhase,
    hasResults: Boolean,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (phase) {
            OnlineSearchPhase.Loading -> {
                Spinner(size = 18.dp)
                Spacer(Modifier.width(Spacing.m))
                Text(
                    "Searching food databases…",
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OnlineSearchPhase.Empty -> {
                Text(
                    "No online matches either.",
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                AppOutlinedButton(text = "Try again", onClick = onSearch)
            }
            OnlineSearchPhase.Error -> {
                Text(
                    "Couldn't reach the food databases.",
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                AppOutlinedButton(text = "Retry", onClick = onSearch)
            }
            OnlineSearchPhase.Idle -> {
                Text(
                    if (hasResults) "Not it either?" else "No luck?",
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                AppOutlinedButton(
                    text = if (hasResults) "Search again" else "Search online",
                    onClick = onSearch,
                )
            }
        }
    }
}

/** Which database a result came from. Small, grey, never competing with the name. */
@Composable
private fun SourceTag(source: OnlineSource) {
    Text(
        source.display,
        style = AppText.eyebrow,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.s),
    )
}

/**
 * Pinned atop the Custom Visible tab. It stays a row in the list rather than a
 * top-bar icon because the icon read as "edit profile" and was easy to miss;
 * here the action is found the moment you land on the tab that needs it.
 *
 * It was an `AppCard` with an accent-filled icon disc — a bordered box sitting
 * directly above a list of plain `FoodRow`s, which is rule 1's "stack of
 * full-width rounded boxes" in miniature, and rule 3's accent-as-chrome in the
 * disc. As a row with a hairline under it, it now sits in the list it belongs
 * to: same height and same left edge as the foods below, with the divider
 * doing what the border did. The `Add` glyph stays — it is the one thing on
 * the row that says *this one is not a food*.
 */
@Composable
private fun AddCustomFoodRow(onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircle(icon = Lucide.Plus)
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Text(
                    "Add a custom food",
                    style = AppText.titleM,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Enter your own nutrition values",
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        AppDivider()
    }
}

/**
 * The per-tab empty copy, kept verbatim — it is well written and
 * each line says what *this* tab fills up with. Only its container changed: the
 * shared [EmptyState] atom instead of a bespoke centred box.
 */
@Composable
private fun EmptyTabHint(
    tab: LogTab,
    isHiddenView: Boolean,
    hasQuery: Boolean,
    onAddCustomFood: () -> Unit,
) {
    // A filtered-to-nothing list is a different situation from an empty tab,
    // and telling someone who just typed "xyz" that they have no favourites
    // yet would be both wrong and unhelpful.
    if (hasQuery) {
        EmptyState(
            icon = Lucide.Search,
            title = "No matches here",
            message = "Nothing on this tab matches your search. Try another tab, or look it up online below.",
        )
        return
    }
    val icon: ImageVector
    val title: String
    val message: String
    when {
        tab == LogTab.Custom && isHiddenView -> {
            icon = Lucide.EyeOff
            title = "No hidden foods"
            message = "Soft-deleted entries will show up here so you can restore or wipe them."
        }
        tab == LogTab.Custom -> {
            icon = Lucide.Package
            title = "No custom foods yet"
            message = "Use the row above to add one."
        }
        tab == LogTab.Favourites -> {
            icon = Lucide.Star
            title = "No favourites yet"
            message = "Tap the star on any food to save it here."
        }
        tab == LogTab.Ranked -> {
            icon = Lucide.Sparkles
            title = "No ranked foods match"
            message = "Try All, or a different word."
        }
        else -> {
            icon = Lucide.Search
            title = "No foods found"
            message = "Add a custom food, or scan a barcode."
        }
    }
    EmptyState(
        icon = icon,
        title = title,
        message = message,
        action = if (tab == LogTab.All) {
            { AppOutlinedButton(text = "Add a custom food", onClick = onAddCustomFood) }
        } else null,
    )
}

@Composable
private fun DeleteCustomFoodSheet(
    food: FoodEntity,
    onCascade: () -> Unit,
    onKeepHistory: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.gutter)
            .padding(bottom = Spacing.xxl),
    ) {
        Text(
            "Delete ${food.name}?",
            style = AppText.titleL,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Custom foods you added can be removed from your catalog. Choose what to do with any past log entries that used this food.",
            style = AppText.bodyS,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.l))
        ChoiceRow(
            icon = Lucide.EyeOff,
            title = "Delete, keep history",
            subtitle = "Hide from search and favourites. Past diary entries still show the name and nutrients.",
            onClick = onKeepHistory,
        )
        ChoiceRow(
            icon = Lucide.Trash2,
            title = "Delete with history",
            subtitle = "Remove this food and every past log entry that used it. Daily totals will change.",
            destructive = true,
            onClick = onCascade,
        )
        AppDivider()
        Spacer(Modifier.height(Spacing.m))
        AppTextButton(text = "Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun HiddenCustomFoodSheet(
    food: FoodEntity,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.gutter)
            .padding(bottom = Spacing.xxl),
    ) {
        Text(food.name, style = AppText.titleL, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "This custom food is hidden from the picker but past diary entries still reference it.",
            style = AppText.bodyS,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.l))
        ChoiceRow(
            icon = Lucide.RotateCcw,
            title = "Restore",
            subtitle = "Bring it back into search and favourites.",
            onClick = onRestore,
        )
        ChoiceRow(
            icon = Lucide.Trash2,
            title = "Delete forever",
            subtitle = "Wipe this food and every past log entry that used it. Daily totals will change.",
            destructive = true,
            onClick = onDeleteForever,
        )
        AppDivider()
        Spacer(Modifier.height(Spacing.m))
        AppTextButton(text = "Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Icon + title + subtitle — the shared `ChoiceRow`, renamed from `DeleteOptionRow`
 * because both of the sheets above offer a non-destructive option too, and a
 * name that says "delete" on the Restore row was a small lie in a place where
 * the user is deciding whether to lose data.
 *
 * It was two outlined cards stacked with a gap — two boxes offering two
 * alternatives, which rule 1 says is a border doing a rule's job. The rows are
 * divided instead, which also puts the two choices on one continuous surface
 * where they can be compared, rather than in two containers that read as two
 * unrelated offers.
 *
 * Only the destructive row keeps a coloured glyph. The other one used `primary`
 * for an icon that is not a status and not the primary action — rule 3 — and
 * on a sheet where one choice loses data, an accent on the *safe* option was
 * actively misleading about which one the app wants you to take.
 */
@Composable
private fun ChoiceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Column {
        AppDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (destructive) scheme.error else scheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Text(title, style = AppText.titleM, color = scheme.onSurface)
                Text(subtitle, style = AppText.bodyS, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** A small frosted pill in a food tile's corner. */
@Composable
private fun CardBadge(text: String) {
    Text(
        text,
        style = AppText.eyebrow,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .padding(Spacing.s)
            .clip(RoundedCornerShape(Radius.pill))
            .background(cardColor().copy(alpha = 0.85f))
            .padding(horizontal = Spacing.s, vertical = 2.dp),
    )
}
