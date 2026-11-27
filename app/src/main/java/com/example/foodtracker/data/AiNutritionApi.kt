package com.example.foodtracker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * AI nutrition reply: a typical serving for the food plus that serving's
 * nutrients. [nutrients] are PER SERVING (not per 100 g).
 */
data class AiNutrition(
    val servingGrams: Double,
    val servingLabel: String,
    val category: FoodCategory,
    val nutrients: Nutrients,
    /**
     * Optional named sizes for foods that genuinely come in them (a small /
     * medium / large banana). Empty for the common case, which leaves
     * [com.example.foodtracker.data.db.FoodEntity.fromNutrientsPer100g] to
     * build its usual canonical + gram-reference pair. Already passed through
     * [ServingVariant.sanitize], so the canonical serving leads the list.
     *
     * These are gram amounts only — [nutrients] always describes
     * [servingGrams], never a variant.
     */
    val servingVariants: List<ServingVariant> = emptyList(),
)

/**
 * Optional AI nutrition lookup. Given a food name, asks the user's chosen LLM
 * provider ([AiProvider]) for per-100 g nutrition and maps the reply into
 * [Nutrients] for the add-food form to pre-fill.
 *
 * This is strictly opt-in: the caller only reaches here when the user has
 * enabled AI in settings AND supplied their own API key — there is NO bundled
 * token, so nothing is ever sent to a third party by default. Mirrors the
 * raw-`HttpURLConnection` + `kotlinx.serialization` style of [UsdaFoodApi],
 * with a POST body instead of a GET query. All failures return null so the
 * form silently falls back to manual entry.
 */
/** The nutrient keys every reply must carry, in the order the prompt lists them. */
private val NUTRIENT_KEYS = listOf(
    "calories", "carbs", "sugar", "fiber", "protein", "fat", "saturatedFat",
    "omega3", "cholesterol", "sodium", "calcium", "iron", "magnesium",
    "potassium", "zinc", "phosphorus", "selenium", "copper", "manganese",
    "vitaminA", "vitaminC", "vitaminD", "vitaminE", "vitaminK", "vitaminB12",
    "folate", "vitaminB1", "vitaminB2", "vitaminB3", "vitaminB6",
)

/**
 * Sub-components of fiber and protein. Left out of the schema's `required`
 * list so a food with no published breakdown — a composite dish — can omit
 * them rather than invent one; a reply that omits them leaves the fields at
 * 0, which the app reads as unknown and hides.
 *
 * Optional in the schema is NOT the same as discouraged in the prompt, and
 * conflating the two is what made these fields come back empty for everything
 * at first. A model will skip a non-required key unless asked for it, so the
 * prompt asks for all four by default and names omission as the exception.
 * [Nutrients.withOptionalDetail] is what keeps that from turning into
 * invention: it re-derives the arithmetic and drops any group that fails, so
 * the prompt can afford to ask broadly.
 */
private val OPTIONAL_NUTRIENT_KEYS = listOf(
    "solubleFiber", "insolubleFiber", "essentialAminoAcids", "leucine",
)

/**
 * The smallest sub-nutrient amount treated as present, in grams — a microgram.
 *
 * A bare `> 0.0` test is not the same question. One reply carried
 * `solubleFiber = 1.65e-308`, a subnormal double that is zero for every
 * practical purpose yet passes `> 0.0`, which let a non-answer through the
 * presence check to be caught only by the arithmetic that follows. Nothing
 * edible contains a microgram of fiber, so anything under this is a glitch
 * rather than a measurement.
 */
private const val MIN_DETAIL_G = 1e-6

class AiNutritionApi {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Which concrete model last answered, as the provider reports it. Gemini
     * returns `modelVersion`, so an alias like `gemini-flash-lite-latest`
     * names what it actually resolved to. Also logged: when Google moves the
     * alias underneath a release, this is the only signal that it did.
     */
    @Volatile
    var lastModelVersion: String? = null
        private set

    /**
     * Three tries in total, then give up — about four seconds of waiting in
     * the worst case. A provider shedding load on one model stays down for
     * minutes, so retrying harder than this only makes the user wait longer
     * for the same failure; the error text points them at another model
     * instead.
     */
    private val RETRY_DELAYS_MS = longArrayOf(1_000L, 3_000L)
    private val MAX_ATTEMPTS = RETRY_DELAYS_MS.size + 1

    /**
     * Human-readable reason for the most recent failure (set on the fetch path),
     * so the UI can distinguish a rejected key from a quota/rate limit or a
     * network drop. Null after a success. Single active request at a time.
     */
    @Volatile
    var lastError: String? = null
        private set

