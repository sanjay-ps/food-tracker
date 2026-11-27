package com.example.foodtracker.reminder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Doze respects battery — that's by design. But if the OS is also putting the
 * app under "app standby" / OEM aggressive battery saver, even
 * `setAndAllowWhileIdle` can be delayed beyond the usability threshold for meal
 * reminders. The fix the user must take is to mark the app exempt from battery
 * optimisation in system settings; we surface a prompt that deeplinks them
 * there without auto-prompting (auto-prompt requires
 * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS which Play restricts to alarm-clock-like
 * apps).
 */
object BatteryOptHelper {

    /**
     * True when the app is currently exempt from battery optimisation — i.e.
     * Doze maintenance windows will fire its alarms close to the requested
     * time. Returns true on API < 23 where the concept doesn't exist.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Open the system Battery Optimisation screen targeted at this app. Falls
     * back to the generic list if the per-app deeplink isn't supported. We do
     * not request the auto-prompt intent — that's Play-policy-restricted.
     */
    fun openSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        // The per-app deeplink (data = package:<id>) opens the Settings entry
        // directly on most OEMs. The generic action is the fallback when that
        // intent isn't resolved.
        val perApp = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val generic = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(generic) }
            .onFailure { runCatching { context.startActivity(perApp) } }
    }
}

/**
 * Reactive view of [BatteryOptHelper.isIgnoringBatteryOptimizations]. Re-checks
 * every time the host lifecycle hits `ON_RESUME` so returning from the system
 * settings screen flips the value without a manual refresh.
 */
@Composable
fun rememberBatteryOptExempt(): State<Boolean> {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state = remember { mutableStateOf(BatteryOptHelper.isIgnoringBatteryOptimizations(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.value = BatteryOptHelper.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}
