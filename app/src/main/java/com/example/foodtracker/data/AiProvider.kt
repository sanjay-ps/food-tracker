package com.example.foodtracker.data

/**
 * A selectable model for a provider. [id] is the value sent to the API.
 * [premium] marks the pricier/stronger models — for those the two-pass autofill
 * skips its second "verify" call (they're accurate enough on the first pass),
 * saving a request.
 */
data class AiModel(val id: String, val label: String, val premium: Boolean = false)

/**
 * AI providers the optional nutrition-autofill can call. The user supplies
 * their own API key (there is no bundled token) and picks one provider — see
 * [AiNutritionApi] for the per-provider request wiring.
 *
 * Each provider offers a short [models] list ordered cheapest-first; the first
 * entry ([defaultModel]) is the cheapest model that's still accurate enough for
 * nutrition lookups and is what's selected by default.
 *
 * Gemini uses Google's `-latest` aliases rather than pinned version ids, so the
 * list never goes stale between releases. The cost is that Google can move the
 * model underneath a release: if autofill quality or JSON adherence ever drifts
 * with no change on our side, suspect that first. Claude and OpenAI stay pinned
 * and do need bumping as new versions ship.
 */
enum class AiProvider(
    val displayName: String,
    /** Short label for where the user creates a key (shown as the link text). */
    val keyHint: String,
    /** Full URL to the provider's API-key page — opened when the hint is tapped. */
    val keyUrl: String,
    val models: List<AiModel>,
) {
    Gemini(
        "Gemini",
        "aistudio.google.com/apikey",
        "https://aistudio.google.com/apikey",
        listOf(
            AiModel("gemini-flash-lite-latest", "Flash Lite"),
            AiModel("gemini-flash-latest", "Flash"),
            AiModel("gemini-pro-latest", "Pro", premium = true),
        ),
    ),
    Claude(
        "Claude",
        "console.anthropic.com",
        "https://console.anthropic.com/settings/keys",
        listOf(
            AiModel("claude-haiku-4-5-20251001", "Haiku 4.5"),
            AiModel("claude-sonnet-5", "Sonnet 5", premium = true),
        ),
    ),
    OpenAI(
        "ChatGPT",
        "platform.openai.com/api-keys",
        "https://platform.openai.com/api-keys",
        listOf(
            AiModel("gpt-5.4-mini", "5.4 mini"),
            AiModel("gpt-5.5", "5.5", premium = true),
        ),
    );

    /**
     * Whether the request can pin the reply's JSON shape server-side, so the
     * prompt does not have to spell out the key list and category values.
     * Only Gemini carries a `response_schema` today; the others are told the
     * contract in words instead (see [AiNutritionApi]).
     */
    val enforcesSchema: Boolean get() = this == Gemini

    /** Cheapest capable model — the default selection. */
    val defaultModel: AiModel get() = models.first()

    /**
     * Resolve a stored id to one of this provider's models, else the default.
     *
     * This is also the upgrade path: a user whose preference still names a
     * retired id (`gemini-3.1-flash-lite`, say) lands on the current default
     * on next read, with no migration code.
     */
    fun modelFromId(id: String?): AiModel = models.firstOrNull { it.id == id } ?: defaultModel

    companion object {
        fun fromName(name: String?): AiProvider =
            entries.firstOrNull { it.name == name } ?: Gemini
    }
}
