package com.example.foodtracker

import com.example.foodtracker.data.ThemeMode
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.SystemBarStyle
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.foodtracker.ui.AppContainer
import com.example.foodtracker.ui.LocalAppContainer
import com.example.foodtracker.ui.NoOpHapticFeedback
import com.example.foodtracker.ui.navigation.AppNav
import com.example.foodtracker.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // One-shot deep-link target. Set from the launching Intent (cold start or
    // onNewIntent) and consumed by AppNav after the onboarded check passes.
    // After consumption, AppNav resets it to null so back/process-death won't
    // re-trigger the navigation on the next composition.
    private val pendingDeepLink = MutableStateFlow<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the system can hand off from the
        // splash theme to the post-splash theme cleanly.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Transparent bars whose icons follow the system light/dark setting,
        // which is also what the app theme follows.
        enableEdgeToEdge()
        pendingDeepLink.value = DeepLink.fromIntent(intent)
        val app = application as FoodTrackerApp
        // Seeding, reminder re-arming and backup scheduling hang off an actual
        // UI launch rather than Application.onCreate, which also runs for
        // broadcast- and worker-only process starts.
        app.initializeForUi()
        val container = AppContainer.from(app)
        setContent {
            val hapticsEnabled by container.profileStore.hapticsEnabled
                .collectAsStateWithLifecycle(initialValue = true)
            val defaultHaptic = LocalHapticFeedback.current
            val haptic = if (hapticsEnabled) defaultHaptic else NoOpHapticFeedback
            val themeMode by container.profileStore.themeMode
                .collectAsStateWithLifecycle(initialValue = null)
            // Nothing until the preference is read (a few ms, under the
            // splash), so a dark-mode user never sees a light frame.
            val mode = themeMode ?: return@setContent
            val dark = when (mode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // Status/navigation bar icons follow the app's choice, not the system's.
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            AppTheme(dark = dark) {
                CompositionLocalProvider(
                    LocalAppContainer provides container,
                    LocalHapticFeedback provides haptic,
                ) {
                    AppNav(
                        pendingDeepLink = pendingDeepLink,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Bring widgets up to date whenever the app is foregrounded — cheapest
        // reliable way to catch the date rollover and any edits made elsewhere.
        lifecycleScope.launch {
            com.example.foodtracker.widget.WidgetUpdater.updateAll(applicationContext)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Activity is launchMode="singleTop", so widget taps while the app is
        // already foregrounded land here instead of onCreate. Forward the new
        // target to AppNav via the same one-shot flow.
        DeepLink.fromIntent(intent)?.let { pendingDeepLink.value = it }
    }
}

/** Targets a widget or reminder-notification tap can request. Each variant is matched by AppNav. */
sealed interface DeepLink {
    /** Open LogFoodScreen with [mealName] preselected (or default meal if null). */
    data class LogMeal(val mealName: String?) : DeepLink
    /** Open Today with the water sheet raised — water stopped being a page in phase 4. */
    data object OpenWater : DeepLink
    /** Open Today with the weigh-in sheet raised — same move as water. */
    data object OpenWeight : DeepLink

    companion object {
        const val EXTRA_DEEP_LINK = "com.example.foodtracker.extra.DEEP_LINK"
        const val EXTRA_MEAL = "com.example.foodtracker.extra.MEAL"
        const val TARGET_LOG_MEAL = "log_meal"
        const val TARGET_OPEN_WATER = "open_water"
        const val TARGET_OPEN_WEIGHT = "open_weight"

        /**
         * Reads the deep-link target and strips the extras as it goes.
         *
         * The launching Intent sticks around for the life of the Activity, so
         * without the strip a configuration change replays it through onCreate
         * and re-navigates the user to the reminder's screen every rotation. A
         * genuine tap always arrives as a fresh Intent, so nothing is lost.
         */
        fun fromIntent(intent: Intent?): DeepLink? {
            val target = intent?.getStringExtra(EXTRA_DEEP_LINK) ?: return null
            val meal = intent.getStringExtra(EXTRA_MEAL)
            intent.removeExtra(EXTRA_DEEP_LINK)
            intent.removeExtra(EXTRA_MEAL)
            return when (target) {
                TARGET_LOG_MEAL -> LogMeal(mealName = meal)
                TARGET_OPEN_WATER -> OpenWater
                TARGET_OPEN_WEIGHT -> OpenWeight
                else -> null
            }
        }

        /** Factory used by widget PendingIntents — keeps the extras schema in one place. */
        fun logMealIntent(context: Context, mealName: String?): Intent =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_DEEP_LINK, TARGET_LOG_MEAL)
                if (mealName != null) putExtra(EXTRA_MEAL, mealName)
            }

        fun openWaterIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_DEEP_LINK, TARGET_OPEN_WATER)
            }

        fun openWeightIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_DEEP_LINK, TARGET_OPEN_WEIGHT)
            }
    }
}
