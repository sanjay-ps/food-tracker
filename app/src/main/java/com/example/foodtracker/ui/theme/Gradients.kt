package com.example.foodtracker.ui.theme

import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The soft "mesh" gradient behind hero cards: a pale base with four large
 * radial blobs (lavender, pink, peach, mint). With [animate] the blobs drift
 * on a slow 14-second loop — movement you feel more than see. [parallax]
 * (a scroll offset in px, read at draw time) shifts the blobs at half speed.
 */
fun Modifier.meshGradient(
    animate: Boolean = true,
    parallax: () -> Float = { 0f },
): Modifier = composed {
    val mesh = AppTheme.extras.mesh
    val t = if (animate) {
        val transition = rememberInfiniteTransition(label = "mesh")
        val v by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart),
            label = "meshT",
        )
        v
    } else 0f
    drawBehind {
        drawRect(mesh[0])
        val w = size.width
        val h = size.height
        val r = maxOf(w, h) * 0.75f
        val a = (t * 2 * PI).toFloat()
        // Blobs drift down at half the scroll speed, so the colour field
        // moves slower than the card it is painted on.
        val p = parallax() * 0.6f
        // (anchor x, anchor y, phase) per blob; each orbits its anchor a little.
        val blobs = listOf(
            Triple(0.10f, 0.05f, 0f),
            Triple(0.95f, 0.35f, 1.6f),
            Triple(0.70f, 1.05f, 3.1f),
            Triple(0.05f, 0.95f, 4.7f),
        )
        blobs.forEachIndexed { i, (ax, ay, phase) ->
            val c = Offset(
                w * ax + cos(a + phase) * w * 0.06f,
                h * ay + sin(a + phase) * h * 0.08f + p,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(mesh[i + 1], mesh[i + 1].copy(alpha = 0f)),
                    center = c,
                    radius = r,
                ),
                radius = r,
                center = c,
            )
        }
    }
}

/**
 * Pastel tile colours, one per data concept. Each has a light and a dark
 * reading: a two-stop gradient and an "ink" for the icon and figures on it.
 */
enum class Pastel(
    private val light: Triple<Color, Color, Color>,
    private val dark: Triple<Color, Color, Color>,
) {
    Peach(
        Triple(Color(0xFFFFEBCF), Color(0xFFFFD6AE), Color(0xFF9A5A07)),
        Triple(Color(0xFF3A2A14), Color(0xFF4A3018), Color(0xFFFFD39A)),
    ),
    Rose(
        Triple(Color(0xFFFFE0DA), Color(0xFFFFC9D9), Color(0xFFA53628)),
        Triple(Color(0xFF3D1C1A), Color(0xFF47202C), Color(0xFFFFB8A8)),
    ),
    Lavender(
        Triple(Color(0xFFECE6FF), Color(0xFFDACFFF), Color(0xFF5440C2)),
        Triple(Color(0xFF282245), Color(0xFF332A5A), Color(0xFFCDBFFF)),
    ),
    Mint(
        Triple(Color(0xFFE0F5E8), Color(0xFFC9EED9), Color(0xFF1C6E43)),
        Triple(Color(0xFF15301F), Color(0xFF1A3A27), Color(0xFFA6EBC1)),
    ),
    Sky(
        Triple(Color(0xFFDDEEFF), Color(0xFFC7E1FF), Color(0xFF1C64AD)),
        Triple(Color(0xFF142A40), Color(0xFF18324D), Color(0xFFA9D2FF)),
    ),
    Teal(
        Triple(Color(0xFFD8F3EF), Color(0xFFC2EAE4), Color(0xFF0F6B61)),
        Triple(Color(0xFF12312D), Color(0xFF163B36), Color(0xFF9EE6DC)),
    ),
    Sand(
        Triple(Color(0xFFF3EEE6), Color(0xFFEAE2D6), Color(0xFF6B5A43)),
        Triple(Color(0xFF26231F), Color(0xFF2E2A25), Color(0xFFE2D5C2)),
    );

    private val tone: Triple<Color, Color, Color>
        @Composable get() = if (AppTheme.extras.isDark) dark else light

    val brush: Brush
        @Composable get() = tone.let { Brush.linearGradient(listOf(it.first, it.second)) }

    /** The flat fill, for small discs where a gradient would not read. */
    val fill: Color
        @Composable get() = tone.first

    val ink: Color
        @Composable get() = tone.third
}

/**
 * A tiled field of small dots over a background — the printed-paper texture on
 * gradient cards. One dot is drawn into a tiny bitmap once and repeated by a
 * shader, then faded out across the card so it reads as texture, not pattern.
 * [parallax] (px) shifts the dots at a fraction of the scroll for depth.
 */
