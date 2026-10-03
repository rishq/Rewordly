package com.rewordly.app.domain.model

/**
 * How a provider wants a chat request built: its own path, its own way of carrying the key, its own answer shape.
 * Keeping the differences here means the rest of the app only ever sees [AiProvider].
 */
enum class AiApiStyle {
    /**
     * OpenAI. Tries `POST {base}responses` first and falls back to `POST {base}chat/completions` when the newer
     * endpoint is not available on the account. Authenticates with `Authorization: Bearer <key>`.
     */
    OPENAI,

    /** Anthropic. `POST {base}messages` with `x-api-key` and the dated `anthropic-version` header. */
    ANTHROPIC,

    /** Google. `POST {base}models/{model}:generateContent` with the key in `x-goog-api-key`. */
    GEMINI,
}

/**
 * An AI provider the user can bring their own key for.
 *
 * The model names below are only *defaults and suggestions*: every one of these vendors retires models on a
 * schedule, so the model field in settings stays editable and nothing here is treated as authoritative.
 *
 * Each default is deliberately the provider's **cheap tier**, not its flagship. Generating a vocabulary entry
 * is a small, tightly-specified structured task — the prompt pins the schema and every answer is validated
 * item by item downstream — so a flagship model buys accuracy the app cannot use while costing far more per
 * call. The stronger tiers stay in [suggestedModels] for anyone who wants them.
 */
enum class AiProvider(
    val apiStyle: AiApiStyle,
    /** Brand name, spelled the same way in every language, so it is deliberately not a translated resource. */
    val displayName: String,
    /** Base URL ending in a slash; the style-specific path is appended to it. */
    val baseUrl: String,
    val defaultModel: String,
    /** Offered as one-tap choices, cheapest tier first. The field stays free text, because model names change. */
    val suggestedModels: List<String>,
    /** Where the user issues a key. Provider metadata like [baseUrl], not a translated resource. */
    val keyConsoleUrl: String,
) {
    OPENAI(
        apiStyle = AiApiStyle.OPENAI,
        displayName = "OpenAI",
        baseUrl = "https://api.openai.com/v1/",
        defaultModel = "gpt-6-luna",
        suggestedModels = listOf("gpt-6-luna", "gpt-6-astra", "gpt-6.1-sol"),
        keyConsoleUrl = "https://platform.openai.com/api-keys",
    ),
    ANTHROPIC(
        apiStyle = AiApiStyle.ANTHROPIC,
        displayName = "Anthropic Claude",
        baseUrl = "https://api.anthropic.com/v1/",
        defaultModel = "claude-haiku-4-5-20251001",
        suggestedModels = listOf("claude-haiku-4-5-20251001", "claude-sonnet-5-5", "claude-opus-5-5"),
        keyConsoleUrl = "https://console.anthropic.com/settings/keys",
    ),
    GOOGLE(
        apiStyle = AiApiStyle.GEMINI,
        displayName = "Google Gemini",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta/",
        defaultModel = "gemini-3.5-flash-lite",
        suggestedModels = listOf("gemini-3.5-flash-lite", "gemini-3.8-flash", "gemini-3.1-pro-preview"),
        keyConsoleUrl = "https://aistudio.google.com/app/apikey",
    ),
    ;

    companion object {
        /** Offered first in the picker: the most widely used of the three. */
        val DEFAULT = OPENAI

        /** Unknown names are dropped instead of failing the read, so removing a provider degrades gracefully. */
        fun fromName(name: String?): AiProvider? = entries.firstOrNull { it.name == name }
    }
}

/**
 * The user's own AI provider.
 *
 * A blank [apiKey] means "not configured", and generation falls back to the project's backend (which is itself
 * unconfigured in a stock build). Nothing is sent to a provider until the user supplies a key.
 */
data class AiSettings(
    val provider: AiProvider? = null,
    /** Blank means "use [AiProvider.defaultModel]", so switching providers never carries a stale model name over. */
    val model: String = "",
    val apiKey: String = "",
) {
    val isReady: Boolean get() = provider != null && apiKey.isNotBlank()

    /** The model that would be sent: what the user typed, or the provider's default. Empty with no provider. */
    val effectiveModel: String
        get() = provider?.let { chosen -> model.trim().ifBlank { chosen.defaultModel } }.orEmpty()

    companion object {
        /** Keys are long but not unbounded; anything larger is a paste accident. */
        const val MAX_API_KEY_LENGTH = 512

        /** Model identifiers are short; the cap only stops a pasted document from being stored. */
        const val MAX_MODEL_LENGTH = 80
    }
}
