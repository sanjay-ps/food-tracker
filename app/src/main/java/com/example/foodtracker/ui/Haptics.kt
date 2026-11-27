package com.example.foodtracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * App-facing facade over Compose's [LocalHapticFeedback]. Keeping the call sites
 * on `haptics.confirm() / light() / reject()` (rather than raw
 * `LocalHapticFeedback.current.performHapticFeedback(...)`) gives us:
 *
 *  - **One vocabulary** for the whole app — call sites stay short and semantic.
 *  - **One gating point** at the app root. `MainActivity` swaps the
 *    `LocalHapticFeedback` provider to a no-op `HapticFeedback` when the user
 *    disables haptics in Profile. That single override silences:
 *      • everything routed through this facade,
 *      • Material 3 components that use `LocalHapticFeedback` internally
 *        (e.g. `Slider`'s tick haptics, drag-and-drop, text-handle moves).
 *
 * Mapping rationale (Compose 1.7+ `HapticFeedbackType`):
 *  - [confirm] → `Confirm` — primary action landed (food logged, weight saved).
 *  - [light]   → `ContextClick` — discrete tap on a tap target (chip toggle,
 *    tab switch, stepper button, slider crossing, favourite star).
 *  - [reject]  → `Reject` — validation failure / no-op tap.
 */
class AppHaptics(private val delegate: HapticFeedback) {
    fun confirm() = delegate.performHapticFeedback(HapticFeedbackType.Confirm)
    fun light() = delegate.performHapticFeedback(HapticFeedbackType.ContextClick)
    fun reject() = delegate.performHapticFeedback(HapticFeedbackType.Reject)
}

@Composable
fun rememberAppHaptics(): AppHaptics {
    val delegate = LocalHapticFeedback.current
    return remember(delegate) { AppHaptics(delegate) }
}

/**
 * Shared no-op [HapticFeedback] used by `MainActivity` to silence the whole
 * subtree when the user has haptics off. Exposing it as a top-level singleton
 * keeps recomposition cheap (same instance every frame the flag is `false`).
 */
val NoOpHapticFeedback: HapticFeedback = object : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {}
}
