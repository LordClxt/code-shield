package codeshield.llm

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Vérifie le parsing des listes de modèles pour chaque format provider. */
class KtorLlmClientTest {

    private fun clientReturning(body: String, status: HttpStatusCode = HttpStatusCode.OK): KtorLlmClient {
        val engine = MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return KtorLlmClient(HttpClient(engine))
    }

    @Test
    fun openAiModelsParsesDataIds() = runTest {
        val client = clientReturning("""{"data":[{"id":"gpt-4o"},{"id":"gpt-4o-mini"}]}""")
        val res = client.listModels(LlmSettings(provider = LlmProvider.OPENAI, apiKey = "sk-x"))
        assertEquals(listOf("gpt-4o", "gpt-4o-mini"), res.getOrThrow())
    }

    @Test
    fun ollamaModelsParsesNamesWithoutKey() = runTest {
        val client = clientReturning("""{"models":[{"name":"llama3.1:latest"},{"name":"qwen2.5-coder"}]}""")
        val res = client.listModels(LlmSettings(provider = LlmProvider.OLLAMA, baseUrl = "http://localhost:11434"))
        assertEquals(listOf("llama3.1:latest", "qwen2.5-coder"), res.getOrThrow())
    }

    @Test
    fun anthropicModelsParsesDataIds() = runTest {
        val client = clientReturning("""{"data":[{"id":"claude-sonnet-4-20250514"}]}""")
        val res = client.listModels(LlmSettings(provider = LlmProvider.ANTHROPIC, apiKey = "sk-ant-x"))
        assertEquals(listOf("claude-sonnet-4-20250514"), res.getOrThrow())
    }

    @Test
    fun geminiModelsStripsPrefixAndFiltersGenerateContent() = runTest {
        val body = """{"models":[
            {"name":"models/gemini-2.0-flash","supportedGenerationMethods":["generateContent"]},
            {"name":"models/text-embedding-004","supportedGenerationMethods":["embedContent"]}
        ]}""".replace("\n", "").replace(" ", "")
        val client = clientReturning(body)
        val res = client.listModels(LlmSettings(provider = LlmProvider.GEMINI, apiKey = "k"))
        assertEquals(listOf("gemini-2.0-flash"), res.getOrThrow())
    }

    @Test
    fun httpErrorBecomesFailure() = runTest {
        val client = clientReturning("""{"error":"invalid_api_key"}""", HttpStatusCode.Unauthorized)
        val res = client.listModels(LlmSettings(provider = LlmProvider.OPENAI, apiKey = "bad"))
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull()?.message?.contains("401") == true)
    }

    @Test
    fun missingKeyFailsBeforeAnyHttpCall() = runTest {
        var called = false
        val engine = MockEngine { called = true; respond("{}", HttpStatusCode.OK) }
        val client = KtorLlmClient(HttpClient(engine))
        val res = client.listModels(LlmSettings(provider = LlmProvider.OPENAI, apiKey = ""))
        assertTrue(res.isFailure)
        assertTrue(!called, "aucun appel HTTP sans clé API")
    }

    @Test
    fun completeUsesConfiguredMaxTokens() = runTest {
        var capturedBody = ""
        val engine = MockEngine { request ->
            capturedBody = (request.body as io.ktor.http.content.TextContent).text
            respond(
                """{"choices":[{"message":{"content":"OK"},"finish_reason":"stop"}]}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = KtorLlmClient(HttpClient(engine))
        val settings = LlmSettings(
            provider = LlmProvider.OPENAI, apiKey = "sk-x",
            model = "gpt-4o-mini", enabled = true, maxTokens = 8192,
        )
        val res = client.complete("dis OK", settings).getOrThrow()
        assertTrue(capturedBody.contains("\"max_tokens\":8192"), "body=$capturedBody")
        assertEquals("OK", res.text)
        assertTrue(!res.truncated)
    }

    @Test
    fun finishReasonLengthIsFlaggedTruncated() = runTest {
        val engine = MockEngine {
            respond(
                """{"choices":[{"message":{"content":"réponse coupée au milieu d'une"},"finish_reason":"length"}]}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = KtorLlmClient(HttpClient(engine))
        val settings = LlmSettings(
            provider = LlmProvider.OPENAI, apiKey = "sk-x", model = "gpt-4o-mini", enabled = true,
        )
        val res = client.complete("explique", settings).getOrThrow()
        assertTrue(res.truncated, "finish_reason=length doit remonter truncated=true")
    }

    @Test
    fun anthropicStopReasonMaxTokensIsFlaggedTruncated() = runTest {
        val engine = MockEngine {
            respond(
                """{"content":[{"text":"début de réponse"}],"stop_reason":"max_tokens"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = KtorLlmClient(HttpClient(engine))
        val settings = LlmSettings(
            provider = LlmProvider.ANTHROPIC, apiKey = "sk-ant-x",
            model = "claude-sonnet-4-20250514", enabled = true,
        )
        val res = client.complete("explique", settings).getOrThrow()
        assertTrue(res.truncated)
        assertEquals("début de réponse", res.text)
    }
}
