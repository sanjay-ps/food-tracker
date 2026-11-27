package com.example.foodtracker.widget

import androidx.glance.unit.ColorProvider
import com.example.foodtracker.R
import com.example.foodtracker.ui.theme.DarkError
import com.example.foodtracker.ui.theme.DarkErrorContainer
import com.example.foodtracker.ui.theme.DarkOnErrorContainer
import com.example.foodtracker.ui.theme.MacroCarbs
import com.example.foodtracker.ui.theme.MacroFat
import com.example.foodtracker.ui.theme.MacroProtein

/**
 * Widget color tokens — single source of truth for both live widgets and the
 * static preview drawables.
 *
 * These used to read [androidx.glance.GlanceTheme].colors, which on Android 12+
 * is Material You: colors extracted from the user's wallpaper, the same system
 * Drive and Calendar use. That was a deliberate choice and it is now the wrong
 * one. Open question 1 settled it for phase 1 — the app is one fixed dark
 * palette (decision 11), so a wallpaper-themed widget puts two visual languages
 * next to each other on one home screen, and the hour it costs to stop that is
 * cheaper than living with it. The contrast argument is the stronger half: a
 * wallpaper cannot be held to the ratios the palette is built on, so `primaryContainer`
 * behind `onPrimaryContainer` was legible by convention rather than by
 * construction.
 *
 * The values now come from `res/values/colors_widget.xml`, which mirrors
 * `ui/theme/Color.kt`. Resources rather than inline colors because the static
 * preview drawables and layouts can only reference `@color/widget_*` — routing
 * both through one table is what keeps the picker preview and the placed widget
 * identical. The three tokens below with no preview counterpart skip the
 * resource and name the theme value directly.
 *
 * These are plain vals now, not `@Composable @ReadOnlyComposable` getters:
 * nothing here depends on composition any more.
 *
 * Macro chip tints stay fixed, as before — they are data-viz and need
 * distinguishable hues — but they are the app's macro palette now, not a
 * separate set of Material-palette hexes that happened to live in this file.
 */
object WidgetColors {
    val Surface = ColorProvider(R.color.widget_surface)
    val OnSurface = ColorProvider(R.color.widget_on_surface)

    val PrimaryContainer = ColorProvider(R.color.widget_primary_container)
    val OnPrimaryContainer = ColorProvider(R.color.widget_on_primary_container)

    val Primary = ColorProvider(R.color.widget_primary)

    // No preview drawable references the error state — the picker preview
    // always shows a day under goal — so these name the theme values directly
    // instead of adding three resources nothing else would read.
    val ErrorBg = ColorProvider(DarkErrorContainer)
    val OnErrorBg = ColorProvider(DarkOnErrorContainer)

    /**
     * The GoalLeft battery fill, under-goal and over-goal.
     *
     * Both used to be alpha-blended neutrals — `0x55000000` over a light
     * container, `0x55FFFFFF` over a dark one — because there was no fixed
     * container to pick a solid step against. There is now: [PrimarySubtle] is
     * `AppPrimaryDeep`, one tone above `primaryContainer`, so the fill reads as
     * a level rather than as a smudge, and the big number keeps the same
     * contrast whether or not the fill has reached it.
     *
     * [ErrorSubtle] stays an alpha blend: `error` at full strength is brighter
     * than the `onErrorContainer` text sitting on top of it, and the point of
     * the over-goal state is to be read, not to shout.
     */
    val PrimarySubtle = ColorProvider(R.color.widget_primary_subtle)
    val ErrorSubtle = ColorProvider(DarkError.copy(alpha = 0.35f))

    // Macro chip palette — fixed (not wallpaper-derived) because data viz needs
    // hue separation, and now the *app's* macro hues, so the carbs chip on the
    // home screen is the carbs colour everywhere else in the app. Each chip is
    // a two-layer level meter:
    //   - `*Base` (20% alpha): always-on subtle macro tint over the widget
    //     surface — so even an empty chip reads as "this one is the carbs chip".
    //   - `*Fill` (60% alpha): intensified version that grows from left to
    //     right with progress. The 40% alpha gap is what creates the visible
    //     battery boundary.
    // Alpha, not two tones, so the gap between them is identical for all three
    // macros regardless of how light the hue is.
    val MacroCarbsBase = ColorProvider(MacroCarbs.copy(alpha = 0.20f))
    val MacroCarbsFill = ColorProvider(MacroCarbs.copy(alpha = 0.60f))
    val MacroProteinBase = ColorProvider(MacroProtein.copy(alpha = 0.20f))
    val MacroProteinFill = ColorProvider(MacroProtein.copy(alpha = 0.60f))
    val MacroFatBase = ColorProvider(MacroFat.copy(alpha = 0.20f))
    val MacroFatFill = ColorProvider(MacroFat.copy(alpha = 0.60f))
}
