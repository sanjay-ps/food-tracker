package com.example.foodtracker.ui.navigation

import androidx.compose.animation.EnterExitState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.foodtracker.DeepLink
import com.example.foodtracker.data.MealType
import com.example.foodtracker.data.PlanSlot
import com.example.foodtracker.ui.LocalAppContainer
import com.example.foodtracker.ui.components.sheets.WaterSheet
import com.example.foodtracker.ui.components.sheets.WeighInSheet
import com.example.foodtracker.ui.screens.addfood.AddFoodScreen
import com.example.foodtracker.ui.screens.barcode.BarcodeScreen
import com.example.foodtracker.ui.screens.log.LogFoodScreen
import com.example.foodtracker.ui.screens.nutrients.AllNutrientsScreen
import com.example.foodtracker.ui.screens.onboarding.OnboardingScreen
import com.example.foodtracker.ui.screens.plan.PlanPickFoodScreen
import com.example.foodtracker.ui.screens.plan.PlanScreen
import com.example.foodtracker.ui.screens.plan.PlanTodayScreen
import com.example.foodtracker.ui.screens.privacy.PrivacyScreen
import com.example.foodtracker.ui.screens.reminders.RemindersScreen
import com.example.foodtracker.ui.screens.rich.RichFoodsScreen
import com.example.foodtracker.ui.screens.today.TodayScreen
import com.example.foodtracker.ui.screens.today.TodaySheet
import com.example.foodtracker.ui.screens.trends.TrendsScreen
import com.example.foodtracker.ui.screens.you.DataBackupScreen
import com.example.foodtracker.ui.screens.you.EditProfileScreen
import com.example.foodtracker.ui.screens.you.YouScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@Composable
fun AppNav(
    pendingDeepLink: MutableStateFlow<DeepLink?> = MutableStateFlow(null),
) {
    val container = LocalAppContainer.current
    val onboarded by produceState<Boolean?>(initialValue = null) {
        container.profileStore.isOnboarded.collect { value = it }
    }
    val navController = rememberNavController()

    // Water and weight stopped being routes in phase 4, so their deep links
    // resolve to "Today, with this sheet open". Held here rather than inside
    // Today because the link arrives before Today composes.
    var pendingSheet by remember { mutableStateOf<TodaySheet?>(null) }

    // Consume widget / reminder-notification deep-links once onboarding is
    // complete. Cleared after navigation so process death / back press don't
    // re-fire.
    //
    // Both gates are awaited *inside* the coroutine rather than keyed on the
    // effect, and the order matters:
    //  1. onboarding done - the onboarding graph has no LogFood destination
    //     to navigate to;
    //  2. a NavHost has actually run setGraph. currentBackStackEntryFlow only
    //     emits once a graph is installed, so awaiting it is what keeps
    //     navigate() from throwing "You must call setGraph before calling
    //     getGraph".
    // Keying the effect on `onboarded` is not enough: the body re-reads the
    // state when it runs, so the run launched while `onboarded` was still null
    // (when the `when` below composes nothing at all) can observe `true` and
    // navigate a frame before the NavHost first composes. That is the cold
    // start from a notification tap.
    LaunchedEffect(navController) {
        snapshotFlow { onboarded }.first { it == true }
        navController.currentBackStackEntryFlow.first()
        pendingDeepLink.filterNotNull().collect { link ->
            // launchSingleTop so a second tap on the same reminder - or
            // tomorrow's firing while the app is still open on that screen -
            // reuses the destination instead of stacking another copy of it.
            when (link) {
                is DeepLink.LogMeal -> {
                    val meal = link.mealName?.let { MealType.fromName(it) } ?: MealType.currentForTime()
                    navController.navigate(LogFoodRoute(meal.name)) { launchSingleTop = true }
                }
                DeepLink.OpenWater -> {
                    navController.toBarDestination(BarDestination.Today)
                    pendingSheet = TodaySheet.Water
                }
                DeepLink.OpenWeight -> {
                    navController.toBarDestination(BarDestination.Today)
                    pendingSheet = TodaySheet.Weight
                }
            }
            pendingDeepLink.value = null
        }
    }

    when (onboarded) {
        null -> Unit // loading
        false -> NavHost(
            navController = navController,
            startDestination = OnboardingRoute,
            modifier = Modifier.fillMaxSize(),
            enterTransition = navEnter,
            exitTransition = navExit,
            popEnterTransition = navPopEnter,
            popExitTransition = navPopExit,
            predictivePopEnterTransition = navPredictivePopEnter,
            predictivePopExitTransition = navPredictivePopExit,
        ) {
            graph(navController, pendingSheet) { pendingSheet = null }
        }
        true -> {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val current = backStackEntry?.destination.barDestination()
            // The bar floats over the tab screens, which pad their own content
            // by [bottomBarClearance] so the last card scrolls clear of it.
            // Leaf pages hide it: a camera or a long form has nothing to say
            // about which tab you are on.
            val hazeState = rememberHazeState()
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = TodayRoute,
                    modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                    enterTransition = navEnter,
                    exitTransition = navExit,
                    popEnterTransition = navPopEnter,
                    popExitTransition = navPopExit,
                    predictivePopEnterTransition = navPredictivePopEnter,
                    predictivePopExitTransition = navPredictivePopExit,
                ) {
                    graph(navController, pendingSheet) { pendingSheet = null }
                }
                AnimatedVisibility(
                    visible = current != null,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    AppBottomBar(
                        hazeState = hazeState,
                        current = current,
                        onSelect = { navController.toBarDestination(it) },
                        onAdd = {
                            navController.navigate(LogFoodRoute(MealType.currentForTime().name)) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Switch bar tabs, and the back-stack behaviour that goes with it.
 *
 * The three are **peers**, not a stack: `popUpTo(startDestination)` with
 * `saveState`/`restoreState` means Today → Trends → You → back lands on Today
 * rather than walking back through Trends, and each tab keeps its own scroll
 * position. `launchSingleTop` stops a second tap on the current tab stacking a
 * duplicate.
 */
private fun NavHostController.toBarDestination(dest: BarDestination) {
    val route: Any = when (dest) {
        BarDestination.Today -> TodayRoute
        BarDestination.Trends -> TrendsRoute
        BarDestination.You -> YouRoute
    }
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Standard Android push/pop: the new screen slides in from the right and pushes
 * the previous one ~30% to the left (parallax); back reverses the motion. 300ms
 * matches the Material spec.
 *
 * The `predictivePop*` pair matters as much as the `pop*` pair. Since
 * navigation-compose 2.10, a pop driven by the predictive-back *gesture* uses
 * its own transitions rather than popEnter/popExit, and
 * `DefaultNavTransitions` defines those as fadeIn + scaleOut(0.7f) - which is
 * the zoom-out you get if you only override popEnter/popExit. Pointing them at
 * the same slide keeps the gesture and the back button producing one motion.
 * The unused Int the predictive lambdas receive is the swipe edge; the screen
 * slides off to the right either way, matching the non-gesture pop.
 */
private const val NAV_ANIM_MS = 300

/**
 * True only when *both* ends of the move are bar destinations.
 *
 * This is the whole fix for a mismatched back transition. The fade-through used
 * to be declared per-destination on the three tab composables, and a
 * destination-level transition beats the NavHost's. A transition has two ends,
 * though, and each end was answering for itself: leaving Today for the log
 * picker, Today ran its own `exitTransition` and faded while the picker slid
 * in; coming back, the picker slid off to the right while Today ran its
 * `popEnterTransition` and faded up underneath instead of parallaxing back.
 * Asking about both ends at once is the only way to tell a peer swap from a
 * push, so the decision belongs here rather than on either destination.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.barDestination() != null &&
        targetState.destination.barDestination() != null

private val navEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (isTabSwitch()) fadeThroughIn() else slideIntoContainer(SlideDirection.Start, tween(NAV_ANIM_MS))
}

private val navExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (isTabSwitch()) fadeThroughOut() else slideOutOfContainer(SlideDirection.Start, tween(NAV_ANIM_MS)) { it / 3 }
}

private val navPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (isTabSwitch()) fadeThroughIn() else slideIntoContainer(SlideDirection.End, tween(NAV_ANIM_MS)) { it / 3 }
}

private val navPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (isTabSwitch()) fadeThroughOut() else slideOutOfContainer(SlideDirection.End, tween(NAV_ANIM_MS))
}

private val navPredictivePopEnter:
    AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> EnterTransition = { navPopEnter() }

private val navPredictivePopExit:
    AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> ExitTransition = { navPopExit() }

/**
 * Fade-through for bar switches, per the motion rules. Peers do not slide: a
 * slide says "deeper" or "back", and Trends is neither of those from Today.
 * 90ms out, then 210ms in, so the outgoing screen is gone before the incoming
 * one starts and the two never cross-dissolve into mud.
 */
private fun fadeThroughIn(): EnterTransition = fadeIn(tween(durationMillis = 210, delayMillis = 90))

private fun fadeThroughOut(): ExitTransition = fadeOut(tween(90))

private fun NavGraphBuilder.graph(
    navController: NavHostController,
    pendingSheet: TodaySheet?,
    onSheetConsumed: () -> Unit,
) {
    composable<OnboardingRoute> {
        OnboardingScreen(onDone = {
            navController.navigate(TodayRoute) {
                popUpTo(OnboardingRoute) { inclusive = true }
            }
        })
    }

    // ---- Bar destinations: fade-through, no slide -------------------------

    composable<TodayRoute> {
        TodayScreen(
            onAddFoodToMeal = { meal, day -> navController.navigate(LogFoodRoute(meal.name, day)) },
            onOpenYou = { navController.toBarDestination(BarDestination.You) },
            onOpenRichFoods = { key -> navController.navigate(RichFoodsRoute(key)) },
            onOpenAllNutrients = { day -> navController.navigate(AllNutrientsRoute(day)) },
            onOpenPlan = { navController.navigate(PlanTodayRoute) },
            openSheet = pendingSheet,
            onSheetConsumed = onSheetConsumed,
        )
    }
    composable<TrendsRoute> {
        // Trends hosts the same two sheets Today does. They are modals, not
        // routes: a whole back-stack entry for four water buttons was
        // over-provisioned, and the weigh-in sheet is one number and a toggle.
        var sheet by rememberSaveable { mutableStateOf<TodaySheet?>(null) }
        TrendsScreen(
            onOpenRichFoods = { key -> navController.navigate(RichFoodsRoute(key)) },
            onOpenWater = { sheet = TodaySheet.Water },
            onOpenWeight = { sheet = TodaySheet.Weight },
        )
        when (sheet) {
            TodaySheet.Water -> WaterSheet(onDismiss = { sheet = null })
            TodaySheet.Weight -> WeighInSheet(onDismiss = { sheet = null })
            null -> Unit
        }
    }
    composable<YouRoute> {
        YouScreen(
            onOpenPlan = { navController.navigate(PlanRoute) },
            onOpenReminders = { navController.navigate(RemindersRoute) },
            onOpenPrivacy = { navController.navigate(PrivacyRoute) },
            onOpenEditProfile = { navController.navigate(EditProfileRoute) },
            onOpenDataBackup = { navController.navigate(DataBackupRoute) },
        )
    }

    // ---- Leaf pages -------------------------------------------------------

    composable<LogFoodRoute> { entry ->
        val route: LogFoodRoute = entry.toRoute()
        LogFoodScreen(
            // False until the slide-in has finished; the grid waits for it.
            entered = transition.currentState == EnterExitState.Visible,
            epochDay = route.epochDay ?: com.example.foodtracker.util.DateUtil.todayEpochDay(),
            initialMeal = MealType.fromName(route.mealType),
            onBack = { navController.popBackStack() },
            onScanBarcode = { navController.navigate(BarcodeRoute) },
            onAddCustomFood = { navController.navigate(AddFoodRoute()) },
            onOpenRichFoods = { navController.navigate(RichFoodsRoute()) },
        )
    }
    composable<AddFoodRoute> { entry ->
        val route: AddFoodRoute = entry.toRoute()
        AddFoodScreen(
            prefillBarcode = route.prefillBarcode,
            onBack = { navController.popBackStack() },
        )
    }
    composable<BarcodeRoute> {
        BarcodeScreen(
            onBack = { navController.popBackStack() },
            onAddManually = { barcode ->
                // Back from the form must not re-open the camera.
                navController.navigate(AddFoodRoute(prefillBarcode = barcode)) {
                    popUpTo(BarcodeRoute) { inclusive = true }
                }
            },
        )
    }
    composable<RichFoodsRoute> { entry ->
        val route: RichFoodsRoute = entry.toRoute()
        RichFoodsScreen(
            onBack = { navController.popBackStack() },
            preselectedNutrientKey = route.nutrientKey,
            planSlot = route.planMeal?.let { PlanSlot.fromName(it) },
            // Back to the editor, not back one step: the user walked
            // Plan → pick → Rich-in to add one food, and unwinding that
            // through the picker would leave them on a search screen they
            // have finished with.
            onPlanned = {
                navController.popBackStack(route = PlanRoute, inclusive = false)
            },
        )
    }
    composable<PlanTodayRoute> {
        PlanTodayScreen(
            onBack = { navController.popBackStack() },
            onEditPlan = { navController.navigate(PlanRoute) },
        )
    }
    composable<PlanRoute> {
        PlanScreen(
            onBack = { navController.popBackStack() },
            onAddFood = { slot -> navController.navigate(PlanPickFoodRoute(slot.storedName)) },
        )
    }
    composable<PlanPickFoodRoute> { entry ->
        val route: PlanPickFoodRoute = entry.toRoute()
        PlanPickFoodScreen(
            slot = PlanSlot.fromName(route.mealType),
            onBack = { navController.popBackStack() },
            onOpenRichFoods = {
                navController.navigate(RichFoodsRoute(planMeal = route.mealType))
            },
        )
    }
    composable<AllNutrientsRoute> { entry ->
        val route: AllNutrientsRoute = entry.toRoute()
        AllNutrientsScreen(
            epochDay = route.epochDay,
            onBack = { navController.popBackStack() },
            onOpenRichFoods = { key -> navController.navigate(RichFoodsRoute(key)) },
        )
    }
    composable<EditProfileRoute> {
        EditProfileScreen(onBack = { navController.popBackStack() })
    }
    composable<DataBackupRoute> {
        DataBackupScreen(onBack = { navController.popBackStack() })
    }
    composable<PrivacyRoute> {
        PrivacyScreen(onBack = { navController.popBackStack() })
    }
    composable<RemindersRoute> {
        RemindersScreen(onBack = { navController.popBackStack() })
    }
}
