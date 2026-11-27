package com.example.foodtracker.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * One way to portion a food (e.g. "1 idli" = 35 g, "1 cup" = 220 g, "100 g" = 100 g).
 * Foods carry an ordered list of these; the first one is the default. The base
 * FoodEntity.servingGrams/servingLabel always remain the canonical "1 serving"
 * reference — variants are alternate ways to express it.
 */
@Immutable
@Serializable
data class ServingVariant(
    val label: String,
    val grams: Double,
) {
    companion object {
        val Default = ServingVariant("1 serving (100 g)", 100.0)

        /** Build a sensible variant list given the canonical serving + label. */
        fun defaultVariantsFor(servingGrams: Double, servingLabel: String): List<ServingVariant> {
            val canonical = ServingVariant(servingLabel, servingGrams)
            val gramRef = ServingVariant("100 g", 100.0)
            val small = ServingVariant("50 g", 50.0)
            return if (servingGrams == 100.0) listOf(canonical, small)
            else listOf(canonical, gramRef)
        }

        private const val MAX_VARIANTS = 4
        private const val MIN_GRAMS = 1.0
        private const val MAX_GRAMS = 2000.0
        private const val MAX_LABEL_LENGTH = 24

        /**
         * Clean an untrusted variant list — currently only the optional one an
         * AI may return — into something safe to store, or [] to mean "none,
         * use the defaults".
         *
         * Enforces the invariants the rest of the app relies on: whole-gram
         * amounts inside a sane range (the pickers render `grams.toInt()`, so a
         * fractional value would display as one number and log as another), no
         * duplicate labels or sizes, at most [MAX_VARIANTS], and the canonical
         * serving present as the FIRST entry — that is the one the log screens
         * preselect. A list that ends up with fewer than two distinct sizes is
         * no better than the default pair, so it collapses to [].
         */
        fun sanitize(
            raw: List<ServingVariant>,
            servingGrams: Double,
            servingLabel: String,
        ): List<ServingVariant> {
            if (raw.isEmpty()) return emptyList()
            val canonicalGrams = servingGrams.roundToWholeGrams()
            val seenLabels = mutableSetOf<String>()
            val seenGrams = mutableSetOf<Double>()
            val cleaned = raw.mapNotNull { v ->
                val label = v.label.trim().take(MAX_LABEL_LENGTH)
                val grams = v.grams.roundToWholeGrams()
                if (label.isEmpty() || !grams.isFinite()) return@mapNotNull null
                if (grams < MIN_GRAMS || grams > MAX_GRAMS) return@mapNotNull null
                if (!seenLabels.add(label.lowercase(Locale.ROOT))) return@mapNotNull null
                if (!seenGrams.add(grams)) return@mapNotNull null
                ServingVariant(label, grams)
            }
            // The canonical serving has to lead. Promote the model's own entry
            // for it when there is one, so we keep its nicer short label
            // ("Medium" reads better than "1 medium banana (120 g)").
            val canonicalIndex = cleaned.indexOfFirst { it.grams == canonicalGrams }
            val ordered = if (canonicalIndex >= 0) {
                listOf(cleaned[canonicalIndex]) + cleaned.filterIndexed { i, _ -> i != canonicalIndex }
            } else {
                val fallback = servingLabel.trim().take(MAX_LABEL_LENGTH)
                    .ifEmpty { "${canonicalGrams.toInt()} g" }
                listOf(ServingVariant(fallback, canonicalGrams)) + cleaned
            }
            return ordered.take(MAX_VARIANTS).takeIf { it.size >= 2 }.orEmpty()
        }

        private fun Double.roundToWholeGrams(): Double =
            if (isFinite()) Math.round(this).toDouble() else Double.NaN
    }
}