fun Modifier.dotTexture(
    color: Color = Color.White.copy(alpha = 0.22f),
    spacing: Dp = 7.dp,
    radius: Dp = 0.9.dp,
    parallax: () -> Float = { 0f },
): Modifier = drawWithCache {
    val cell = spacing.toPx().roundToInt().coerceAtLeast(2)
    val tile = ImageBitmap(cell, cell)
    Canvas(tile).drawCircle(
        Offset(cell / 2f, cell / 2f),
        radius.toPx(),
        Paint().apply { this.color = color; isAntiAlias = true },
    )
    val dots = ImageShader(tile, TileMode.Repeated, TileMode.Repeated)
    // Dots strongest at the top-right, gone by the bottom-left.
    val fade = LinearGradientShader(
        from = Offset(size.width, 0f),
        to = Offset(0f, size.height),
        colors = listOf(Color.White, Color.White.copy(alpha = 0.35f), Color.Transparent),
    )
    val brush = ShaderBrush(android.graphics.ComposeShader(dots, fade, android.graphics.BlendMode.DST_IN))
    onDrawBehind {
        val shift = -parallax() * 0.3f
        translate(top = shift % cell) { drawRect(brush, topLeft = Offset(0f, -cell.toFloat()), size = Size(size.width, size.height + 2 * cell)) }
    }
}

/**
 * Soft per-nutrient gradients — one hue per thing the app measures, kept
 * pastel so a screen of them stays calm. Each has a light and a dark reading
 * and an [ink]: the deep shade of the same hue for icons, labels and meters on
 * the tile. Figures on a tile use the normal `onSurface`.
 */
enum class Rich(
    private val light: List<Long>,
    private val lightInk: Long,
    private val dark: List<Long>,
    private val darkInk: Long,
) {
    /** Calories — peach to apricot. */
    Energy(listOf(0xFFFFE6DA, 0xFFFFD5C6, 0xFFFFE9CF), 0xFFB23A1E, listOf(0xFF3A1F18, 0xFF46241C, 0xFF3E2A17), 0xFFFFB59E),
    /** Protein — blush to rose. */
    Protein(listOf(0xFFFCE4EE, 0xFFF8D0E2, 0xFFFFDDE7), 0xFF9C1F5E, listOf(0xFF3B1A2A, 0xFF46203A, 0xFF3D1D2C), 0xFFF5A8CB),
    /** Carbs — cream to saffron. */
    Carbs(listOf(0xFFFFF2DA, 0xFFFFE4B8, 0xFFFFEDCB), 0xFF94570A, listOf(0xFF3A2C12, 0xFF45331A, 0xFF3B2E16), 0xFFF7C77A),
    /** Fat — lilac to lavender. */
    Fat(listOf(0xFFEFEBFF, 0xFFDFD7FF, 0xFFEBE5FF), 0xFF4B39B8, listOf(0xFF25213F, 0xFF2E2850, 0xFF2A2346), 0xFFC6B8FF),
    /** Fiber — mint to sage. */
    Fiber(listOf(0xFFE4F6EB, 0xFFD0EEDD, 0xFFE5F4E6), 0xFF1B6E46, listOf(0xFF15301F, 0xFF1A3B27, 0xFF183424), 0xFF9FE3BD),
    /** Water — mist to sky. */
    Water(listOf(0xFFE3F0FF, 0xFFCFE4FF, 0xFFE0F1FF), 0xFF1F5FB0, listOf(0xFF152A42, 0xFF1A3352, 0xFF172E48), 0xFFA6CEFF),
    /** Weight — seafoam. */
    Weight(listOf(0xFFDEF4F0, 0xFFC9ECE6, 0xFFE0F3EF), 0xFF0E6B62, listOf(0xFF12302D, 0xFF163B37, 0xFF143531), 0xFF96E2D6),
    /** Vitamins & minerals — lilac through pink to peach. */
    Micros(listOf(0xFFF1E8FF, 0xFFFFE2F1, 0xFFFFECDF), 0xFF7A2E8E, listOf(0xFF2C1E3E, 0xFF3A1F36, 0xFF3A2A22), 0xFFE4B5F2),
    /** Streaks — apricot to butter. */
    Streak(listOf(0xFFFFEADA, 0xFFFFD9C7, 0xFFFFEEC6), 0xFFB0441A, listOf(0xFF3C2015, 0xFF472619, 0xFF40301A), 0xFFFFB894);

    val brush: Brush
        @Composable get() = Brush.linearGradient((if (AppTheme.extras.isDark) dark else light).map { Color(it) })

    val ink: Color
        @Composable get() = Color(if (AppTheme.extras.isDark) darkInk else lightInk)

    /** The hue's one flat pastel (the middle of [brush]), for blending several hues in one gradient. */
    val tint: Color
        @Composable get() = Color((if (AppTheme.extras.isDark) dark else light)[1])
}