    // ─── Diagnostics ────────────────────────────────────────────────────────
    //
    // Which pass is in flight, so every attempt it makes can be recorded
    // against the right model without threading four parameters through the
    // HTTP layer. Safe because one request runs at a time — the same
    // assumption [lastError] already makes, and the one the add-food screen
    // enforces by refusing a second Ask AI while aiStep != Idle.

    @Volatile private var passName: String = "Look up"
    @Volatile private var passProvider: String = ""
    @Volatile private var passModel: String = ""
    @Volatile private var passSubject: String? = null

    private fun beginPass(
        pass: String,
        provider: AiProvider,
        model: String,
        foodName: String,
        brand: String,
    ) {
        passName = pass
        passProvider = provider.displayName
        passModel = model
        passSubject = if (brand.isBlank()) foodName else "$foodName — $brand"
    }

    private fun logAttempt(
        attempt: Int,
        outcome: AiLogOutcome,
        code: Int?,
        durationMs: Long,
        detail: String?,
    ) {
        AiLog.record(
            AiLogEntry(
                atMillis = System.currentTimeMillis(),
                pass = passName,
                provider = passProvider,
                model = passModel,
                attempt = attempt,
                attemptsAllowed = MAX_ATTEMPTS,
                outcome = outcome,
                httpCode = code,
                durationMs = durationMs,
                subject = passSubject,
                detail = AiLog.redact(detail),
            ),
        )
    }

    /**
     * A 200 whose body we could not turn into nutrition. Logged separately
     * from the HTTP attempt that fetched it, because the two fail for opposite
     * reasons and the fix is different: this one is the model answering in
     * prose, or dropping the macros, and it is the case the user-facing
     * message can say the least about.
     */
    private fun logParseFailure(reply: String) {
        AiLog.record(
            AiLogEntry(
                atMillis = System.currentTimeMillis(),
                pass = passName,
                provider = passProvider,
                model = passModel,
                attempt = 1,
                attemptsAllowed = 1,
                outcome = AiLogOutcome.ParseError,
                subject = passSubject,
                detail = AiLog.redact("Reply did not parse into nutrition:\n$reply"),
            ),
        )
    }

    suspend fun fetchNutrition(
        foodName: String,
        brand: String = "",
        instructions: String = "",
        provider: AiProvider,
        model: String,
        apiKey: String,
    ): AiNutrition? = withContext(Dispatchers.IO) {
        if (foodName.isBlank() || apiKey.isBlank()) return@withContext null
        lastError = null
        // Resolve against the provider's model list; a stale/blank id falls back
        // to the provider's default.
        val modelId = provider.modelFromId(model).id
        beginPass("Look up", provider, modelId, foodName.trim(), brand.trim())
        val prompt = buildPrompt(foodName.trim(), brand.trim(), instructions.trim(), provider.enforcesSchema)
        val reply = when (provider) {
            AiProvider.Gemini -> callGemini(prompt, modelId, apiKey)
            AiProvider.Claude -> callClaude(prompt, modelId, apiKey)
            AiProvider.OpenAI -> callOpenAi(prompt, modelId, apiKey)
        } ?: return@withContext null
        val parsed = parseResult(reply)
        if (parsed == null) {
            if (lastError == null) {
                lastError = "The AI reply couldn't be read. Try again or pick another model."
            }
            logParseFailure(reply)
        }
        parsed
    }

    /**
     * Second pass: hand the first result back to the model as a fact-checker and
     * ask it to correct any wrong/implausible values, returning the same schema.
     * Returns null on failure so the caller keeps the already-prefilled values.
     */
    suspend fun verifyNutrition(
        foodName: String,
        brand: String = "",
        candidate: AiNutrition,
        instructions: String = "",
        provider: AiProvider,
        model: String,
        apiKey: String,
    ): AiNutrition? = withContext(Dispatchers.IO) {
        if (foodName.isBlank() || apiKey.isBlank()) return@withContext null
        val modelId = provider.modelFromId(model).id
        beginPass("Verify", provider, modelId, foodName.trim(), brand.trim())
        val prompt = buildVerifyPrompt(
            foodName.trim(), brand.trim(), candidate, instructions.trim(), provider.enforcesSchema,
        )
        val reply = when (provider) {
            AiProvider.Gemini -> callGemini(prompt, modelId, apiKey)
            AiProvider.Claude -> callClaude(prompt, modelId, apiKey)
            AiProvider.OpenAI -> callOpenAi(prompt, modelId, apiKey)
        } ?: return@withContext null
        val verified = parseResult(reply)
        if (verified == null) logParseFailure(reply)
        verified?.withDetailCarriedFrom(candidate)
    }

