package codeshield.llm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

import io.ktor.client.plugins.HttpTimeout

/**
 * Client LLM via Ktor (JVM). Metadata-only : l'appelant ne doit JAMAIS
 * inclure de code source dans le prompt (cf. buildTriagePrompt/buildExplainPrompt).
 */
class KtorLlmClient(
    private val http: HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
        }
    }
) : LlmClient {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun complete(prompt: String, settings: LlmSettings): Result<LlmResult> =
        runCatching {
            if (!settings.enabled || settings.provider == LlmProvider.NONE) {
                throw IllegalStateException("LLM désactivé — choisissez un provider dans Settings")
            }
            val model = settings.model.ifBlank { LlmModels.defaultModel(settings.provider) }
            if (model.isBlank()) throw IllegalStateException("Modèle vide — choisissez un modèle")
            if (LlmModels.needsKey(settings.provider) && settings.apiKey.isBlank()) {
                throw IllegalStateException("Clé API manquante pour ${settings.provider.label}")
            }
            val completion = when (settings.provider) {
                LlmProvider.OPENAI, LlmProvider.MISTRAL, LlmProvider.OPENROUTER,
                LlmProvider.OLLAMA, LlmProvider.CUSTOM -> openAiCompatible(prompt, settings, model)
                LlmProvider.ANTHROPIC -> anthropic(prompt, settings, model)
                LlmProvider.GEMINI -> gemini(prompt, settings, model)
                LlmProvider.NONE -> throw IllegalStateException("LLM désactivé")
            }
            // Pas de take() arbitraire : la longueur est pilotée par maxTokens,
            // et une coupure éventuelle est signalée via `truncated`.
            LlmResult(completion.text.trim(), model, settings.provider, completion.truncated)
        }

    override suspend fun testConnection(settings: LlmSettings): Result<String> =
        runCatching {
            val r = complete("Réponds uniquement: OK", settings).getOrThrow()
            "OK (${r.provider.label} / ${r.model}) — réponse: ${r.text.take(80)}"
        }

    override suspend fun listModels(settings: LlmSettings): Result<List<String>> =
        runCatching {
            if (settings.provider == LlmProvider.NONE) {
                throw IllegalStateException("Choisissez un provider d'abord")
            }
            if (LlmModels.needsKey(settings.provider) && settings.apiKey.isBlank()) {
                throw IllegalStateException("Clé API manquante pour ${settings.provider.label}")
            }
            val models = when (settings.provider) {
                LlmProvider.OLLAMA -> ollamaModels(settings)
                LlmProvider.ANTHROPIC -> anthropicModels(settings)
                LlmProvider.GEMINI -> geminiModels(settings)
                LlmProvider.OPENAI, LlmProvider.MISTRAL, LlmProvider.OPENROUTER,
                LlmProvider.CUSTOM -> openAiModels(settings)
                LlmProvider.NONE -> throw IllegalStateException("LLM désactivé")
            }
            models.distinct().sorted().ifEmpty {
                throw IllegalStateException("Aucun modèle retourné par ${settings.provider.label}")
            }
        }

    /** GET {base}/models — format OpenAI-compatible: {"data":[{"id":…}]}. */
    private suspend fun openAiModels(s: LlmSettings): List<String> {
        val res = http.get("${baseOf(s)}/models") {
            if (s.apiKey.isNotBlank()) header("Authorization", "Bearer ${s.apiKey}")
            if (s.provider == LlmProvider.OPENROUTER) {
                header("HTTP-Referer", "https://codeshield.local")
                header("X-Title", "CodeShield Desktop")
            }
        }
        val raw = res.body<String>()
        if (!res.status.isSuccess()) throw IllegalStateException("HTTP ${res.status.value}: ${raw.take(300)}")
        val data = json.parseToJsonElement(raw).jsonObject["data"]?.jsonArray
            ?: throw IllegalStateException("Réponse /models illisible: ${raw.take(200)}")
        return data.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
    }

    /** GET {base}/api/tags — format Ollama: {"models":[{"name":…}]}. */
    private suspend fun ollamaModels(s: LlmSettings): List<String> {
        val res = http.get("${baseOf(s)}/api/tags")
        val raw = res.body<String>()
        if (!res.status.isSuccess()) throw IllegalStateException("HTTP ${res.status.value}: ${raw.take(300)}")
        val arr = json.parseToJsonElement(raw).jsonObject["models"]?.jsonArray
            ?: throw IllegalStateException("Réponse Ollama illisible: ${raw.take(200)}")
        return arr.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
    }

    /** GET {base}/v1/models — format Anthropic: {"data":[{"id":…}]}. */
    private suspend fun anthropicModels(s: LlmSettings): List<String> {
        val res = http.get("${baseOf(s)}/v1/models") {
            header("x-api-key", s.apiKey)
            header("anthropic-version", "2023-06-01")
        }
        val raw = res.body<String>()
        if (!res.status.isSuccess()) throw IllegalStateException("HTTP ${res.status.value}: ${raw.take(300)}")
        val data = json.parseToJsonElement(raw).jsonObject["data"]?.jsonArray
            ?: throw IllegalStateException("Réponse Anthropic illisible: ${raw.take(200)}")
        return data.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
    }

    /** GET {base}/v1beta/models?key=… — format Gemini: {"models":[{"name":"models/x"}]}. */
    private suspend fun geminiModels(s: LlmSettings): List<String> {
        val base = if (s.baseUrl.isNotBlank()) s.baseUrl.trimEnd('/') else "https://generativelanguage.googleapis.com"
        val res = http.get("$base/v1beta/models?key=${s.apiKey}")
        val raw = res.body<String>()
        if (!res.status.isSuccess()) throw IllegalStateException("HTTP ${res.status.value}: ${raw.take(300)}")
        val arr = json.parseToJsonElement(raw).jsonObject["models"]?.jsonArray
            ?: throw IllegalStateException("Réponse Gemini illisible: ${raw.take(200)}")
        // Seuls les modèles capables de generateContent sont utiles ici.
        return arr.mapNotNull { m ->
            val methods = m.jsonObject["supportedGenerationMethods"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            val name = m.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/")
            if (name != null && ("generateContent" in methods || methods.isEmpty())) name else null
        }
    }

    private fun baseOf(settings: LlmSettings): String {
        if (settings.baseUrl.isNotBlank()) return settings.baseUrl.trimEnd('/')
        val d = LlmModels.defaultBaseUrl(settings.provider)
        return if (d.isNotBlank()) d else throw IllegalStateException("Base URL manquante (provider CUSTOM)")
    }

    /** Texte + flag de coupure par limite de tokens (remonté à l'UI). */
    private data class RawCompletion(val text: String, val truncated: Boolean)

    private suspend fun openAiCompatible(prompt: String, s: LlmSettings, model: String): RawCompletion {
        val url = "${baseOf(s)}/chat/completions"
        val body = buildJsonObject {
            put("model", model)
            put("messages", JsonArray(listOf(buildJsonObject {
                put("role", "user")
                put("content", prompt)
            })))
            put("temperature", 0.2)
            put("max_tokens", s.coercedMaxTokens())
        }
        val res = http.post(url) {
            contentType(ContentType.Application.Json)
            if (s.apiKey.isNotBlank()) header("Authorization", "Bearer ${s.apiKey}")
            if (s.provider == LlmProvider.OPENROUTER) {
                header("HTTP-Referer", "https://codeshield.local")
                header("X-Title", "CodeShield Desktop")
            }
            setBody(body.toString())
        }
        val raw = res.body<String>()
        if (!res.status.isSuccess()) throw IllegalStateException("LLM HTTP ${res.status.value}: ${raw.take(300)}")
        val root = json.parseToJsonElement(raw).jsonObject
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw IllegalStateException("Réponse OpenAI-compatible illisible: ${raw.take(200)}")
        val text = choice["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("Réponse OpenAI-compatible illisible: ${raw.take(200)}")
        // finish_reason == "length" : la réponse a été coupée par max_tokens.
        val truncated = choice["finish_reason"]?.jsonPrimitive?.contentOrNull == "length"
        return RawCompletion(text, truncated)
    }

    private suspend fun anthropic(prompt: String, s: LlmSettings, model: String): RawCompletion {
        val url = "${baseOf(s)}/v1/messages"
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", s.coercedMaxTokens())
            put("messages", JsonArray(listOf(buildJsonObject {
                put("role", "user")
                put("content", prompt)
            })))
        }
        val res = http.post(url) {
            contentType(ContentType.Application.Json)
            header("x-api-key", s.apiKey)
            header("anthropic-version", "2023-06-01")
            setBody(body.toString())
        }
        val raw = res.body<String>()
        if (!res.status.isSuccess()) throw IllegalStateException("Anthropic HTTP ${res.status.value}: ${raw.take(300)}")
        val root = json.parseToJsonElement(raw).jsonObject
        val content = root["content"]?.jsonArray ?: throw IllegalStateException("Réponse Anthropic illisible")
        val text = content.mapNotNull {
            runCatching { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }.getOrNull()
        }.firstOrNull { !it.isNullOrBlank() }
            ?: throw IllegalStateException("Réponse Anthropic vide: ${raw.take(200)}")
        // stop_reason == "max_tokens" : réponse coupée par la limite.
        val truncated = root["stop_reason"]?.jsonPrimitive?.contentOrNull == "max_tokens"
        return RawCompletion(text, truncated)
    }

    private suspend fun gemini(prompt: String, s: LlmSettings, model: String): RawCompletion {
        val base = if (s.baseUrl.isNotBlank()) s.baseUrl.trimEnd('/') else "https://generativelanguage.googleapis.com"
        val url = "$base/v1beta/models/$model:generateContent?key=${s.apiKey}"
        val body = buildJsonObject {
            put("contents", JsonArray(listOf(buildJsonObject {
                put("parts", JsonArray(listOf(buildJsonObject { put("text", prompt) })))
            })))
            put("generationConfig", buildJsonObject {
                put("temperature", 0.2)
                put("maxOutputTokens", s.coercedMaxTokens())
            })
        }
        val res = http.post(url) {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val raw = res.body<String>()
        if (!res.status.isSuccess()) throw IllegalStateException("Gemini HTTP ${res.status.value}: ${raw.take(300)}")
        val root = json.parseToJsonElement(raw).jsonObject
        val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
        val parts = candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray
        val text = parts?.mapNotNull { part ->
            part.jsonObject["text"]?.jsonPrimitive?.contentOrNull
        }?.filter { it.isNotBlank() }?.joinToString("\n")
            ?: throw IllegalStateException("Réponse Gemini illisible: ${raw.take(200)}")
        // finishReason == "MAX_TOKENS" : réponse coupée par la limite.
        val truncated = candidate.get("finishReason")?.jsonPrimitive?.contentOrNull == "MAX_TOKENS"
        return RawCompletion(text, truncated)
    }
}
