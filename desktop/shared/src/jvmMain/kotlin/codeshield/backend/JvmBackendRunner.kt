package codeshield.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

class JvmBackendRunner(
    private val defaultBackendDir: String? = null,
) : BackendRunner {

    private val _state = MutableStateFlow(BackendState())
    override val state: StateFlow<BackendState> = _state.asStateFlow()

    private var process: Process? = null

    /** Sondes de vie, dans l'ordre : toute 2xx/3xx/4xx prouve que *quelque chose* répond. */
    private val healthPaths = listOf("/health", "/", "/api/health")

    private fun get(path: String): Int? = try {
        val conn = URI(path).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 1500
        conn.readTimeout = 1500
        conn.requestMethod = "GET"
        conn.responseCode
    } catch (_: Exception) {
        null
    }

    override suspend fun checkHealth(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = url.trimEnd('/')
            val code = healthPaths.firstNotNullOfOrNull { get("$root$it") }
            val ok = code != null && code < 500
            _state.value = _state.value.copy(
                running = ok,
                url = url,
                error = if (ok) null else _state.value.error,
                message = if (ok) "En ligne (port ${_state.value.port})" else "Arrêté",
            )
            ok
        } catch (_: Exception) {
            _state.value = _state.value.copy(
                running = false,
                url = url,
                message = "Arrêté",
            )
            false
        }
    }

    override suspend fun startBackend(
        customDir: String?,
        port: Int,
        customCommand: List<String>?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val currentUrl = "http://localhost:$port"
        if (checkHealth(currentUrl)) {
            _state.value = _state.value.copy(
                running = true,
                starting = false,
                port = port,
                url = currentUrl,
                message = "Le backend est déjà en ligne.",
            )
            return@withContext Result.success(Unit)
        }
        // Un précédent processus (autre projet / autre port) ne doit pas survivre en tâche de fond.
        stopProcess()

        _state.value = _state.value.copy(
            starting = true,
            error = null,
            diagnostic = null,
            rawLogs = null,
            exitCode = null,
            port = port,
            url = currentUrl,
            message = "Démarrage du backend...",
        )

        // 1) Projet sélectionné d'abord : c'est LUI qu'on penteste, pas forcément CodeShield.
        //    Si un dossier est sélectionné mais sans backend détectable, on échoue
        //    avec un message clair plutôt que de démarrer silencieusement CodeShield.
        val selected = customDir?.let(::File)?.takeIf { it.exists() && it.isDirectory }
        val selectedPlan: Triple<File, List<String>, String>? = when {
            customCommand != null && customCommand.isNotEmpty() -> {
                val base = selected ?: File(System.getProperty("user.dir"))
                Triple(base, customCommand, "Commande personnalisée")
            }
            selected != null -> {
                detectStack(selected, port)?.let { plan -> Triple(File(plan.workDir), plan.command, plan.label) }
            }
            else -> null
        }

        val (workDir, cmd, stackLabel) = when {
            selectedPlan != null -> selectedPlan
            selected != null -> {
                val err = "Aucun backend détecté dans ${selected.absolutePath} " +
                    "(ni Docker Compose, ni FastAPI, ni package.json, ni main.py, ni artisan/php, ni go/rust/gradle). " +
                    "Vous pouvez spécifier une commande de lancement personnalisée dans les options Pentest."
                val diag = BackendDiagnosticEngine.diagnoseLocally(selected.absolutePath, "Inconnu", null, err)
                _state.value = _state.value.copy(starting = false, error = err, message = err, diagnostic = diag)
                return@withContext Result.failure(IllegalStateException(err))
            }
            else -> {
                // Aucun projet sélectionné : repli pratique sur le backend CodeShield
                // (trouvé en remontant depuis le cwd, quel que soit le lanceur).
                val codeShieldDir = resolveBackendDir(null)
                if (codeShieldDir == null) {
                    val err = "Répertoire backend introuvable. Spécifiez le dossier du projet."
                    val diag = BackendDiagnosticEngine.diagnoseLocally(System.getProperty("user.dir"), "Go CodeShield", null, err)
                    _state.value = _state.value.copy(starting = false, error = err, message = err, diagnostic = diag)
                    return@withContext Result.failure(IllegalStateException(err))
                }
                Triple(codeShieldDir, goApiCommand(port), "Go CodeShield")
            }
        }
        try {
            val pb = ProcessBuilder(cmd)
                .directory(workDir)
                .redirectErrorStream(true)

            val proc = pb.start()
            process = proc
            val pid = runCatching { proc.pid() }.getOrNull()

            // Attend que le serveur soit prêt (max 15s : npm/vite démarre plus lentement qu'uvicorn)
            var ready = false
            for (i in 1..30) {
                delay(500)
                if (!proc.isAlive) {
                    val exit = proc.exitValue()
                    val output = runCatching { proc.inputStream.bufferedReader().readText().takeLast(4000) }.getOrDefault("")
                    val err = "Échec du démarrage [$stackLabel] (code $exit) : ${output.lines().filter { it.isNotBlank() }.takeLast(3).joinToString(" ").ifBlank { "processus terminé" }}"
                    val diagnostic = BackendDiagnosticEngine.diagnoseLocally(
                        workDir = workDir.absolutePath,
                        stackLabel = stackLabel,
                        exitCode = exit,
                        logs = output.ifBlank { err },
                    )
                    _state.value = _state.value.copy(
                        starting = false,
                        running = false,
                        error = err,
                        message = err,
                        effectiveCommand = cmd,
                        rawLogs = output.ifBlank { err },
                        exitCode = exit,
                        diagnostic = diagnostic,
                        projectDir = workDir.absolutePath,
                        stackLabel = stackLabel,
                    )
                    return@withContext Result.failure(IllegalStateException(err))
                }
                if (checkHealth(currentUrl)) {
                    ready = true
                    break
                }
            }

            if (ready) {
                _state.value = _state.value.copy(
                    running = true,
                    starting = false,
                    pid = pid,
                    effectiveCommand = cmd,
                    error = null,
                    diagnostic = null,
                    rawLogs = null,
                    exitCode = null,
                    projectDir = workDir.absolutePath,
                    stackLabel = stackLabel,
                    message = "Backend démarré [$stackLabel] (port $port) ✓",
                )
                Result.success(Unit)
            } else {
                val exit = if (!proc.isAlive) proc.exitValue() else null
                val output = runCatching { proc.inputStream.bufferedReader().readText().takeLast(4000) }.getOrDefault("")
                val err = "[$stackLabel] lancé mais ne répond pas sur $currentUrl après 15s."
                val diagnostic = BackendDiagnosticEngine.diagnoseLocally(
                    workDir = workDir.absolutePath,
                    stackLabel = stackLabel,
                    exitCode = exit,
                    logs = output.ifBlank { err },
                )
                _state.value = _state.value.copy(
                    starting = false,
                    running = false,
                    error = err,
                    message = err,
                    effectiveCommand = cmd,
                    rawLogs = output.ifBlank { err },
                    exitCode = exit,
                    diagnostic = diagnostic,
                    projectDir = workDir.absolutePath,
                    stackLabel = stackLabel,
                )
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            val err = "Erreur lancement backend [$stackLabel] : ${e.message}"
            val diagnostic = BackendDiagnosticEngine.diagnoseLocally(
                workDir = workDir.absolutePath,
                stackLabel = stackLabel,
                exitCode = null,
                logs = err,
            )
            _state.value = _state.value.copy(
                starting = false,
                running = false,
                error = err,
                message = err,
                effectiveCommand = cmd,
                rawLogs = err,
                diagnostic = diagnostic,
                projectDir = workDir.absolutePath,
                stackLabel = stackLabel,
            )
            Result.failure(e)
        }
    }

    override suspend fun stopBackend(): Result<Unit> = withContext(Dispatchers.IO) {
        stopProcess()
        _state.value = _state.value.copy(running = false, starting = false, pid = null, message = "Arrêté")
        Result.success(Unit)
    }

    private fun stopProcess() {
        val proc = process
        if (proc != null && proc.isAlive) {
            proc.destroy()
            Thread.sleep(500)
            if (proc.isAlive) proc.destroyForcibly()
            process = null
        }
    }

    /** Stack détectée dans le dossier projet sélectionné (quel que soit le projet). */
    private data class StackPlan(val workDir: String, val command: List<String>, val label: String)

    private fun detectStack(dir: File, port: Int): StackPlan? {
        val portStr = port.toString()

        // 1. Docker Compose (si compose.yaml ou docker-compose.yml présent)
        val composeFiles = listOf("compose.yaml", "compose.yml", "docker-compose.yaml", "docker-compose.yml")
        val composeFile = composeFiles.firstOrNull { File(dir, it).exists() }
        if (composeFile != null) {
            return StackPlan(
                dir.absolutePath,
                listOf("docker", "compose", "up"),
                "Docker Compose ($composeFile)",
            )
        }

        // 2. Backend CodeShield lui-même (Go : backend-go/cmd/api)
        listOf(dir, File(dir, "backend-go")).firstOrNull(::isCodeShieldGoBackend)?.let { bd ->
            return StackPlan(bd.absolutePath, goApiCommand(port), "Go CodeShield")
        }

        // 3. Django (manage.py)
        val managePy = File(dir, "manage.py")
        if (managePy.exists()) {
            val venvPython = File(dir, ".venv/bin/python")
            val py = if (venvPython.exists() && venvPython.canExecute()) venvPython.absolutePath else "python3"
            return StackPlan(
                dir.absolutePath,
                listOf(py, "manage.py", "runserver", "127.0.0.1:$portStr"),
                "Python/Django",
            )
        }

        // 4. Node (vite / next / CRA / générique package.json)
        val pkg = File(dir, "package.json")
        if (pkg.exists()) {
            val content = runCatching { pkg.readText() }.getOrDefault("")
            val isVite = content.contains("vite", ignoreCase = true)
            val isNext = content.contains("\"next\"", ignoreCase = true) || content.contains("next dev", ignoreCase = true)
            val isCra = content.contains("react-scripts", ignoreCase = true)
            val args = when {
                isVite -> listOf("sh", "-c", "npx vite --port $portStr --host 127.0.0.1")
                isNext -> listOf("sh", "-c", "npm run dev -- -p $portStr")
                isCra -> listOf("sh", "-c", "PORT=$portStr BROWSER=none npm start")
                else -> listOf("sh", "-c", "npm run dev -- --port $portStr")
            }
            val label = when {
                isVite -> "Node/Vite"
                isNext -> "Node/Next"
                isCra -> "Node/CRA"
                else -> "Node"
            }
            return StackPlan(dir.absolutePath, args, label)
        }

        // 5. Python générique (FastAPI / Flask / wsgi / script)
        val pythonCandidates = listOf(
            "app/main.py" to "app.main",
            "main.py" to "main",
            "app.py" to "app",
            "src/main.py" to "src.main",
            "api/main.py" to "api.main",
        )
        for ((rel, module) in pythonCandidates) {
            val f = File(dir, rel)
            if (f.exists()) {
                val venvPython = File(dir, ".venv/bin/python")
                val py = if (venvPython.exists() && venvPython.canExecute()) venvPython.absolutePath else "python3"
                val text = runCatching { f.readText() }.getOrDefault("")
                val isFlask = text.contains("Flask(", ignoreCase = false)
                val cmd = if (isFlask) {
                    listOf(py, "-m", "flask", "run", "--port", portStr, "--host", "127.0.0.1")
                } else {
                    listOf(py, "-m", "uvicorn", "$module:app", "--port", portStr, "--host", "127.0.0.1")
                }
                val label = if (isFlask) "Python/Flask ($rel)" else "Python/FastAPI ($rel)"
                return StackPlan(dir.absolutePath, cmd, label)
            }
        }

        // 6. PHP (Laravel, Symfony, index.php ou projet web PHP natif)
        val isLaravel = File(dir, "artisan").exists()
        val hasIndexPhp = File(dir, "index.php").exists()
        val hasPublicIndex = File(dir, "public/index.php").exists()
        val hasComposer = File(dir, "composer.json").exists()
        val hasPhpFiles = hasIndexPhp || hasPublicIndex || hasComposer ||
            (dir.listFiles()?.any { it.isFile && it.extension.equals("php", ignoreCase = true) } == true)

        if (isLaravel) {
            return StackPlan(
                dir.absolutePath,
                listOf("php", "artisan", "serve", "--port=$portStr", "--host=127.0.0.1"),
                "PHP/Laravel",
            )
        } else if (hasPhpFiles) {
            val cmd = if (hasPublicIndex) {
                listOf("php", "-S", "127.0.0.1:$portStr", "-t", "public")
            } else {
                listOf("php", "-S", "127.0.0.1:$portStr")
            }
            val label = when {
                hasComposer -> "PHP/Composer"
                hasIndexPhp || hasPublicIndex -> "PHP (Native Web)"
                else -> "PHP"
            }
            return StackPlan(dir.absolutePath, cmd, label)
        }

        // 7. Go (go.mod ou main.go)
        if (File(dir, "go.mod").exists() || File(dir, "main.go").exists()) {
            return StackPlan(
                dir.absolutePath,
                listOf("go", "run", "."),
                "Go Web",
            )
        }

        // 8. Rust (Cargo.toml)
        if (File(dir, "Cargo.toml").exists()) {
            return StackPlan(
                dir.absolutePath,
                listOf("cargo", "run"),
                "Rust/Cargo",
            )
        }

        // 9. Java / Kotlin Gradle (gradlew ou build.gradle)
        val gradlew = File(dir, "gradlew")
        if (gradlew.exists() || File(dir, "build.gradle").exists() || File(dir, "build.gradle.kts").exists()) {
            val gradCmd = if (gradlew.exists() && gradlew.canExecute()) "./gradlew" else "gradle"
            return StackPlan(
                dir.absolutePath,
                listOf(gradCmd, "bootRun"),
                "Java/Gradle (bootRun)",
            )
        }

        // 10. Java Maven (mvnw ou pom.xml)
        val mvnw = File(dir, "mvnw")
        if (mvnw.exists() || File(dir, "pom.xml").exists()) {
            val mvnCmd = if (mvnw.exists() && mvnw.canExecute()) "./mvnw" else "mvn"
            return StackPlan(
                dir.absolutePath,
                listOf(mvnCmd, "spring-boot:run"),
                "Java/Maven (Spring Boot)",
            )
        }

        // 11. Ruby on Rails
        if (File(dir, "bin/rails").exists() || (File(dir, "Gemfile").exists() && File(dir, "config.ru").exists())) {
            return StackPlan(
                dir.absolutePath,
                listOf("bundle", "exec", "rails", "server", "-p", portStr, "-b", "127.0.0.1"),
                "Ruby on Rails",
            )
        }

        // 12. .NET / C#
        val hasDotnet = dir.listFiles()?.any { it.name.endsWith(".csproj") || it.name.endsWith(".sln") } == true
        if (hasDotnet) {
            return StackPlan(
                dir.absolutePath,
                listOf("dotnet", "run", "--urls=http://127.0.0.1:$portStr"),
                ".NET / C#",
            )
        }

        return null
    }

    private fun resolveBackendDir(customDir: String?): File? {
        // Remonte jusqu'à 4 niveaux depuis le cwd (couvre : desktop/, desktop/desktopApp/,
        // IDE, Gradle run) en testant `<niveau>/backend-go` puis `<niveau>` lui-même.
        val cwd = File(System.getProperty("user.dir"))
        val ancestors = generateSequence(cwd) { it.parentFile }.take(5).toList()
        val candidates = buildList {
            customDir?.let { add(File(it, "backend-go")); add(File(it)) }
            defaultBackendDir?.let { add(File(it)) }
            for (base in ancestors) {
                add(File(base, "backend-go"))
                add(base)
            }
        }
        return candidates.firstOrNull(::isCodeShieldGoBackend)
    }

    /** Module Go du backend CodeShield (go.mod `codeshield/backend` + cmd/api). */
    private fun isCodeShieldGoBackend(dir: File): Boolean =
        File(dir, "cmd/api/main.go").isFile &&
            runCatching { File(dir, "go.mod").readText() }.getOrDefault("").contains("module codeshield/backend")

    /**
     * `go run ./cmd/api` sur le port demandé. DATABASE_URL est hérité de
     * l'environnement (voir `go run ./cmd/devdb` pour une base locale).
     */
    private fun goApiCommand(port: Int): List<String> =
        listOf("env", "PORT=$port", "go", "run", "./cmd/api")
}
