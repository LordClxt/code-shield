package codeshield.ui

import codeshield.llm.LlmClient
import codeshield.llm.LlmProvider
import codeshield.llm.LlmResult
import codeshield.llm.LlmSettings
import codeshield.model.ToolName
import codeshield.tools.ToolInstaller
import codeshield.tools.ToolState
import codeshield.tools.ToolStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fake LLM pilotable (pas de réseau en tests). */
private class FakeLlm(
    var testResult: Result<String> = Result.success("OK (fake)"),
    var modelsResult: Result<List<String>> = Result.success(listOf("model-a", "model-b")),
    var completeResult: Result<LlmResult> = Result.success(
        LlmResult("1. Priorités…", "model-a", LlmProvider.OPENAI),
    ),
) : LlmClient {
    override suspend fun complete(prompt: String, settings: LlmSettings): Result<LlmResult> = completeResult
    override suspend fun testConnection(settings: LlmSettings): Result<String> = testResult
    override suspend fun listModels(settings: LlmSettings): Result<List<String>> = modelsResult
}

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelLlmTest {

    private fun TestScope.vmWith(llm: FakeLlm) = ScannerViewModel(
        scope = this,
        runner = { _, _ -> flow {} },
        fileReader = { emptyList() },
        syncFactory = { _, _ -> error("no network") },
        locator = { null },
        dirChecker = { true },
        installer = object : ToolInstaller {
            override fun installedPath(tool: ToolName): String? = null
            override suspend fun status(tool: ToolName) = ToolStatus(tool, ToolState.MISSING)
            override suspend fun install(tool: ToolName, onProgress: (Float) -> Unit): Result<String> =
                Result.failure(UnsupportedOperationException())
        },
        ioDispatcher = StandardTestDispatcher(testScheduler),
        llm = llm,
    )

    @Test
    fun testLlmSuccessEndsWithOkMessageAndNoLoading() = runTest {
        val vm = vmWith(FakeLlm())
        vm.testLlm()
        advanceUntilIdle()
        val s = vm.state.value
        assertFalse(s.llmTesting, "le spinner doit être retombé")
        assertEquals(true, s.llmTestOk)
        assertTrue(s.llmTestMessage?.contains("LLM OK") == true, s.llmTestMessage.toString())
    }

    @Test
    fun testLlmFailureEndsWithKoMessageAndNoLoading() = runTest {
        val vm = vmWith(FakeLlm(testResult = Result.failure(IllegalStateException("HTTP 401"))))
        vm.testLlm()
        advanceUntilIdle()
        val s = vm.state.value
        assertFalse(s.llmTesting)
        assertEquals(false, s.llmTestOk)
        assertTrue(s.llmTestMessage?.contains("HTTP 401") == true, s.llmTestMessage.toString())
    }

    @Test
    fun fetchModelsPopulatesListAndSelectsFirstWhenBlank() = runTest {
        val vm = vmWith(FakeLlm())
        vm.updateSettings { it.copy(provider = LlmProvider.OPENAI, apiKey = "sk-x", model = "") }
        vm.fetchModels()
        advanceUntilIdle()
        val s = vm.state.value
        assertFalse(s.modelsLoading)
        assertEquals(listOf("model-a", "model-b"), s.fetchedModels)
        assertEquals("model-a", s.llmSettings.model, "le 1er modèle fetché est auto-sélectionné")
        assertNull(s.modelsMessage)
    }

    @Test
    fun fetchModelsFailureSurfacesInlineError() = runTest {
        val vm = vmWith(FakeLlm(modelsResult = Result.failure(IllegalStateException("timeout"))))
        vm.updateSettings { it.copy(provider = LlmProvider.OPENAI, apiKey = "sk-x") }
        vm.fetchModels()
        advanceUntilIdle()
        val s = vm.state.value
        assertFalse(s.modelsLoading)
        assertTrue(s.fetchedModels.isEmpty())
        assertEquals("timeout", s.modelsMessage)
    }

    @Test
    fun changingProviderInvalidatesFetchedModelsAndTestVerdict() = runTest {
        val vm = vmWith(FakeLlm())
        vm.updateSettings { it.copy(provider = LlmProvider.OPENAI, apiKey = "sk-x") }
        vm.fetchModels()
        vm.testLlm()
        advanceUntilIdle()
        assertTrue(vm.state.value.fetchedModels.isNotEmpty())
        // Changement de provider -> états périmés remis à zéro.
        vm.updateSettings { it.copy(provider = LlmProvider.OLLAMA) }
        val s = vm.state.value
        assertTrue(s.fetchedModels.isEmpty())
        assertNull(s.llmTestMessage)
        assertNull(s.llmTestOk)
    }

    private fun ScannerViewModel.withOneFinding() {
        update {
            it.copy(
                findings = listOf(
                    codeshield.model.Finding(
                        codeshield.model.ToolName.SEMGREP, "rule", "a.py", 1,
                        codeshield.model.Severity.HIGH, "titre", "msg",
                    ),
                ),
            )
        }
    }

    @Test
    fun triageSuccessPublishesVisibleComment() = runTest {
        val vm = vmWith(FakeLlm())
        vm.withOneFinding()
        vm.triageWithAi()
        advanceUntilIdle()
        val s = vm.state.value
        assertFalse(s.aiTriageLoading)
        assertEquals("1. Priorités…", s.aiTriage, "le commentaire IA doit être publié dans l'état")
        assertNull(s.aiTriageError)
        // Dismiss : le panneau se ferme.
        vm.dismissTriage()
        assertNull(vm.state.value.aiTriage)
    }

    @Test
    fun triageFailureSurfacesInlineErrorInsteadOfSilence() = runTest {
        val vm = vmWith(FakeLlm(completeResult = Result.failure(IllegalStateException("HTTP 429"))))
        vm.withOneFinding()
        vm.triageWithAi()
        advanceUntilIdle()
        val s = vm.state.value
        assertFalse(s.aiTriageLoading)
        assertNull(s.aiTriage)
        assertEquals("HTTP 429", s.aiTriageError, "l'erreur doit être visible inline, pas seulement au terminal")
        // Un retry efface l'erreur précédente au démarrage.
        vm.dismissTriage()
        assertNull(vm.state.value.aiTriageError)
    }

    @Test
    fun triageTruncatedResponseShowsExplicitHint() = runTest {
        val vm = vmWith(
            FakeLlm(
                completeResult = Result.success(
                    LlmResult("phrase coupée au milieu", "m", LlmProvider.OPENAI, truncated = true),
                ),
            ),
        )
        vm.withOneFinding()
        vm.triageWithAi()
        advanceUntilIdle()
        val s = vm.state.value
        assertTrue(
            s.aiTriage?.contains("Réponse tronquée") == true,
            "une coupure doit être signalée dans le texte affiché : ${s.aiTriage}",
        )
    }
}