    /**
     * Keep the first pass's fiber split and amino profile when the
     * verification pass drops them, rescaled to whatever totals it settled on.
     *
     * The verifier regenerates all thirty-odd keys from scratch, and the four
     * optional ones are the first to fall out of a small model's reply — they
     * are the only keys nothing in the schema forces it to emit. Measured on
     * cooked green peas: the first pass returned a complete, gate-passing
     * breakdown and the verifier returned none of it, which `applyResult` in
     * the add-food view model then wrote over the top of the good values. Two
     * prompt revisions did not move it, including one that put the figures in
     * front of the model and told it to keep them, so this is enforced here
     * instead of asked for there.
     *
     * Carried as RATIOS, never as amounts. The verifier's job includes fixing
     * the serving size, and it uses it — on that same lookup it rescaled a
     * 100 g serving to a 160 g cup, multiplying every figure by 1.6 while
     * leaving the food's per-gram composition identical. Copying grams across
     * would have been wrong by 60%; copying `soluble/fiber` and
     * `eaa/protein` is right at either size, because those fractions are
     * properties of the food rather than of the portion.
     *
     * The verifier's own detail wins whenever it supplies some: it has already
     * been through [withOptionalDetail], so it is as validated as what it
     * would replace, and it reflects any genuine correction it made. This only
     * fills a gap — it never overrides an answer.
     */
    private fun AiNutrition.withDetailCarriedFrom(first: AiNutrition): AiNutrition {
        val prior = first.nutrients
        var n = nutrients

        if (!n.hasFiberSplit() && prior.hasFiberSplit() && prior.fiber > 0.0 && n.fiber > 0.0) {
            n = n.copy(
                solubleFiber = n.fiber * (prior.solubleFiber / prior.fiber),
                insolubleFiber = n.fiber * (prior.insolubleFiber / prior.fiber),
            )
        }
        if (!n.hasAminoAcids() && prior.hasAminoAcids() && prior.protein > 0.0 && n.protein > 0.0) {
            val eaa = n.protein * (prior.essentialAminoAcids / prior.protein)
            n = n.copy(
                essentialAminoAcids = eaa,
                leucine = eaa * (prior.leucine / prior.essentialAminoAcids),
            )
        }

        return if (n === nutrients) this else copy(nutrients = n)
    }

    // ─── Prompt ───────────────────────────────────────────────────────────

    /**
     * The part of the contract Gemini gets for free from its
     * `response_schema` — the JSON-only instruction, the category values and
     * the required-key list. Sent only to providers whose reply shape is not
     * enforced server-side, which is where it still has to be said.
     */
    private fun jsonContract(schemaEnforced: Boolean): String =
        if (schemaEnforced) "" else """
        Return ONLY a compact JSON object, no markdown and no explanation.
        category must be exactly one of: SouthIndian, NorthIndian, Snack,
        Beverage, Dairy, Fruit, Vegetable, Grain, Protein, Packaged, Other.
        Required keys: servingGrams, servingLabel, category, ${NUTRIENT_KEYS.joinToString(", ")}.
        """.trimIndent()

