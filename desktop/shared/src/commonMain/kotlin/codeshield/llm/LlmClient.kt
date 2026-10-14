package codeshield.llm

/** Résultat LLM (texte déjà nettoyé, prêt à afficher). */
data class LlmResult(
    val text: String,
    val model: String,
    val provider: LlmProvider,
    /** true si l'API a coupé la réponse par limite de tokens (finish_reason=length/max_tokens). */
    val truncated: Boolean = false,
)

/** Client LLM injecté (Ktor sur JVM, fake en tests). */
interface LlmClient {
    suspend fun complete(prompt: String, settings: LlmSettings): Result<LlmResult>
    suspend fun testConnection(settings: LlmSettings): Result<String>

    /**
     * Récupère la liste des modèles réellement disponibles chez le provider
     * (GET /models OpenAI-compatible, /api/tags Ollama, /v1/models Anthropic,
     * /v1beta/models Gemini). La clé API sert uniquement d'authentification.
     */
    suspend fun listModels(settings: LlmSettings): Result<List<String>>
}

/** Fake offline pour tests / provider NONE. */
object NoopLlmClient : LlmClient {
    override suspend fun complete(prompt: String, settings: LlmSettings): Result<LlmResult> =
        Result.failure(IllegalStateException("LLM désactivé — activez un provider dans Settings"))
    override suspend fun testConnection(settings: LlmSettings): Result<String> =
        Result.failure(IllegalStateException("LLM désactivé"))
    override suspend fun listModels(settings: LlmSettings): Result<List<String>> =
        Result.failure(IllegalStateException("LLM désactivé"))
}
