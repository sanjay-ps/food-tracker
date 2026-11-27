package com.example.foodtracker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette — "calm canvas, pastel light".
 *
 * Two tables, light and dark, following the system setting. Light is the
 * default reading: a cool off-white canvas with white cards, ink-black actions
 * and soft pastel gradients behind the hero figures. Dark keeps the same roles
 * on a near-black canvas.
 *
 * Roles:
 *  - `primary` is **ink** — buttons, the add button, selected toggles. Not data.
 *  - `secondary` is **positive** — on-track meters and status cells.
 *  - `tertiary` is the **accent** — today's bar, the wordmark dot, highlights.
 *  - `error` / [StatusOver] — past a limit.
 */

// ---- Light ----------------------------------------------------------------

val LightCanvas = Color(0xFFF4F4F7)
val LightCard = Color(0xFFFFFFFF)
val LightSurfaceLow = Color(0xFFF9F9FB)
val LightSurfaceHigh = Color(0xFFEEEEF2)
val LightSurfaceHighest = Color(0xFFE5E5EB)
val LightInk = Color(0xFF121214)
val LightInkSoft = Color(0xFF6E6E78)
val LightOutline = Color(0xFFD4D4DC)
val LightOutlineVariant = Color(0xFFEAEAF0)

val LightPositive = Color(0xFF1E9E5A)
val LightPositiveContainer = Color(0xFFDDF5E6)
val LightAccent = Color(0xFFF2663A)
val LightAccentContainer = Color(0xFFFFE5DA)
val LightError = Color(0xFFE5484D)
val LightErrorContainer = Color(0xFFFDE3E3)
val LightOnErrorContainer = Color(0xFF8A1C1F)

// ---- Dark -----------------------------------------------------------------

val DarkCanvas = Color(0xFF0B0B0E)
val DarkCard = Color(0xFF17171B)
val DarkSurfaceLow = Color(0xFF121216)
val DarkSurfaceHigh = Color(0xFF222228)
val DarkSurfaceHighest = Color(0xFF2C2C33)
val DarkInk = Color(0xFFF3F3F6)
val DarkInkSoft = Color(0xFF9A9AA6)
val DarkOutline = Color(0xFF3A3A44)
val DarkOutlineVariant = Color(0xFF26262D)

val DarkPositive = Color(0xFF4ADE80)
val DarkPositiveContainer = Color(0xFF12321F)
val DarkAccent = Color(0xFFFF7A4D)
val DarkAccentContainer = Color(0xFF3A1E14)
val DarkError = Color(0xFFFF6B6B)
val DarkErrorContainer = Color(0xFF3B1416)
val DarkOnErrorContainer = Color(0xFFFFD9D9)

// ---- Data palette ---------------------------------------------------------
// One hue per concept, shared by every chart and meter. Mid-tone so the same
// value reads on a white card and on a near-black one.

val MacroCarbs = Color(0xFFE27A0C)     // saffron  
val MacroProtein = Color(0xFFC2257A)   // rose     
val MacroFat = Color(0xFF5B3FD6)       // indigo   
val MacroFiber = Color(0xFF0E9466)     // emerald  
val MacroWater = Color(0xFF2468E8)     // blue     
val MacroBurned = Color(0xFFF0433A)    // calories 
val MacroWeight = Color(0xFF0F8A86)    // teal     

// ---- Status ---------------------------------------------------------------

val StatusOver = Color(0xFFF97316)     // orange — past a limit

// ---- BMI bands ------------------------------------------------------------

val BmiUnderweight = Color(0xFF3B8EF5)
val BmiNormal = Color(0xFF22C55E)
val BmiOverweight = Color(0xFFF5A524)
val BmiObese = Color(0xFFEF4444)