    private fun buildPrompt(
        foodName: String,
        brand: String,
        instructions: String,
        schemaEnforced: Boolean,
    ): String = """
        You are a nutrition database. The user is in India — prefer Indian
        food-composition data (IFCT) over Western tables wherever it exists.

        Food: "$foodName"
        ${brandPrompt(brand)}
        Pick ONE typical Indian household serving of this food, then give that
        serving's nutrition. Assume a common South Indian preparation (Tamil
        Nadu, Kerala, Karnataka) unless the name says otherwise, with real
        proportions of rice, lentils, coconut, oil, ghee and tempering. Never
        substitute a Western recipe for an Indian dish.

        - servingGrams: that serving's weight in grams.
        - servingLabel: names the serving and ends with its grams, like
          "1 medium (120 g)".
        - servingVariants: OPTIONAL and usually omitted. Only for a food eaten
          in distinct named sizes someone would ask for. Omit it for anything
          spooned or measured out, anything sold as a fixed unit, and whenever
          unsure. If included, give 2-4 {label, grams} with one entry's grams
          EQUAL to servingGrams.

        Every nutrient value is FOR THAT ONE SERVING — never per 100 g, never
        scaled to a variant.

        Accuracy:
        - calories must agree with 4xcarbs + 4xprotein + 9xfat to within 10%.
          If they disagree, correct the nutrients.
        - Use 0 only when a nutrient is genuinely absent or no reliable value
          exists, never as a guessed trace. Keep small real values, especially
          zinc, iron, copper, potassium, calcium, vitamin C and vitamin K.

        Composition detail — solubleFiber, insolubleFiber,
        essentialAminoAcids, leucine, all in grams for the same serving.
        INCLUDE all four whenever published composition data exists for this
        food: every single ingredient, and every grain, pulse, dairy, egg,
        meat, nut, seed, fruit and vegetable, raw or plainly cooked. These are
        standard table values, not a judgement call — give them.
        Fiber split: give solubleFiber and insolubleFiber, which add up to
        fiber, the figure you gave above, for this serving. If you are sure
        of only one of the two, give that one alone and omit the other —
        do not pad it out to make the pair add up.

        Amino acids: essentialAminoAcids is the sum of all nine essential
        amino acids — histidine, isoleucine, leucine, lysine, methionine,
        phenylalanine, threonine, tryptophan, valine — and leucine is the
        leucine among them. Use this food's own composition table where you
        have it. Otherwise a typical figure for its family is fine: as a
        share of protein, about 0.29 for wheat, 0.35 for oats, 0.37 for rice,
        0.36-0.39 for pulses and soy, 0.41 for meat, 0.45 for egg and 0.47
        for dairy, with a green or immature legume lower than a dry pulse.
        leucine is about 0.19-0.21 of essentialAminoAcids across all of them.
        Pick the family this food actually belongs to rather than rounding to
        the middle of the range.

        Omit any pair that does not apply, and all four for a composite dish
        with no published breakdown.

        Units — kcal: calories. grams: carbs, sugar, fiber, protein, fat,
        saturatedFat, omega3. milligrams: cholesterol, sodium, calcium, iron,
        magnesium, potassium, zinc, phosphorus, copper, manganese, vitaminC,
        vitaminE, vitaminB1, vitaminB2, vitaminB3, vitaminB6. micrograms:
        selenium, vitaminA, vitaminD, vitaminK, vitaminB12, folate.
        ${jsonContract(schemaEnforced)}
        ${instructionsPrompt(instructions)}
    """.trimIndent()

    private fun buildVerifyPrompt(
        foodName: String,
        brand: String,
        candidate: AiNutrition,
        instructions: String,
        schemaEnforced: Boolean,
    ): String = """
        You are a nutrition fact-checker. Below is a proposed profile for ONE
        SERVING of a food. Correct every field that is wrong or implausible and
        return the whole object.

        Food: "$foodName"
        ${brandPrompt(brand)}
        Proposed: ${candidateJson(candidate)}

        Check:
        - If a brand is named, the values are that product's own label figures
          and not a generic version of the food.
        - Otherwise the values match Indian food-composition data (IFCT) where
          it exists.
        - servingGrams is a realistic single serving and servingLabel matches it.
        - The dish is a South Indian household preparation unless its name says
          otherwise — never a Western substitute, and with the coconut, lentil,
          oil, ghee and tempering proportions that implies.
        - calories agree with 4xcarbs + 4xprotein + 9xfat to within 10%.
        - No per-100 g value has been copied into a per-serving field.
        - servingVariants, if present, are sizes this food really comes in, with
          one entry's grams equal to servingGrams. Drop the key otherwise; add
          it only if the food plainly has 2-4 recognised sizes.
        - 0 only for a genuinely absent or unavailable nutrient, never a guessed
          trace. Same units as given.
        - Composition detail (solubleFiber, insolubleFiber,
          essentialAminoAcids, leucine): KEEP the values given above. If you
          change the serving size or correct fiber or protein, rescale them
          by the same factor so solubleFiber + insolubleFiber still equals
          fiber. Add them only from this food's own composition table, never
          as a percentage of protein. Drop them only for a composite dish
          with no published breakdown.
        ${jsonContract(schemaEnforced)}
        ${instructionsPrompt(instructions)}
    """.trimIndent()

    /**
     * The brand, when the user typed one.
     *
     * A branded packaged food is a different question from the dish: Britannia
     * Marie Gold has a printed label and a fixed biscuit weight, and answering
     * it with a generic "marie biscuit" row is wrong in both the serving and
     * the sodium. So the brand is stated as the product's identity and the
     * model is told to prefer its label over a composition table — but told
     * explicitly not to invent one, because a made-up label reads exactly like
     * a real one and the user has no way to tell.
     */
    private fun brandPrompt(brand: String): String =
        if (brand.isBlank()) "" else """
        Brand: "$brand" — this is that brand's own packaged product, not a
        homemade or generic version. Use the figures from its published
        nutrition label and its printed pack/piece size where you know them,
        in preference to any food-composition table. If you do not know this
        particular product, say nothing about the brand and fall back to the
        generic food rather than inventing label values.
        """.trimIndent()

    private fun instructionsPrompt(instructions: String): String =
        if (instructions.isBlank()) "" else "Additional user instructions (use as context, not as a nutrient value): $instructions"

