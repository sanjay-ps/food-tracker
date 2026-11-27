package com.example.foodtracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.example.foodtracker.R

/**
 * The type scale: **Manrope** for headlines and numbers, **Inter** for text.
 *
 * One style per job — pick by role, never by size:
 *
 * | Role | Style |
 * |---|---|
 * | The closing quote on Today | `quote` |
 * | The one hero figure on a screen | `numberXXL` |
 * | A sheet's live readout | `numberXL` |
 * | A figure in a card | `numberL` |
 * | A figure in a dense row/grid | `numberM` |
 * | Page title (tabs, onboarding) | `headline` |
 * | Sheet title | `titleL` |
 * | Card title, list item, pushed-page title | `titleM` |
 * | Paragraphs, inputs | `body` |
 * | Secondary lines, captions, units | `bodyS` |
 * | Buttons | `button` |
 * | Chips, tabs, pills | `label` |
 * | Label above a figure or field | `eyebrow` |
 *
 * Both are Google *downloadable* fonts — fetched once by Play Services and
 * shared across apps, so they cost the APK nothing. Until the download lands
 * (or on a device without Play Services) the system font stands in.
 *
 * Every numeric style carries `tnum` so digits don't jitter while a value
 * animates.
 */

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private fun family(name: String, vararg weights: FontWeight) = FontFamily(
    weights.map { Font(googleFont = GoogleFont(name), fontProvider = provider, weight = it) }
)

val DisplayFont = family(
    "Manrope", FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold,
)
val TextFont = family(
    "Inter", FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold,
)

private const val TNUM = "tnum"

object AppText {
    /** The one number a screen exists for. */
    val numberXXL = TextStyle(
        fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold,
        fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = (-1.5).sp,
        fontFeatureSettings = TNUM,
    )

    val numberXL = TextStyle(
        fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp,
        fontFeatureSettings = TNUM,
    )

    val numberL = TextStyle(
        fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp,
        fontFeatureSettings = TNUM,
    )

    val numberM = TextStyle(
        fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
        fontSize = 17.sp, lineHeight = 22.sp, fontFeatureSettings = TNUM,
    )

    /** The big closing line at the foot of Today. */
    val quote = TextStyle(
        fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold,
        fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-1.2).sp,
    )

    /** Page headings — "Today", "Trends". */
    val headline = TextStyle(
        fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp,
    )

    /** Screen and sheet titles. */
    val titleL = TextStyle(
        fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp,
    )

    /** Card titles, food names. */
    val titleM = TextStyle(
        fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
        fontSize = 16.sp, lineHeight = 22.sp,
    )

    val body = TextStyle(
        fontFamily = TextFont, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp,
    )

    val bodyS = TextStyle(
        fontFamily = TextFont, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TNUM,
    )

    /** Buttons. */
    val button = TextStyle(
        fontFamily = TextFont, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp,
    )

    /** Chips, tabs, pills. */
    val label = TextStyle(
        fontFamily = TextFont, fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp, lineHeight = 18.sp,
    )

    /** Small field labels — "Spent this week", "Protein". */
    val eyebrow = TextStyle(
        fontFamily = TextFont, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.1.sp,
    )
}

/** Material slots pointed at the styles above so stock components inherit them. */
val Typography = Typography(
    displayLarge = AppText.numberXXL,
    displayMedium = AppText.numberL,
    displaySmall = AppText.numberM,
    headlineLarge = AppText.headline,
    headlineMedium = AppText.numberM,
    headlineSmall = AppText.titleL,
    titleLarge = AppText.titleL,
    titleMedium = AppText.titleM,
    titleSmall = AppText.label,
    bodyLarge = AppText.body,
    bodyMedium = AppText.body,
    bodySmall = AppText.bodyS,
    labelLarge = AppText.label,
    labelMedium = AppText.label,
    labelSmall = AppText.eyebrow,
)
