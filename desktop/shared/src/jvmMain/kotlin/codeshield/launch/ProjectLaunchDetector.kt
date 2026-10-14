package codeshield.launch

import java.io.File

/**
 * Analyse heuristique d'un dossier projet (quel qu'il soit) pour deviner
 * comment lancer backend / frontend sur un port isolé en vue d'un pentest
 * de charge, sans toucher à l'instance dev.
 *
 * 100% local (lecture de fichiers marqueurs uniquement) : fonctionne pour
 * FastAPI/uvicorn, Vite, Next, CRA, Dockerfile, compose, etc.
 */
data class LaunchCommand(
    val label: String,
    val devCommand: String,
    val isolatedCommand: String,
    val workDir: String,
    val devPort: Int,
    val isolatedPort: Int,
)

data class ProjectLaunchPlan(
    val projectDir: String,
    val backend: LaunchCommand?,
    val frontend: LaunchCommand?,
    val notes: List<String>,
)

object ProjectLaunchDetector {
    fun detect(projectDir: String): ProjectLaunchPlan {
        val root = File(projectDir)
        val backendDir = resolveBackendDir(root)
        val frontendDir = resolveFrontendDir(root)
        val notes = mutableListOf<String>()

        val backend = backendDir?.let { dir ->
            val text = readTextSafe(File(dir, "pyproject.toml")) +
                readTextSafe(File(dir, "requirements.txt"))
            val isFastApi = text.contains("fastapi", true) ||
                File(dir, "app/main.py").exists()
            if (isFastApi) {
                notes += "Backend FastAPI détecté dans ${dir.name}. Port isolé 8001 conseillé pour pentest."
                LaunchCommand(
                    label = "FastAPI (uvicorn)",
                    devCommand = "uv run uvicorn app.main:app --reload",
                    isolatedCommand = "uv run uvicorn app.main:app --host 127.0.0.1 --port 8001",
                    workDir = dir.absolutePath,
                    devPort = 8000,
                    isolatedPort = 8001,
                )
            } else null
        }

        val frontend = frontendDir?.let { dir ->
            val pkg = readTextSafe(File(dir, "package.json"))
            val cmd = when {
                pkg.contains("\"vite\"", true) || pkg.contains("\"dev\": \"vite\"", true) ->
                    "npm run dev -- --port 5180" to 5180
                pkg.contains("next", true) ->
                    "npm run dev -- -p 5180" to 5180
                pkg.contains("react-scripts", true) ->
                    "PORT=5180 npm start" to 5180
                else -> "npm run dev" to 5173
            }
            notes += "Frontend Node détecté dans ${dir.name}. VITE_API_URL=http://localhost:${backend?.isolatedPort ?: 8001} pour viser le backend isolé."
            LaunchCommand(
                label = "Frontend (node)",
                devCommand = "npm run dev",
                isolatedCommand = cmd.first,
                workDir = dir.absolutePath,
                devPort = 5173,
                isolatedPort = cmd.second,
            )
        }

        if (File(root, "docker-compose.yml").exists() || File(root, "compose.yaml").exists()) {
            notes += "compose détecté : surchargez les ports via `docker compose up -p` ou un fichier override pour isoler le pentest."
        }
        if (backend == null && frontend == null) {
            notes += "Aucun marqueur backend/frontend trouvé. Vérifiez le dossier projet."
        }
        notes += "Pensez à autoriser le port frontend isolé dans CORS backend (ex. http://localhost:5180)."
        return ProjectLaunchPlan(projectDir, backend, frontend, notes)
    }

    private fun resolveBackendDir(root: File): File? {
        val candidates = listOf(
            File(root, "backend"),
            root,
            File(root, "api"),
            File(root, "server"),
        )
        return candidates.firstOrNull { File(it, "app/main.py").exists() }
    }

    private fun resolveFrontendDir(root: File): File? {
        val candidates = listOf(
            File(root, "frontend"),
            root,
            File(root, "web"),
            File(root, "client"),
        )
        return candidates.firstOrNull { File(it, "package.json").exists() }
    }

    private fun readTextSafe(f: File): String = runCatching {
        if (f.exists()) f.readText() else ""
    }.getOrDefault("")
}