    private fun candidateJson(c: AiNutrition): String = buildJsonObject {
        put("servingGrams", c.servingGrams)
        put("servingLabel", c.servingLabel)
        put("category", c.category.name)
        val n = c.nutrients
        put("calories", n.calories); put("carbs", n.carbs); put("sugar", n.sugar)
        put("fiber", n.fiber); put("protein", n.protein); put("fat", n.fat)
        put("saturatedFat", n.saturatedFat); put("omega3", n.omega3)
        put("cholesterol", n.cholesterol); put("sodium", n.sodium)
        put("calcium", n.calcium); put("iron", n.iron); put("magnesium", n.magnesium)
        put("potassium", n.potassium); put("zinc", n.zinc); put("phosphorus", n.phosphorus)
        put("selenium", n.selenium); put("copper", n.copper); put("manganese", n.manganese)
        put("vitaminA", n.vitaminA); put("vitaminC", n.vitaminC); put("vitaminD", n.vitaminD)
        put("vitaminE", n.vitaminE); put("vitaminK", n.vitaminK); put("vitaminB12", n.vitaminB12)
        put("folate", n.folate); put("vitaminB1", n.vitaminB1); put("vitaminB2", n.vitaminB2)
        put("vitaminB3", n.vitaminB3); put("vitaminB6", n.vitaminB6)
        // Only when known — sending zeros would invite the fact-checker to
        // "correct" an absent breakdown into an invented one.
        if (n.hasFiberSplit()) {
            put("solubleFiber", n.solubleFiber); put("insolubleFiber", n.insolubleFiber)
        }
        if (n.hasAminoAcids()) {
            put("essentialAminoAcids", n.essentialAminoAcids); put("leucine", n.leucine)
        }
    }.toString()

    // ─── Provider calls ─────────────────────────────────────────────────────

