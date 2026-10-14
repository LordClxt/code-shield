package codeshield.llm

/** Persistance des réglages (fichier 0600 sur JVM, mémoire en tests). */
interface SettingsStore {
    suspend fun load(): LlmSettings
    suspend fun save(settings: LlmSettings)
}

class InMemorySettingsStore(initial: LlmSettings = LlmSettings()) : SettingsStore {
    private var current = initial
    override suspend fun load(): LlmSettings = current
    override suspend fun save(settings: LlmSettings) { current = settings }
}
