package codeshield.backend

import codeshield.llm.LlmClient
import codeshield.llm.LlmProvider
import codeshield.llm.LlmResult
import codeshield.llm.LlmSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class BackendDiagnosticEngineTest {

    @Test
    fun databaseNotFoundErrorIsIdentifiedWithQuickFixes() {
        val errorLogs = """
            INFO:     Started server process [9776]
            INFO:     Waiting for application startup.
            2026-10-03 18:17:49,761 | INFO | app.main | Tentative de connexion à la base de données : host=localhost, port=5432, db=ecit_database
            asyncpg.exceptions.InvalidCatalogNameError: database "ecit_database" does not exist
            ERROR:    Application startup failed. Exiting.
        """.trimIndent()

        val diag = BackendDiagnosticEngine.diagnoseLocally(
            workDir = "/Users/apple/PycharmProjects/ecit-app-v2",
            stackLabel = "FastAPI CodeShield",
            exitCode = 3,
            logs = errorLogs,
        )

        assertEquals(FailureCategory.DATABASE_NOT_FOUND, diag.category)
        assertTrue(diag.title.contains("ecit_database"))
        assertTrue(diag.rootCause.contains("ecit_database"))
        assertEquals("ecit_database", diag.detectedServiceOrDb)
        assertTrue(diag.quickFixActions.isNotEmpty(), "Should provide quick fix actions")
        assertTrue(diag.quickFixActions.any { it.command.any { c -> c.contains("createdb") } })
        assertTrue(diag.suggestedFixes.any { it.contains("createdb") || it.contains("CREATE DATABASE") })
    }

    @Test
    fun connectionRefusedPostgresIsIdentified() {
        val errorLogs = """
            psycopg2.OperationalError: could not connect to server: Connection refused
            Is the server running on host "localhost" (127.0.0.1) and accepting
            TCP/IP connections on port 5432?
        """.trimIndent()

        val diag = BackendDiagnosticEngine.diagnoseLocally(
            workDir = "/dummy/project",
            stackLabel = "FastAPI",
            exitCode = 1,
            logs = errorLogs,
        )

        assertEquals(FailureCategory.DATABASE_UNREACHABLE, diag.category)
        assertTrue(diag.title.contains("5432"))
        assertTrue(diag.quickFixActions.any { it.command.contains("docker") || it.command.contains("brew") })
    }

    @Test
    fun portInUseIsIdentifiedWithKillAction() {
        val errorLogs = """
            ERROR: [Errno 48] error while attempting to bind on address ('127.0.0.1', 8000): address already in use
        """.trimIndent()

        val diag = BackendDiagnosticEngine.diagnoseLocally(
            workDir = "/dummy/project",
            stackLabel = "FastAPI",
            exitCode = 1,
            logs = errorLogs,
        )

        assertEquals(FailureCategory.PORT_IN_USE, diag.category)
        assertTrue(diag.title.contains("8000"))
        assertTrue(diag.quickFixActions.any { it.command.any { c -> c.contains("lsof") } })
    }

    @Test
    fun missingModuleIsIdentifiedWithInstallAction() {
        val errorLogs = """
            Traceback (most recent call last):
              File "main.py", line 2, in <module>
                import asyncpg
            ModuleNotFoundError: No module named 'asyncpg'
        """.trimIndent()

        val diag = BackendDiagnosticEngine.diagnoseLocally(
            workDir = "/dummy/project",
            stackLabel = "Python",
            exitCode = 1,
            logs = errorLogs,
        )

        assertEquals(FailureCategory.MISSING_DEPENDENCY, diag.category)
        assertTrue(diag.title.contains("asyncpg"))
        assertTrue(diag.quickFixActions.any { it.command.contains("asyncpg") })
    }

    @Test
    fun runtimeExceptionIsIdentifiedWithFileAndLine() {
        val errorLogs = """
            Traceback (most recent call last):
              File "/app/seed.py", line 149, in seed_if_empty
                Group.type_group_id == existing_type_group.id
            AttributeError: 'NoneType' object has no attribute 'id'
            ERROR: Application startup failed. Exiting.
        """.trimIndent()

        val diag = BackendDiagnosticEngine.diagnoseLocally(
            workDir = "/dummy/project",
            stackLabel = "FastAPI",
            exitCode = 3,
            logs = errorLogs,
        )

        assertEquals(FailureCategory.RUNTIME_EXCEPTION, diag.category)
        assertTrue(diag.title.contains("AttributeError"))
        assertTrue(diag.rootCause.contains("seed.py:L149"))
    }

    @Test
    fun llmDiagnosticEnrichesResultWhenAvailable() = runTest {
        val fakeLlm = object : LlmClient {
            override suspend fun complete(prompt: String, settings: LlmSettings): Result<LlmResult> {
                val jsonResponse = """
                    {
                      "category": "DATABASE_NOT_FOUND",
                      "title": "Base ecit_database introuvable (analyse IA)",
                      "rootCause": "La base n'existe pas dans le PostgreSQL actif.",
                      "explanation": "L'application FastAPI utilise SQLAlchemy et a besoin que la base existe.",
                      "suggestedFixes": ["docker exec careercommit-db createdb -U postgres ecit_database"]
                    }
                """.trimIndent()
                return Result.success(LlmResult(jsonResponse, "test-model", LlmProvider.OPENAI))
            }
            override suspend fun testConnection(settings: LlmSettings): Result<String> = Result.success("OK")
            override suspend fun listModels(settings: LlmSettings): Result<List<String>> = Result.success(emptyList())
        }

        val settings = LlmSettings(enabled = true, apiKey = "test-key", provider = LlmProvider.OPENAI)
        val res = BackendDiagnosticEngine.diagnoseWithLlm(
            llm = fakeLlm,
            settings = settings,
            workDir = "/dummy",
            stackLabel = "FastAPI",
            exitCode = 3,
            logs = "database \"ecit_database\" does not exist",
        )

        assertTrue(res.isSuccess)
        val diag = res.getOrThrow()
        assertEquals(DiagnosticSource.LLM, diag.source)
        assertEquals("Base ecit_database introuvable (analyse IA)", diag.title)
        assertEquals("La base n'existe pas dans le PostgreSQL actif.", diag.rootCause)
    }

    @Test
    fun missingBinaryFromPathIsIdentifiedWithBrewAction() {
        val errorLogs = """
            java.io.IOException: Cannot run program "php": error=2, No such file or directory
                at java.lang.ProcessBuilder.start(ProcessBuilder.java:1143)
        """.trimIndent()

        val diag = BackendDiagnosticEngine.diagnoseLocally(
            workDir = "/dummy/project",
            stackLabel = "PHP/Laravel",
            exitCode = null,
            logs = errorLogs,
        )

        assertEquals(FailureCategory.MISSING_DEPENDENCY, diag.category)
        assertTrue(diag.title.contains("php"))
        assertTrue(diag.quickFixActions.any { it.command.contains("php") })
    }

    @Test
    fun missingNodeModulesIsIdentifiedWithNpmInstallAction() {
        val errorLogs = """
            sh: vite: not found
            npm ERR! Lifecycle script `dev` failed with error: 127
        """.trimIndent()

        val diag = BackendDiagnosticEngine.diagnoseLocally(
            workDir = "/dummy/project",
            stackLabel = "Node/Vite",
            exitCode = 127,
            logs = errorLogs,
        )

        assertEquals(FailureCategory.MISSING_DEPENDENCY, diag.category)
        assertTrue(diag.title.contains("Node.js"))
        assertTrue(diag.quickFixActions.any { it.command == listOf("npm", "install") })
    }

    @Test
    fun missingEnvFileIsIdentified() {
        val errorLogs = """
            FileNotFoundError: [Errno 2] No such file or directory: '.env'
            pydantic_core._pydantic_core.ValidationError: 1 validation error for Settings
        """.trimIndent()

        val diag = BackendDiagnosticEngine.diagnoseLocally(
            workDir = "/dummy/project",
            stackLabel = "FastAPI",
            exitCode = 1,
            logs = errorLogs,
        )

        assertEquals(FailureCategory.ENVIRONMENT_ERROR, diag.category)
        assertTrue(diag.title.contains(".env"))
        assertTrue(diag.quickFixActions.any { it.id == "create_env_from_example" })
    }

    @Test
    fun databaseAuthFailedIsIdentified() {
        val errorLogs = """
            asyncpg.exceptions.InvalidPasswordError: password authentication failed for user "postgres"
            ERROR:    Application startup failed. Exiting.
        """.trimIndent()

        val diag = BackendDiagnosticEngine.diagnoseLocally(
            workDir = "/dummy/project",
            stackLabel = "FastAPI",
            exitCode = 3,
            logs = errorLogs,
        )

        assertEquals(FailureCategory.ENVIRONMENT_ERROR, diag.category)
        assertTrue(diag.title.contains("postgres"))
        assertTrue(diag.title.contains("authentification"))
    }

    @Test
    fun dockerDaemonDownIsIdentified() {
        val errorLogs = """
            Cannot connect to the Docker daemon at unix:///var/run/docker.sock. Is the docker daemon running?
        """.trimIndent()

        val diag = BackendDiagnosticEngine.diagnoseLocally(
            workDir = "/dummy/project",
            stackLabel = "Docker Compose",
            exitCode = 1,
            logs = errorLogs,
        )

        assertEquals(FailureCategory.DATABASE_UNREACHABLE, diag.category)
        assertTrue(diag.title.contains("Docker"))
        assertTrue(diag.quickFixActions.any { it.id == "start_docker_app" })
    }

    @Test
    fun unappliedMigrationsAreIdentified() {
        val errorLogs = """
            alembic.util.exc.CommandError: Target database is not up to date.
            sqlalchemy.exc.ProgrammingError: (psycopg2.errors.UndefinedTable) relation "users" does not exist
        """.trimIndent()

        val diag = BackendDiagnosticEngine.diagnoseLocally(
            workDir = "/dummy/project",
            stackLabel = "FastAPI",
            exitCode = 1,
            logs = errorLogs,
        )

        assertEquals(FailureCategory.MIGRATION_PENDING, diag.category)
        assertTrue(diag.title.contains("Migrations"))
        assertTrue(diag.quickFixActions.any { it.id == "run_alembic_migrate" })
    }

    @Test
    fun startupTimeoutIsIdentified() {
        val errorLogs = "[Python/FastAPI] lancé mais ne répond pas sur http://localhost:8000 après 15s."

        val diag = BackendDiagnosticEngine.diagnoseLocally(
            workDir = "/dummy/project",
            stackLabel = "Python/FastAPI",
            exitCode = null,
            logs = errorLogs,
        )

        assertEquals(FailureCategory.GENERIC_CRASH, diag.category)
        assertTrue(diag.title.contains("Délai de démarrage dépassé"))
    }

    @Test
    fun permissionDeniedIsIdentified() {
        val errorLogs = "sh: ./gradlew: Permission denied (error=13)"

        val diag = BackendDiagnosticEngine.diagnoseLocally(
            workDir = "/dummy/project",
            stackLabel = "Java/Gradle",
            exitCode = 126,
            logs = errorLogs,
        )

        assertEquals(FailureCategory.PERMISSION_DENIED, diag.category)
        assertTrue(diag.title.contains("Permission"))
        assertTrue(diag.quickFixActions.any { it.id == "chmod_all_scripts" })
    }
}
