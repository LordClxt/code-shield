package codeshield.backend

import codeshield.llm.LlmClient
import codeshield.llm.LlmSettings
import codeshield.pentest.extractJsonBlock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Moteur de diagnostic intelligent des pannes de démarrage backend.
 * Combine un analyseur de règles heuristiques locales ultra-rapide (0ms)
 * et une analyse approfondie par IA (LLM) quand demandée ou disponible.
 */
object BackendDiagnosticEngine {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Analyse heuristique locale instantanée à partir des logs et du code d'erreur.
     */
    fun diagnoseLocally(
        workDir: String,
        stackLabel: String,
        exitCode: Int?,
        logs: String,
    ): BackendDiagnostic {
        val cleanLogs = logs.trim()

        // 1. Port réseau déjà occupé
        if (cleanLogs.contains("Address already in use", ignoreCase = true) ||
            cleanLogs.contains("Errno 48", ignoreCase = true) ||
            cleanLogs.contains("Errno 98", ignoreCase = true) ||
            cleanLogs.contains("EADDRINUSE", ignoreCase = true) ||
            cleanLogs.contains("bind: address already in use", ignoreCase = true)
        ) {
            val portMatch = Regex("""port\s*(\d+)|\:(\d+)""", RegexOption.IGNORE_CASE).find(cleanLogs)?.let {
                it.groupValues.drop(1).firstOrNull { g -> g.isNotBlank() }
            } ?: "8000"

            val nextPort = (portMatch.toIntOrNull() ?: 8000) + 1

            return BackendDiagnostic(
                category = FailureCategory.PORT_IN_USE,
                title = "Port $portMatch déjà utilisé par un autre processus",
                rootCause = "Une autre application ou une ancienne instance backend écoute déjà sur le port $portMatch.",
                explanation = "Le serveur ne peut pas lier son socket d'écoute (Address already in use). Vous pouvez libérer le port en tuant le processus occupant ou basculer le backend sur le port $nextPort.",
                suggestedFixes = listOf(
                    "lsof -i :$portMatch",
                    "kill -9 \$(lsof -t -i :$portMatch)",
                ),
                quickFixActions = listOf(
                    QuickFixAction(
                        id = "kill_port_process",
                        label = "Libérer le port $portMatch (kill processus)",
                        command = listOf("sh", "-c", "kill -9 \$(lsof -t -i :$portMatch 2>/dev/null) 2>/dev/null || true"),
                        description = "Termine le processus tiers qui bloque le port $portMatch.",
                        autoRestartBackend = true,
                    ),
                ),
                source = DiagnosticSource.LOCAL_RULE,
                rawLogs = cleanLogs,
            )
        }

        // 2. Base de données inexistante / non créée
        // Ex: asyncpg.exceptions.InvalidCatalogNameError: database "ecit_database" does not exist
        // ou: FATAL: database "xyz" does not exist
        val dbNotFoundRegex = Regex("""(?:database|catalog)\s*["']?([a-zA-Z0-9_\-]+)["']?\s*does not exist|InvalidCatalogNameError.*database\s*["']?([a-zA-Z0-9_\-]+)["']?|Unknown database\s*["']?([a-zA-Z0-9_\-]+)["']?""", RegexOption.IGNORE_CASE)
        val dbNotFoundMatch = dbNotFoundRegex.find(cleanLogs)
        val notFoundDbName = dbNotFoundMatch?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
            ?: extractDbNameFromLogs(cleanLogs)

        val isDbNotFound = dbNotFoundMatch != null ||
            (cleanLogs.contains("database", ignoreCase = true) &&
                cleanLogs.contains("does not exist", ignoreCase = true) &&
                !cleanLogs.contains("Target database is not up to date", ignoreCase = true) &&
                !cleanLogs.contains("relation", ignoreCase = true)) ||
            cleanLogs.contains("InvalidCatalogNameError", ignoreCase = true) ||
            cleanLogs.contains("Unknown database", ignoreCase = true)

        if (isDbNotFound) {
            val dbName = notFoundDbName ?: "votre_base"
            val quickFixes = mutableListOf<QuickFixAction>()
            quickFixes += QuickFixAction(
                id = "create_db_docker",
                label = "Créer la base '$dbName' via Docker",
                command = listOf(
                    "sh", "-c",
                    "CONTAINER=\$(docker ps --format '{{.Names}}' | grep -E 'careercommit-db|db|postgres' | head -n1); " +
                    "if [ -n \"\$CONTAINER\" ]; then docker exec \"\$CONTAINER\" createdb -U postgres $dbName; " +
                    "else docker run -d --name ${dbName}_pg -p 5432:5432 -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=$dbName postgres:16-alpine; fi"
                ),
                description = "Crée automatiquement la base de données dans le conteneur PostgreSQL actif ou lance une instance dédiée.",
                autoRestartBackend = true,
            )
            quickFixes += QuickFixAction(
                id = "create_db_local",
                label = "Créer la base via psql local",
                command = listOf("createdb", "-U", "postgres", dbName),
                description = "Exécute l'utilitaire createdb de PostgreSQL localement.",
                autoRestartBackend = true,
            )

            return BackendDiagnostic(
                category = FailureCategory.DATABASE_NOT_FOUND,
                title = "Base de données '$dbName' inexistante",
                rootCause = "Le backend tente de se connecter à la base de données '$dbName', mais celle-ci n'a pas encore été créée dans l'instance active.",
                explanation = "Le serveur de base de données a refusé la connexion car la base cible n'existe pas. Vous devez créer la base avant que le serveur ne puisse initialiser ses tables et lancer ses workers.",
                suggestedFixes = listOf(
                    "docker exec careercommit-db createdb -U postgres $dbName",
                    "docker exec \$(docker ps -q --filter ancestor=postgres | head -n1) createdb -U postgres $dbName",
                    "psql -h localhost -U postgres -c \"CREATE DATABASE $dbName;\"",
                    "createdb -h localhost -U postgres $dbName",
                ),
                quickFixActions = quickFixes,
                source = DiagnosticSource.LOCAL_RULE,
                rawLogs = cleanLogs,
                detectedServiceOrDb = dbName,
            )
        }

        // 3. Échec d'authentification à la base de données
        val authErrorRegex = Regex("""password authentication failed for user\s*["']?([a-zA-Z0-9_\-]+)["']?|Access denied for user\s*['"]?([a-zA-Z0-9_\-]+)['"]?""", RegexOption.IGNORE_CASE)
        val authMatch = authErrorRegex.find(cleanLogs)
        if (authMatch != null || cleanLogs.contains("password authentication failed", ignoreCase = true) || cleanLogs.contains("Access denied for user", ignoreCase = true)) {
            val user = authMatch?.groupValues?.getOrNull(1)?.ifBlank { null } ?: "postgres"
            return BackendDiagnostic(
                category = FailureCategory.ENVIRONMENT_ERROR,
                title = "Échec d'authentification à la base de données (user '$user')",
                rootCause = "Les identifiants de connexion (mot de passe ou nom d'utilisateur '$user') définis dans la configuration ou le fichier .env sont rejetés.",
                explanation = "PostgreSQL ou MySQL a refusé la connexion pour l'utilisateur '$user' (password authentication failed). Vérifiez la variable DATABASE_URL ou POSTGRES_PASSWORD dans votre fichier .env.",
                suggestedFixes = listOf(
                    "Vérifier le fichier .env : DATABASE_URL=postgresql://$user:password@localhost:5432/nom_base",
                    "Modifier le mot de passe utilisateur : psql -U postgres -c \"ALTER USER $user WITH PASSWORD 'nouveau_mdp';\"",
                ),
                quickFixActions = emptyList(),
                source = DiagnosticSource.LOCAL_RULE,
                rawLogs = cleanLogs,
                detectedServiceOrDb = user,
            )
        }

        // 4. Daemon Docker arrêté
        if (cleanLogs.contains("Cannot connect to the Docker daemon", ignoreCase = true) ||
            cleanLogs.contains("Is the docker daemon running", ignoreCase = true) ||
            cleanLogs.contains("docker daemon is not running", ignoreCase = true) ||
            cleanLogs.contains("error during connect: Get \"http://%2Fvar%2Frun%2Fdocker.sock", ignoreCase = true)
        ) {
            return BackendDiagnostic(
                category = FailureCategory.DATABASE_UNREACHABLE,
                title = "Le démon Docker Desktop n'est pas démarré",
                rootCause = "Le client Docker ne parvient pas à se connecter au socket /var/run/docker.sock car le daemon Docker est inactif.",
                explanation = "L'application ou ses services dépendants nécessitent Docker pour tourner. Veuillez lancer l'application Docker Desktop.",
                suggestedFixes = listOf(
                    "open -a Docker",
                    "sudo systemctl start docker",
                ),
                quickFixActions = listOf(
                    QuickFixAction(
                        id = "start_docker_app",
                        label = "Démarrer Docker Desktop (macOS)",
                        command = listOf("open", "-a", "Docker"),
                        description = "Lance l'application Docker Desktop en arrière-plan.",
                        autoRestartBackend = true,
                    ),
                ),
                source = DiagnosticSource.LOCAL_RULE,
                rawLogs = cleanLogs,
                detectedServiceOrDb = "Docker Daemon",
            )
        }

        // 5. Refus de connexion au serveur de base de données (Postgres / MySQL / Redis)
        if (cleanLogs.contains("Connection refused", ignoreCase = true) ||
            cleanLogs.contains("could not connect to server", ignoreCase = true) ||
            cleanLogs.contains("CannotConnectNowError", ignoreCase = true) ||
            cleanLogs.contains("Can't connect to MySQL server", ignoreCase = true) ||
            (cleanLogs.contains("host=localhost", ignoreCase = true) && cleanLogs.contains("port=5432", ignoreCase = true) && cleanLogs.contains("impossible", ignoreCase = true))
        ) {
            val portMatch = Regex("""port[=:]\s*(\d+)""", RegexOption.IGNORE_CASE).find(cleanLogs)?.groupValues?.get(1) ?: "5432"
            val dbName = extractDbNameFromLogs(cleanLogs) ?: "app_db"
            val quickFixes = mutableListOf<QuickFixAction>()
            quickFixes += QuickFixAction(
                id = "start_pg_docker",
                label = "Démarrer un conteneur PostgreSQL (port $portMatch)",
                command = listOf(
                    "docker", "run", "-d",
                    "--name", "codeshield_pg_$portMatch",
                    "-p", "$portMatch:5432",
                    "-e", "POSTGRES_PASSWORD=postgres",
                    "-e", "POSTGRES_DB=$dbName",
                    "postgres:16-alpine",
                ),
                description = "Démarre un conteneur PostgreSQL Docker isolé sur le port $portMatch.",
                autoRestartBackend = true,
            )
            quickFixes += QuickFixAction(
                id = "start_pg_brew",
                label = "Démarrer PostgreSQL (Homebrew)",
                command = listOf("brew", "services", "start", "postgresql@16"),
                description = "Démarre le service PostgreSQL géré par Homebrew sur macOS.",
                autoRestartBackend = true,
            )

            return BackendDiagnostic(
                category = FailureCategory.DATABASE_UNREACHABLE,
                title = "Serveur de base de données inaccessible (port $portMatch)",
                rootCause = "Aucun service ne répond sur localhost:$portMatch. Le serveur de base de données (PostgreSQL/MySQL) est arrêté ou le conteneur n'est pas lancé.",
                explanation = "L'application ne peut pas démarrer car sa tentative de connexion réseau vers la base a échoué (Connection refused). Démarrez Docker ou votre service PostgreSQL local.",
                suggestedFixes = listOf(
                    "docker run -d --name codeshield_pg -p $portMatch:5432 -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=$dbName postgres:16-alpine",
                    "docker compose up -d db",
                    "brew services start postgresql@16",
                ),
                quickFixActions = quickFixes,
                source = DiagnosticSource.LOCAL_RULE,
                rawLogs = cleanLogs,
                detectedServiceOrDb = "PostgreSQL :$portMatch",
            )
        }

        // 6. Binaire ou commande introuvable dans le PATH
        val missingBinRegex = Regex(
            """Cannot run program ["']([^"']+)["'](?:: error=\d+)?|sh:\s*(?:line \d+:\s*)?([a-zA-Z0-9_\-]+):\s*command not found|command not found:\s*([a-zA-Z0-9_\-]+)|bash:\s*([a-zA-Z0-9_\-]+):\s*(?:command not found|No such file)""",
            RegexOption.IGNORE_CASE
        )
        val missingBinMatch = missingBinRegex.find(cleanLogs)
        if (missingBinMatch != null) {
            val rawBin = missingBinMatch.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: "outil"
            val binary = rawBin.substringAfterLast('/').substringAfterLast('\\')

            val quickFixes = mutableListOf<QuickFixAction>()
            val installCmd = when (binary) {
                "php" -> listOf("brew", "install", "php")
                "node", "npm", "npx" -> listOf("brew", "install", "node")
                "python3", "python" -> listOf("brew", "install", "python")
                "docker" -> listOf("brew", "install", "--cask", "docker")
                "go" -> listOf("brew", "install", "go")
                "cargo", "rustc" -> listOf("sh", "-c", "curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y")
                "mvn" -> listOf("brew", "install", "maven")
                "gradle" -> listOf("brew", "install", "gradle")
                else -> emptyList()
            }
            if (installCmd.isNotEmpty()) {
                quickFixes += QuickFixAction(
                    id = "install_$binary",
                    label = "Installer '$binary' via Homebrew",
                    command = installCmd,
                    description = "Installe l'outil '$binary' manquant sur votre machine.",
                    autoRestartBackend = true,
                )
            }

            return BackendDiagnostic(
                category = FailureCategory.MISSING_DEPENDENCY,
                title = "Binaire ou exécutable introuvable : '$binary'",
                rootCause = "La commande '$binary' n'a pas été trouvée sur votre machine ou n'est pas déclarée dans la variable d'environnement PATH.",
                explanation = "Le lanceur CodeShield a tenté d'exécuter '$binary' mais le système d'exploitation a retourné une erreur 'command not found'. Installez le paquet requis pour votre environnement.",
                suggestedFixes = listOf(
                    "brew install $binary",
                    "which $binary || echo 'Ajoutez le répertoire de $binary à votre PATH'",
                ),
                quickFixActions = quickFixes,
                source = DiagnosticSource.LOCAL_RULE,
                rawLogs = cleanLogs,
                detectedServiceOrDb = binary,
            )
        }

        // 7. Dépendances Node.js manquantes (node_modules)
        val nodeNotFoundRegex = Regex("""Cannot find module\s*['"]([^'"]+)['"]|MODULE_NOT_FOUND|ERR_MODULE_NOT_FOUND|vite:\s*not found|next:\s*not found|react-scripts:\s*not found""")
        val nodeMatch = nodeNotFoundRegex.find(cleanLogs)
        if (nodeMatch != null || cleanLogs.contains("ERR_MODULE_NOT_FOUND") || cleanLogs.contains("vite: not found") || cleanLogs.contains("next: not found")) {
            val missingMod = Regex("""Cannot find module\s*['"]([^'"]+)['"]""").find(cleanLogs)?.groupValues?.get(1) ?: "dépendances"
            return BackendDiagnostic(
                category = FailureCategory.MISSING_DEPENDENCY,
                title = "Dépendances Node.js non installées ($missingMod)",
                rootCause = "Le projet Node.js nécessite des modules qui ne sont pas présents dans le dossier node_modules.",
                explanation = "L'interpréteur Node ou le bundler (Vite / Next) a échoué car les modules requis sont introuvables. Exécutez 'npm install' pour installer les dépendances définies dans package.json.",
                suggestedFixes = listOf(
                    "npm install",
                    "pnpm install",
                    "yarn install",
                ),
                quickFixActions = listOf(
                    QuickFixAction(
                        id = "npm_install",
                        label = "Installer les dépendances (npm install)",
                        command = listOf("npm", "install"),
                        description = "Installe tous les paquets Node.js listés dans package.json.",
                        autoRestartBackend = true,
                    )
                ),
                source = DiagnosticSource.LOCAL_RULE,
                rawLogs = cleanLogs,
            )
        }

        // 8. Dépendance Python manquante ou venv inexistant
        val moduleNotFoundRegex = Regex("""(?:ModuleNotFoundError:\s*No module named|ImportError:\s*cannot import name)\s*['"]([^'"]+)['"]""")
        val moduleMatch = moduleNotFoundRegex.find(cleanLogs)
        val venvMissing = cleanLogs.contains(".venv/bin/python: No such file", ignoreCase = true) ||
            cleanLogs.contains(".venv/bin/uvicorn: No such file", ignoreCase = true)

        if (moduleMatch != null || venvMissing) {
            val mod = moduleMatch?.groupValues?.get(1) ?: "packages requis"
            val quickFixes = mutableListOf<QuickFixAction>()
            if (moduleMatch != null) {
                quickFixes += QuickFixAction(
                    id = "install_missing_module",
                    label = "Installer '$mod' via pip",
                    command = listOf("pip", "install", mod),
                    description = "Installe le package manquant dans l'environnement Python courant.",
                    autoRestartBackend = true,
                )
            }
            quickFixes += QuickFixAction(
                id = "create_venv_install",
                label = "Créer .venv et installer requirements.txt",
                command = listOf("sh", "-c", "python3 -m venv .venv && .venv/bin/pip install --upgrade pip && if [ -f requirements.txt ]; then .venv/bin/pip install -r requirements.txt; fi"),
                description = "Recrée l'environnement virtuel local .venv et installe les requirements.",
                autoRestartBackend = true,
            )

            return BackendDiagnostic(
                category = FailureCategory.MISSING_DEPENDENCY,
                title = if (venvMissing) "Environnement virtuel Python (.venv) manquant" else "Module Python manquant : '$mod'",
                rootCause = if (venvMissing) "L'interpréteur .venv/bin/python est introuvable." else "Le package Python '$mod' est importé mais introuvable dans l'environnement actif.",
                explanation = "L'application Python a échoué lors de l'import de ses dépendances. Vous devez installer les modules nécessaires ou recréer l'environnement virtuel .venv.",
                suggestedFixes = listOf(
                    "uv pip install $mod",
                    "pip install $mod",
                    "python3 -m venv .venv && source .venv/bin/activate && pip install -r requirements.txt",
                ),
                quickFixActions = quickFixes,
                source = DiagnosticSource.LOCAL_RULE,
                rawLogs = cleanLogs,
            )
        }

        // 9. Fichier de configuration .env ou variable requise manquante
        if (cleanLogs.contains("FileNotFoundError", ignoreCase = true) && cleanLogs.contains(".env", ignoreCase = true) ||
            cleanLogs.contains("Missing required environment variable", ignoreCase = true) ||
            (cleanLogs.contains("ValidationError", ignoreCase = true) && cleanLogs.contains("Field required", ignoreCase = true)) ||
            (cleanLogs.contains("KeyError", ignoreCase = true) && (cleanLogs.contains("SECRET", ignoreCase = true) || cleanLogs.contains("DATABASE_URL", ignoreCase = true) || cleanLogs.contains("ENV", ignoreCase = true)))
        ) {
            val quickFixes = mutableListOf<QuickFixAction>()
            quickFixes += QuickFixAction(
                id = "create_env_from_example",
                label = "Créer le fichier .env (depuis .env.example)",
                command = listOf("sh", "-c", "if [ -f .env.example ]; then cp .env.example .env; elif [ -f .env.sample ]; then cp .env.sample .env; else touch .env; fi"),
                description = "Copie .env.example vers .env ou crée un fichier .env vide dans le répertoire du projet.",
                autoRestartBackend = true,
            )

            return BackendDiagnostic(
                category = FailureCategory.ENVIRONMENT_ERROR,
                title = "Fichier de configuration '.env' ou variables d'environnement manquantes",
                rootCause = "L'application nécessite des variables de configuration définies dans un fichier .env à la racine.",
                explanation = "Une erreur de validation de configuration ou FileNotFoundError sur '.env' a interrompu le chargement de l'application.",
                suggestedFixes = listOf(
                    "cp .env.example .env",
                    "Vérifier les variables d'environnement requises (DATABASE_URL, SECRET_KEY)",
                ),
                quickFixActions = quickFixes,
                source = DiagnosticSource.LOCAL_RULE,
                rawLogs = cleanLogs,
            )
        }

        // 10. Migrations SQL non appliquées
        if (cleanLogs.contains("Target database is not up to date", ignoreCase = true) ||
            cleanLogs.contains("alembic.util.exc.CommandError", ignoreCase = true) ||
            cleanLogs.contains("ProgrammingError: relation", ignoreCase = true) ||
            (cleanLogs.contains("relation", ignoreCase = true) && cleanLogs.contains("does not exist", ignoreCase = true)) ||
            (cleanLogs.contains("Table", ignoreCase = true) && cleanLogs.contains("doesn't exist", ignoreCase = true)) ||
            cleanLogs.contains("P3005", ignoreCase = true)
        ) {
            val quickFixes = mutableListOf<QuickFixAction>()
            quickFixes += QuickFixAction(
                id = "run_alembic_migrate",
                label = "Appliquer les migrations Alembic (upgrade head)",
                command = listOf("sh", "-c", "if [ -x .venv/bin/alembic ]; then .venv/bin/alembic upgrade head; else alembic upgrade head; fi"),
                description = "Applique toutes les migrations SQLAlchemy/Alembic en attente sur la base de données.",
                autoRestartBackend = true,
            )
            quickFixes += QuickFixAction(
                id = "run_django_migrate",
                label = "Appliquer les migrations Django (manage.py migrate)",
                command = listOf("sh", "-c", "python3 manage.py migrate"),
                description = "Applique les migrations Django ORM.",
                autoRestartBackend = true,
            )

            return BackendDiagnostic(
                category = FailureCategory.MIGRATION_PENDING,
                title = "Migrations de base de données non appliquées",
                rootCause = "Le schéma de la base de données n'est pas synchronisé avec les modèles de l'application.",
                explanation = "Une table ou une relation requise par le code n'existe pas encore en base de données. Exécutez l'outil de migration (Alembic, Django, Prisma) pour créer les tables.",
                suggestedFixes = listOf(
                    "alembic upgrade head",
                    "python3 manage.py migrate",
                    "npx prisma migrate dev",
                    "php artisan migrate",
                ),
                quickFixActions = quickFixes,
                source = DiagnosticSource.LOCAL_RULE,
                rawLogs = cleanLogs,
            )
        }

        // 11. Permission refusée
        if (cleanLogs.contains("Permission denied", ignoreCase = true) ||
            cleanLogs.contains("error=13", ignoreCase = true) ||
            cleanLogs.contains("EACCES", ignoreCase = true)
        ) {
            return BackendDiagnostic(
                category = FailureCategory.PERMISSION_DENIED,
                title = "Permission d'exécution refusée",
                rootCause = "Les droits d'accès du système de fichiers empêchent l'exécution d'un binaire ou d'un script.",
                explanation = "Le processus s'est arrêté avec l'erreur 'Permission denied' (code EACCES). Accordez les droits d'exécution au fichier concerné.",
                suggestedFixes = listOf(
                    "chmod +x ./gradlew",
                    "chmod +x script.sh",
                ),
                quickFixActions = listOf(
                    QuickFixAction(
                        id = "chmod_all_scripts",
                        label = "Rendre les scripts exécutables (chmod +x)",
                        command = listOf("sh", "-c", "chmod +x gradlew mvnw *.sh 2>/dev/null || true"),
                        description = "Ajoute les droits d'exécution sur les scripts du projet (gradlew, mvnw, etc.).",
                        autoRestartBackend = true,
                    )
                ),
                source = DiagnosticSource.LOCAL_RULE,
                rawLogs = cleanLogs,
            )
        }

        // 12. Timeout / Serveur sans réponse aux sondes HTTP
        if (cleanLogs.contains("ne répond pas sur", ignoreCase = true) && cleanLogs.contains("après 15s", ignoreCase = true)) {
            return BackendDiagnostic(
                category = FailureCategory.GENERIC_CRASH,
                title = "Délai de démarrage dépassé (15s sans réponse HTTP)",
                rootCause = "Le processus a démarré mais ne répond à aucune requête HTTP sur le port attendu.",
                explanation = "Le backend a été lancé mais aucune réponse HTTP valide (2xx/3xx/4xx) n'a été reçue après 15 secondes. L'application écoute peut-être sur un autre port, nécessite une compilation préalable plus longue, ou est bloquée dans une boucle infinie.",
                suggestedFixes = listOf(
                    "Vérifier le port effectif dans les logs du serveur",
                    "Tester l'accès direct avec curl : curl -v http://localhost:8000/",
                    "Augmenter ou personnaliser la commande de lancement dans les paramètres Pentest",
                ),
                quickFixActions = emptyList(),
                source = DiagnosticSource.LOCAL_RULE,
                rawLogs = cleanLogs,
            )
        }

        // 13. Exception applicative avec trace de stack (ex: AttributeError, KeyError, ValueError)
        val exceptionLine = cleanLogs.lines().findLast {
            it.matches(Regex("""^[A-Z][a-zA-Z0-9_]*(Error|Exception):.*"""))
        }
        val fileLineMatch = Regex("""File\s*["']([^"']+)["'],\s*line\s*(\d+)(?:,\s*in\s*(\w+))?""")
            .findAll(cleanLogs).lastOrNull()

        if (exceptionLine != null) {
            val loc = if (fileLineMatch != null) {
                val f = fileLineMatch.groupValues[1].substringAfterLast('/')
                val l = fileLineMatch.groupValues[2]
                " ($f:L$l)"
            } else ""

            return BackendDiagnostic(
                category = FailureCategory.RUNTIME_EXCEPTION,
                title = "Crash applicatif : ${exceptionLine.take(60)}",
                rootCause = "L'application a levé une exception non gérée durant sa phase d'initialisation : $exceptionLine$loc.",
                explanation = "Une erreur dans la logique métier ou le code de démarrage (lifespan/seeding) a interrompu le serveur avec le code $exitCode.",
                suggestedFixes = listOf(
                    "Inspecter la ligne signalée dans la trace : $loc",
                    "Vérifier les données initiales ou migrations de base de données.",
                ),
                source = DiagnosticSource.LOCAL_RULE,
                rawLogs = cleanLogs,
            )
        }

        // 14. Détection générique par défaut
        return BackendDiagnostic(
            category = FailureCategory.GENERIC_CRASH,
            title = "Échec du processus backend [$stackLabel] (code ${exitCode ?: "?"})",
            rootCause = "Le processus s'est arrêté prématurément sans que le serveur HTTP ne devienne joignable.",
            explanation = "Consultez les dernières lignes de log capturées pour diagnostiquer la cause exacte du crash applicatif ou demandez l'aide de l'IA pour une analyse approfondie.",
            suggestedFixes = listOf(
                "Vérifier les logs complets ci-dessous",
                "Lancer le démarrage manuellement dans le terminal pour tester la stack",
            ),
            source = DiagnosticSource.LOCAL_RULE,
            rawLogs = cleanLogs,
        )
    }

