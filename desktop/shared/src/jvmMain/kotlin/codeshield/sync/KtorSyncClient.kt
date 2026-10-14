package codeshield.sync

import codeshield.model.ScanPayload
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json

/** Implémentation Ktor du Cloud Sync Trigger (auth Bearer JWT ou clé API csk_*). */
class KtorSyncClient(
    private val baseUrl: String,
    private val tokenProvider: () -> String?,
    private val http: HttpClient = defaultClient(),
) : SyncClient {
    override suspend fun submit(payload: ScanPayload): SyncResponse {
        val res = http.post("${baseUrl.trimEnd('/')}/api/v1/sync/submit") {
            tokenProvider()?.let { bearerAuth(it) }
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!res.status.isSuccess()) {
            // Message lisible dans l'UI (ex. "HTTP 403 : API key lacks scope sync:write").
            val detail = runCatching { res.bodyAsText() }.getOrDefault("").take(300)
            throw IllegalStateException("HTTP ${res.status.value} : $detail")
        }
        return res.body()
    }

    companion object {
        fun defaultClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                // explicitNulls=false : les champs optionnels absents ne sont pas envoyés.
                json(ScanPayload.json.let { base -> kotlinx.serialization.json.Json(base) { ignoreUnknownKeys = true } })
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 120_000
            }
            // Réessaie sur erreur réseau ou 5xx (le diff backend est idempotent par scan).
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 3)
                retryOnException(maxRetries = 3, retryOnTimeout = true)
                exponentialDelay()
            }
        }
    }
}