    /**
     * The exact object every reply must return, handed to Gemini as a
     * `response_schema` so the server enforces it rather than the prompt
     * asking nicely.
     *
     * This is what holds replies steady across a model swap: the Gemini ids
     * in [AiProvider] are `-latest` aliases, so Google can move the model
     * underneath a release, and a schema makes the SHAPE — every key present,
     * numbers as numbers, category from the fixed list — a property of the
     * request instead of of whichever model answers. It cannot do anything
     * about the VALUES; those are the model's, and no request setting makes
     * two different models agree on how much iron is in a dosa.
     */
    private fun nutritionSchema(): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            putJsonObject("servingGrams") { put("type", "NUMBER") }
            putJsonObject("servingLabel") { put("type", "STRING") }
            putJsonObject("category") {
                put("type", "STRING")
                putJsonArray("enum") {
                    FoodCategory.entries
                        .filter { it != FoodCategory.Custom }
                        .forEach { add(it.name) }
                }
            }
            // Deliberately absent from `required` — a food with no named
            // sizes omits it, which is the common case.
            putJsonObject("servingVariants") {
                put("type", "ARRAY")
                putJsonObject("items") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("label") { put("type", "STRING") }
                        putJsonObject("grams") { put("type", "NUMBER") }
                    }
                    putJsonArray("required") { add("label"); add("grams") }
                }
            }
            NUTRIENT_KEYS.forEach { key -> putJsonObject(key) { put("type", "NUMBER") } }
            // Declared so a model that HAS the data can return it, but kept
            // out of `required` below so one that doesn't simply omits them.
            OPTIONAL_NUTRIENT_KEYS.forEach { key -> putJsonObject(key) { put("type", "NUMBER") } }
        }
        putJsonArray("required") {
            add("servingGrams"); add("servingLabel"); add("category")
            NUTRIENT_KEYS.forEach { add(it) }
        }
    }

    private suspend fun callGemini(prompt: String, model: String, apiKey: String): String? {
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", prompt) }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("response_mime_type", "application/json")
                put("response_schema", nutritionSchema())
            }
        }.toString()
        val resp = post(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            mapOf("x-goog-api-key" to apiKey),
            body,
        ) ?: return null
        return runCatching {
            val root = json.parseToJsonElement(resp).jsonObject
            root["modelVersion"]?.jsonPrimitive?.contentOrNull?.let { resolved ->
                lastModelVersion = resolved
                if (resolved != model) {
                    // Only ever true for an alias id — this is the alias
                    // naming the model it resolved to today.
                    android.util.Log.i("AiNutritionApi", "$model resolved to $resolved")
                }
            }
            root["candidates"]
                ?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    private suspend fun callClaude(prompt: String, model: String, apiKey: String): String? {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 1024)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", prompt)
                }
            }
        }.toString()
        val resp = post(
            "https://api.anthropic.com/v1/messages",
            mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"),
            body,
        ) ?: return null
        return runCatching {
            json.parseToJsonElement(resp).jsonObject["content"]
                ?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    private suspend fun callOpenAi(prompt: String, model: String, apiKey: String): String? {
        val body = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", prompt)
                }
            }
            putJsonObject("response_format") { put("type", "json_object") }
        }.toString()
        val resp = post(
            "https://api.openai.com/v1/chat/completions",
            mapOf("Authorization" to "Bearer $apiKey"),
            body,
        ) ?: return null
        return runCatching {
            json.parseToJsonElement(resp).jsonObject["choices"]
                ?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    // ─── HTTP + parsing ─────────────────────────────────────────────────────

    /**
     * Outcome of a single HTTP attempt.
     *
     * [code] and [durationMs] are carried out of the attempt even on success,
     * because they are what [AiLog] records — a lookup that took 41 seconds and
     * worked is the thing you want to see after the next one times out.
     */
    private sealed interface PostResult {
        val code: Int?
        val durationMs: Long

        data class Ok(
            val body: String,
            override val code: Int?,
            override val durationMs: Long,
        ) : PostResult

        data class Failed(
            val message: String,
            val retryable: Boolean,
            override val code: Int?,
            override val durationMs: Long,
            /** The provider's own reply body, or the exception. Redacted on record. */
            val detail: String?,
        ) : PostResult
    }

    /**
     * POST with a short retry on transient upstream failures.
     *
     * Providers routinely answer 503 "model is experiencing high demand" or
     * 429 for a model that is momentarily hot, and the read timeout fires on a
     * slow first token. Those are worth a second look; a rejected key or an
     * unknown model is not, so only [isRetryable] codes and network errors are
     * retried. [lastError] keeps the final failure so the UI still explains
     * what went wrong.
     */
    private suspend fun post(urlStr: String, headers: Map<String, String>, body: String): String? {
        var attempt = 0
        while (true) {
            when (val result = postOnce(urlStr, headers, body)) {
                is PostResult.Ok -> {
                    lastError = null
                    logAttempt(attempt + 1, AiLogOutcome.Ok, result.code, result.durationMs, null)
                    return result.body
                }
                is PostResult.Failed -> {
                    lastError = result.message
                    logAttempt(
                        attempt = attempt + 1,
                        outcome = if (result.code == null) AiLogOutcome.NetworkError
                        else AiLogOutcome.HttpError,
                        code = result.code,
                        durationMs = result.durationMs,
                        detail = result.detail ?: result.message,
                    )
                    if (!result.retryable || attempt >= MAX_ATTEMPTS - 1) return null
                    delay(RETRY_DELAYS_MS[attempt])
                    attempt++
                }
            }
        }
    }

    /**
     * Transient states worth retrying: a request timeout or any 5xx, which in
     * practice is a provider briefly shedding load on one model.
     *
     * 429 is deliberately NOT retryable even though it looks transient. Both
     * flavours need far longer than this backoff — a per-minute rate limit
     * needs about a minute, and a free-tier daily quota reports its reset in
     * tens of minutes — so retrying only makes the user wait for the same
     * failure.
     */
    private fun isRetryable(code: Int): Boolean =
        code == 408 || code in 500..599

    private fun postOnce(urlStr: String, headers: Map<String, String>, body: String): PostResult {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            // 60s, not 30s: the reply is a ~34-key object generated against a
            // response_schema, and generation time scales with the model.
            // gemini-flash-lite answers in about two seconds and never comes
            // near either figure, but gemini-flash exhausted all three
            // attempts at exactly 30s each — our own limit firing mid-
            // generation, not the network failing. Three strikes at ~31s also
            // meant a lookup could spend 95 seconds to arrive at nothing.
            // connectTimeout stays at 15s; reaching the server is a different
            // question from waiting for it to think.
            readTimeout = 60_000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "FoodTracker/1.0 (Android)")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        val startedAt = System.nanoTime()
        fun elapsedMs(): Long = (System.nanoTime() - startedAt) / 1_000_000
        return try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                android.util.Log.w("AiNutritionApi", "POST $urlStr -> HTTP $code: ${err?.take(800)}")
                PostResult.Failed(
                    message = describeHttp(code, err),
                    retryable = isRetryable(code),
                    code = code,
                    durationMs = elapsedMs(),
                    detail = err,
                )
            } else {
                PostResult.Ok(
                    body = conn.inputStream.bufferedReader().use { it.readText() },
                    code = code,
                    durationMs = elapsedMs(),
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("AiNutritionApi", "POST $urlStr failed: ${e.javaClass.simpleName}: ${e.message}")
            PostResult.Failed(
                message = "Network error — check your internet connection and try again.",
                retryable = true,
                // No status: the request never got one. That null is what tells
                // the log this was the network and not the provider.
                code = null,
                durationMs = elapsedMs(),
                detail = "${e.javaClass.simpleName}: ${e.message}",
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun describeHttp(code: Int, body: String?): String {
        // Billing/credit problems can arrive as 400/402/429 depending on provider
        // — detect them from the error body so the message is actionable.
        val b = body?.lowercase().orEmpty()
        // A free-tier daily cap is not a billing problem and is not fixed by
        // retrying — it is fixed by waiting, and the provider says how long.
        // Checked before the billing sniff below, whose keywords it shares.
        if (b.contains("free_tier") || b.contains("free tier")) {
            val resets = retryAfterPhrase(body)
            return "You've used up today's free-tier requests for this model." +
                (resets?.let { " Resets in about " + it + "." } ?: "") +
                " Pick a different model in Settings, or add billing to your key."
        }
        if (b.contains("credit balance") || b.contains("billing") ||
            b.contains("insufficient_quota") || b.contains("insufficient funds")
        ) {
            return "Your account is out of credit/quota. Add billing or credits for this key, then retry."
        }
        return when (code) {
            401, 403 -> "Your API key was rejected (HTTP $code). Check the key and selected provider in Settings."
            404 -> "That model isn't available to your account (HTTP 404). Pick a different model, or your plan may not include it."
            429 -> "That model is rate-limited right now (HTTP 429). Nothing is wrong with your key — pick a different model in Settings, or wait a minute and retry."
            400 -> "The provider rejected the request (HTTP 400). Try a different model."
            // 503 in particular is the provider shedding load on one model;
            // the key is fine and another model usually answers immediately,
            // so say so rather than just "try again later".
            503 -> "That model is overloaded right now (HTTP 503). This is the provider's side, not your key — pick a different model in Settings, or retry in a few minutes."
            in 500..599 -> "The AI provider had a server error (HTTP $code). Try again shortly."
            else -> "AI request failed (HTTP $code)."
        }
    }

    /**
     * Providers report when a quota clears ("Please retry in 53m50.8s"). Pull
     * that out so the message can say when it is worth trying again, rounded
     * to whole minutes because the seconds are noise at this scale.
     */
    private fun retryAfterPhrase(body: String?): String? {
        val text = body.orEmpty()
        val minutesAndSeconds = Regex("retry in (\\d+)m([\\d.]+)s", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.toDoubleOrNull()?.times(60)
        val secondsOnly = Regex("retry in ([\\d.]+)s", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        val seconds = minutesAndSeconds ?: secondsOnly ?: return null
        val minutes = (seconds / 60).roundToInt()
        return if (minutes >= 1) "$minutes min" else "under a minute"
    }

    /**
     * Pull the JSON object out of the model's reply (models sometimes wrap it in
     * ``` fences or prose) and map keys to an [AiNutrition] (serving + per-serving
     * nutrients). Returns null if the payload has no usable macros so the form
     * isn't wiped with zeros.
     */
    private fun parseResult(reply: String): AiNutrition? =
        extractObject(reply)?.let { mapNutrition(it) }

    /** Models sometimes wrap the object in ``` fences or prose — dig it out. */
    private fun extractObject(reply: String): JsonObject? {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            json.parseToJsonElement(reply.substring(start, end + 1)).jsonObject
        }.getOrNull()
    }

    private fun mapNutrition(obj: JsonObject): AiNutrition? {
        fun num(key: String): Double = obj[key]?.jsonPrimitive?.doubleOrNull ?: 0.0
        fun str(key: String): String? =
            obj[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

        val n = Nutrients(
            calories = num("calories"),
            carbs = num("carbs"),
            sugar = num("sugar"),
            fiber = num("fiber"),
            protein = num("protein"),
            fat = num("fat"),
            saturatedFat = num("saturatedFat"),
            omega3 = num("omega3"),
            cholesterol = num("cholesterol"),
            sodium = num("sodium"),
            calcium = num("calcium"),
            iron = num("iron"),
            magnesium = num("magnesium"),
            potassium = num("potassium"),
            zinc = num("zinc"),
            phosphorus = num("phosphorus"),
            selenium = num("selenium"),
            copper = num("copper"),
            manganese = num("manganese"),
            vitaminA = num("vitaminA"),
            vitaminC = num("vitaminC"),
            vitaminD = num("vitaminD"),
            vitaminE = num("vitaminE"),
            vitaminK = num("vitaminK"),
            vitaminB12 = num("vitaminB12"),
            folate = num("folate"),
            vitaminB1 = num("vitaminB1"),
            vitaminB2 = num("vitaminB2"),
            vitaminB3 = num("vitaminB3"),
            vitaminB6 = num("vitaminB6"),
        )
        if (!(n.calories > 0.0 || n.protein > 0.0 || n.carbs > 0.0 || n.fat > 0.0)) return null

        val detailed = n.withOptionalDetail(obj)

        // Fall back to a 100 g serving if the model omitted or botched the size.
        val grams = num("servingGrams").takeIf { it > 0.0 } ?: 100.0
        val label = str("servingLabel") ?: "1 serving (${grams.roundToInt()} g)"
        // fromName returns Other for anything unexpected/missing.
        val category = FoodCategory.fromName(str("category") ?: "")
        return AiNutrition(
            servingGrams = grams,
            servingLabel = label,
            category = category,
            nutrients = detailed,
            servingVariants = ServingVariant.sanitize(parseVariants(obj), grams, label),
        )
    }

    /**
     * Fold the optional fiber split and protein profile into [this], but only
     * where the reply's own arithmetic holds.
     *
     * A breakdown whose parts contradict the whole is a hallucination, not a
     * measurement, and storing it would put two numbers on screen that argue
     * with each other. Each group is taken or dropped as a unit; dropping
     * leaves the fields at 0, which the app reads as unknown and hides, so the
     * food still shows its correct total fiber and protein.
     *
     * The ranges below are not invented. Every one of the fourteen published
     * food profiles seeded in
     * [com.example.foodtracker.data.db.IndianFoodSeed] — wheat at the bottom,
     * dairy at the top — falls inside them, with the real spread being
     * 29-47% of protein as essential amino acids and 18-22% of that as
     * leucine. The bounds are widened a little past the observed spread so a
     * genuinely unusual food is not rejected, which still leaves a box narrow
     * enough that a guessed pair rarely lands in it.
     */
    private fun Nutrients.withOptionalDetail(obj: JsonObject): Nutrients {
        fun num(key: String): Double =
            (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.doubleOrNull
                ?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0

        // Complete a half-given split rather than discard it. The two
        // fractions are defined as summing to fiber, so the missing side is
        // arithmetic on a figure we already have, not another guess — and
        // half an answer is the common failure here: asked without any ratio
        // to lean on, the model returned solubleFiber 1.2 against fiber 5.1
        // for cooked peas, a plausible published value, and simply left
        // insolubleFiber out. Requiring both threw that away.
        //
        // Only from a side that is a credible share of the whole: between a
        // twentieth and nineteen twentieths. Outside that the given figure is
        // describing something other than this serving's fiber, and
        // subtracting from it would manufacture a number to match.
        val givenSoluble = num("solubleFiber")
        val givenInsoluble = num("insolubleFiber")
        fun completes(given: Double) =
            fiber > 0.0 && given in (fiber * 0.05)..(fiber * 0.95)

        val soluble = if (givenSoluble <= MIN_DETAIL_G && completes(givenInsoluble)) {
            fiber - givenInsoluble
        } else {
            givenSoluble
        }
        val insoluble = if (givenInsoluble <= MIN_DETAIL_G && completes(givenSoluble)) {
            fiber - givenSoluble
        } else {
            givenInsoluble
        }

        // Allow a 20% rounding gap against our own fiber figure; beyond that
        // the model is describing a different portion, or inventing. A
        // completed pair satisfies this by construction — the check still
        // matters for the pairs the model supplied whole.
        val fiberOk = fiber > 0.0 && (soluble > MIN_DETAIL_G || insoluble > MIN_DETAIL_G) &&
            kotlin.math.abs((soluble + insoluble) - fiber) <= fiber * 0.20

        val eaa = num("essentialAminoAcids")
        val leucine = num("leucine")
        val aminoOk = protein > 0.0 && eaa > MIN_DETAIL_G && leucine > MIN_DETAIL_G &&
            eaa / protein in 0.25..0.55 &&
            leucine / eaa in 0.14..0.25

        return copy(
            solubleFiber = if (fiberOk) soluble else 0.0,
            insolubleFiber = if (fiberOk) insoluble else 0.0,
            essentialAminoAcids = if (aminoOk) eaa else 0.0,
            leucine = if (aminoOk) leucine else 0.0,
        )
    }

    /**
     * Read the optional `servingVariants` array. Absent is the expected case,
     * and anything malformed is dropped rather than failing the whole reply —
     * a food with no named sizes is still a perfectly good result.
     */
    private fun parseVariants(obj: JsonObject): List<ServingVariant> {
        val array = runCatching { obj["servingVariants"]?.jsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            runCatching {
                val v = element.jsonObject
                val label = v["label"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
                val grams = v["grams"]?.jsonPrimitive?.doubleOrNull ?: return@runCatching null
                ServingVariant(label, grams)
            }.getOrNull()
        }
    }
}