    /**
     * Analyse avancée assistée par IA via le client LLM configuré.
     */
    suspend fun diagnoseWithLlm(
        llm: LlmClient,
        settings: LlmSettings,
        workDir: String,
        stackLabel: String,
        exitCode: Int?,
        logs: String,
    ): Result<BackendDiagnostic> = runCatching {
        val local = diagnoseLocally(workDir, stackLabel, exitCode, logs)
        if (!settings.enabled || settings.apiKey.isBlank() && settings.provider != codeshield.llm.LlmProvider.OLLAMA) {
            return@runCatching local
        }

        val logTail = logs.takeLast(3000)
        val prompt = """
Tu es un ingénieur expert DevSecOps et sécurité applicative de CodeShield.
Le backend d'une application cible a échoué au démarrage lors de la préparation d'un audit de sécurité (Pentest).

Contexte du projet :
- Dossier : $workDir
- Stack détectée : $stackLabel
- Code de sortie du processus : $exitCode

Logs d'erreur (STDOUT/STDERR) :
```
$logTail
```

Diagnostic local préalable :
- Catégorie : ${local.category.name}
- Titre : ${local.title}

Tâche :
Fournis un diagnostic précis et les actions de remédiation en français, au format JSON strict :
{
  "category": "${local.category.name}",
  "title": "Titre clair et concis (ex: Base de données 'ecit_database' inexistante)",
  "rootCause": "Cause racine identifiée en 1 ou 2 phrases",
  "explanation": "Explication technique didactique et claire pour le développeur",
  "suggestedFixes": [
    "commande bash 1",
    "commande bash 2"
  ]
}
Réponds UNIQUEMENT le bloc JSON.
""".trimIndent()

        val response = llm.complete(prompt, settings).getOrThrow()
        val rawJson = extractJsonBlock(response.text)
            ?: return@runCatching local.copy(source = DiagnosticSource.LLM)

        val dto = json.decodeFromString<LlmDiagnosticDto>(rawJson)
        val cat = runCatching { FailureCategory.valueOf(dto.category) }.getOrDefault(local.category)

        local.copy(
            category = cat,
            title = dto.title.ifBlank { local.title },
            rootCause = dto.rootCause.ifBlank { local.rootCause },
            explanation = dto.explanation.ifBlank { local.explanation },
            suggestedFixes = if (dto.suggestedFixes.isNotEmpty()) dto.suggestedFixes else local.suggestedFixes,
            source = DiagnosticSource.LLM,
        )
    }

    private fun extractDbNameFromLogs(logs: String): String? {
        val m = Regex("""db=([a-zA-Z0-9_\-]+)|dbname=([a-zA-Z0-9_\-]+)|/([a-zA-Z0-9_\-]+)\b""").find(logs)
        return m?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() && it !in setOf("localhost", "postgres", "tcp") }
    }
}

@Serializable
private data class LlmDiagnosticDto(
    val category: String = "",
    val title: String = "",
    val rootCause: String = "",
    val explanation: String = "",
    val suggestedFixes: List<String> = emptyList(),
)
