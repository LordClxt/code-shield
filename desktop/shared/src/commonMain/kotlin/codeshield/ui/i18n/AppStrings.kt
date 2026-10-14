package codeshield.ui.i18n

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * Contrat de localisation pour CodeShield Desktop.
 */
interface AppStrings {
    val language: AppLanguage

    // Sidebar & Navigation
    val sidebarWorkspace: String
    val sidebarOverview: String
    val sidebarFindings: String
    val sidebarPipeline: String
    val sidebarPentest: String
    val sidebarSync: String
    val sidebarSettings: String
    val sidebarSelectProject: String
    val sidebarNoRecent: String
    val sidebarOpenFolder: String
    val sidebarClearHistory: String
    val sidebarBackendOnline: String
    val sidebarBackendOffline: String
    fun sidebarEnginesReady(ready: Int, total: Int): String
    val sidebarLanguageSwitch: String

    // Onglets / Workspaces
    val tabUntitledProject: String
    val tabNew: String

    // Paramètres (Settings)
    val settingsTitle: String
    val settingsSubtitle: String
    val settingsTestLlm: String
    val settingsTesting: String
    val settingsZeroCodeNotice: String
    val settingsAiTitle: String
    val settingsAiSubtitle: String
    val settingsAiActive: String
    val settingsAiDisabled: String
    val settingsAiEnableLabel: String
    val settingsAiEnableDesc: String
    val settingsProviderLabel: String
    val settingsProviderDesc: String
    val settingsApiKeyLabel: String
    fun settingsApiKeyDesc(provider: String): String
    val settingsModelLabel: String
    val settingsModelDesc: String
    val settingsFetchModels: String
    val settingsFetchingModels: String
    val settingsMaxTokensLabel: String
    val settingsMaxTokensDesc: String
    val settingsPreferencesTitle: String
    val settingsPreferencesSubtitle: String
    val settingsLanguageLabel: String
    val settingsLanguageDesc: String
    val settingsThemeLabel: String
    val settingsThemeDesc: String
    val settingsThemeDark: String
    val settingsThemeLight: String

    // Vue d'ensemble (Overview)
    val overviewTitle: String
    val overviewSubtitle: String
    val overviewKpiScore: String
    val overviewKpiCritical: String
    val overviewKpiHigh: String
    val overviewKpiTotal: String
    val overviewConfigTitle: String
    val overviewConfigSubtitle: String
    val overviewProjectDirLabel: String
    val overviewBrowse: String
    val overviewDirNotFound: String
    val overviewRunScan: String
    val overviewScanning: String
    val overviewEnginesTitle: String
    val overviewEnginesSubtitle: String
    val overviewInstall: String
    val overviewReady: String
    val overviewMissing: String

    // Pentest & Reconnaissance
    val pentestTitle: String
    val pentestSubtitle: String
    val pentestPingHealth: String
    val pentestChecking: String
    val pentestRunPentest: String
    val pentestAuditing: String
    val pentestLifecycleTitle: String
    fun pentestActiveTarget(url: String): String
    fun pentestTargetProject(dir: String): String
    val pentestStartBackend: String
    val pentestStarting: String
    val pentestTargetOptions: String
    val pentestDirectModeBadge: String
    val pentestLocalModeBadge: String
    fun pentestStatusOnline(port: Int): String
    val pentestStatusOffline: String
    val pentestStatusStarting: String
    val pentestStatusDirect: String
    val pentestReconTitle: String
    val pentestReconSubtitle: String
    val pentestNoTargetFolder: String
    val pentestBrowseFolder: String
    val pentestRescan: String
    val pentestAnalyzing: String
    val pentestNoFolderTitle: String
    val pentestNoFolderHint: String
    val pentestSelectDirectory: String
    val pentestScanningCodebase: String
    val pentestNoReportTitle: String
    val pentestNoReportYet: String
    val pentestNoReportHint: String
    val pentestStartAudit: String
    fun pentestDiscoveredEndpoints(filtered: Int, total: Int): String
    val pentestFilterRoutesPlaceholder: String
    fun pentestNoMatchingRoutes(query: String): String
    val pentestAuditResultsTitle: String
    fun pentestVectorsTested(count: Int): String
    val pentestTargetUrlLabel: String
    val pentestCustomCommandLabel: String

    // Vulnérabilités (Findings)
    val findingsTitle: String
    val findingsNoFindingsTitle: String
    val findingsNoFindingsHint: String
    val findingsNoFilterMatch: String
    val findingsTableSeverity: String
    val findingsTableRule: String
    val findingsTableFile: String
    val findingsTableLine: String
    val findingsTableTool: String
    val findingsSearchPlaceholder: String
    val findingsTriageButton: String
    val findingsTriaging: String
    val findingsExplainButton: String
    val findingsExplaining: String

    // Pipeline / Console
    val pipelineTitle: String
    val pipelineSubtitle: String
    val pipelineNoOutputTitle: String
    val pipelineNoOutputHint: String
    val pipelineClearHistory: String
    val pipelineHistoryTitle: String

    // Synchronisation Cloud
    val syncTitle: String
    val syncSubtitle: String
    val syncServerUrl: String
    val syncProjectId: String
    val syncApiToken: String
    val syncButton: String
    val syncing: String
    val syncNoSyncTitle: String
    val syncNoSyncHint: String
    val syncLastSync: String
}

/**
 * Traductions en Français.
 */
object StringsFr : AppStrings {
    override val language: AppLanguage = AppLanguage.FR

    override val sidebarWorkspace = "Espace de travail"
    override val sidebarOverview = "Vue d'ensemble"
    override val sidebarFindings = "Vulnérabilités"
    override val sidebarPipeline = "Pipeline"
    override val sidebarPentest = "Pentest"
    override val sidebarSync = "Synchronisation"
    override val sidebarSettings = "Paramètres"
    override val sidebarSelectProject = "Sélectionner un projet…"
    override val sidebarNoRecent = "Aucun projet récent"
    override val sidebarOpenFolder = "Ouvrir un dossier…"
    override val sidebarClearHistory = "Effacer l'historique"
    override val sidebarBackendOnline = "Backend en ligne"
    override val sidebarBackendOffline = "Backend hors-ligne"
    override fun sidebarEnginesReady(ready: Int, total: Int) = "$ready/$total moteurs prêts"
    override val sidebarLanguageSwitch = "Langue"

    override val tabUntitledProject = "Projet sans titre"
    override val tabNew = "Nouvel onglet"

    override val settingsTitle = "Paramètres"
    override val settingsSubtitle = "Configuration de l'assistant de sécurité et préférences de l'application"
    override val settingsTestLlm = "Tester la connexion LLM"
    override val settingsTesting = "Test en cours…"
    override val settingsZeroCodeNotice = "Politique zéro code : Les requêtes envoyées à votre fournisseur LLM contiennent uniquement les identifiants de règles, chemins de fichiers et niveaux de sévérité. Le code source brut n'est jamais transmis."
    override val settingsAiTitle = "Assistant IA & Explications"
    override val settingsAiSubtitle = "Active le triage automatisé des failles et les explications contextuelles de remédiation"
    override val settingsAiActive = "Actif"
    override val settingsAiDisabled = "Désactivé"
    override val settingsAiEnableLabel = "Activer l'assistant IA"
    override val settingsAiEnableDesc = "Lorsque désactivé, seules les règles locales hors-ligne sont utilisées."
    override val settingsProviderLabel = "Fournisseur LLM"
    override val settingsProviderDesc = "Sélectionnez votre fournisseur LLM ou choisissez Ollama en local pour une confidentialité totale."
    override val settingsApiKeyLabel = "Clé API"
    override fun settingsApiKeyDesc(provider: String) = "Clé d'authentification pour $provider (chiffrée et stockée localement)."
    override val settingsModelLabel = "Modèle"
    override val settingsModelDesc = "Modèle LLM utilisé pour le triage et l'explication des failles."
    override val settingsFetchModels = "Récupérer"
    override val settingsFetchingModels = "Chargement…"
    override val settingsMaxTokensLabel = "Budget de tokens"
    override val settingsMaxTokensDesc = "Limite de tokens par réponse pour éviter les coupures (recommandé: 4096)."
    override val settingsPreferencesTitle = "Apparence & Langue"
    override val settingsPreferencesSubtitle = "Personnalisez la langue de l'interface et le thème visuel sombre"
    override val settingsLanguageLabel = "Langue de l'interface"
    override val settingsLanguageDesc = "Choisissez la langue d'affichage globale de l'application CodeShield Desktop."
    override val settingsThemeLabel = "Thème visuel"
    override val settingsThemeDesc = "Palette sombre inspirée de Linear avec fort contraste pour le code."
    override val settingsThemeDark = "Sombre"
    override val settingsThemeLight = "Clair"

    override val overviewTitle = "Vue d'ensemble"
    override val overviewSubtitle = "Santé globale de sécurité et configuration des moteurs de scan"
    override val overviewKpiScore = "SCORE SÉCURITÉ"
    override val overviewKpiCritical = "CRITIQUES"
    override val overviewKpiHigh = "ÉLEVÉES"
    override val overviewKpiTotal = "TOTAL FAILLES"
    override val overviewConfigTitle = "Configuration du scanner"
    override val overviewConfigSubtitle = "Sélectionnez le dossier local et activez les moteurs de détection"
    override val overviewProjectDirLabel = "Dossier du projet"
    override val overviewBrowse = "Parcourir…"
    override val overviewDirNotFound = "Dossier introuvable — vérifiez le chemin spécifié."
    override val overviewRunScan = "Lancer le scan"
    override val overviewScanning = "Scan en cours…"
    override val overviewEnginesTitle = "Moteurs de détection"
    override val overviewEnginesSubtitle = "Outils d'analyse statique SAST, secrets et dépendances SCA"
    override val overviewInstall = "Installer"
    override val overviewReady = "Prêt"
    override val overviewMissing = "Non installé"

    override val pentestTitle = "Pentest Backend"
    override val pentestSubtitle = "Tests dynamiques de sécurité (DAST, injections, contournement d'auth, robustesse aux rafales)"
    override val pentestPingHealth = "Tester la santé"
    override val pentestChecking = "Vérification…"
    override val pentestRunPentest = "Lancer le Pentest"
    override val pentestAuditing = "Audit en cours…"
    override val pentestLifecycleTitle = "Cycle de vie du service backend"
    override fun pentestActiveTarget(url: String) = "Cible active : $url"
    override fun pentestTargetProject(dir: String) = if (dir.isBlank()) "Projet cible : Aucun sélectionné" else "Projet cible : $dir"
    override val pentestStartBackend = "Démarrer le backend"
    override val pentestStarting = "Démarrage…"
    override val pentestTargetOptions = "Options cible"
    override val pentestDirectModeBadge = "Mode Direct / Externe"
    override val pentestLocalModeBadge = "Lancement Local Automatique"
    override fun pentestStatusOnline(port: Int) = "En ligne (port $port)"
    override val pentestStatusOffline = "Hors-ligne"
    override val pentestStatusStarting = "Démarrage…"
    override val pentestStatusDirect = "Direct (sans serveur local)"
    override val pentestReconTitle = "Reconnaissance du projet & surface d'attaque"
    override val pentestReconSubtitle = "Inspection statique locale · Cartographie des routes · Détection multi-stack"
    override val pentestNoTargetFolder = "Aucun dossier cible sélectionné"
    override val pentestBrowseFolder = "Parcourir le dossier…"
    override val pentestRescan = "Re-scanner"
    override val pentestAnalyzing = "Analyse…"
    override val pentestNoFolderTitle = "Aucun dossier projet sélectionné"
    override val pentestNoFolderHint = "Sélectionnez un dossier de code source pour détecter les points d'entrée du framework, le gestionnaire de paquets et les vecteurs d'attaque."
    override val pentestSelectDirectory = "Sélectionner un dossier projet"
    override val pentestScanningCodebase = "Analyse de la structure du code (stack, gestionnaire de paquets, décorateurs de routes, points d'entrée)…"
    override val pentestNoReportTitle = "Aucun rapport d'audit disponible"
    override val pentestNoReportYet = "Aucun test d'intrusion exécuté pour le moment"
    override val pentestNoReportHint = "Sonde votre backend à la recherche d'en-têtes manquants, injections SQL, contournements d'auth et résistance aux rafales de requêtes."
    override val pentestStartAudit = "Démarrer l'audit de robustesse"
    override fun pentestDiscoveredEndpoints(filtered: Int, total: Int) = "Endpoints découverts ($filtered/$total)"
    override val pentestFilterRoutesPlaceholder = "Filtrer les routes (ex: /api, auth, post)…"
    override fun pentestNoMatchingRoutes(query: String) = "Aucun endpoint ne correspond au filtre '$query'."
    override val pentestAuditResultsTitle = "Résultats détaillés de l'audit"
    override fun pentestVectorsTested(count: Int) = "$count vecteurs de sécurité testés"

    override val pentestTargetUrlLabel = "URL de l'application cible"
    override val pentestCustomCommandLabel = "Commande de lancement personnalisée (optionnel)"

    override val findingsTitle = "Vulnérabilités détectées"
    override val findingsNoFindingsTitle = "Aucune vulnérabilité pour le moment"
    override val findingsNoFindingsHint = "Sélectionnez un projet et lancez un scan pour afficher les failles détectées ici."
    override val findingsNoFilterMatch = "Aucune vulnérabilité ne correspond aux filtres actuels."
    override val findingsTableSeverity = "Sévérité"
    override val findingsTableRule = "Règle"
    override val findingsTableFile = "Fichier"
    override val findingsTableLine = "Ligne"
    override val findingsTableTool = "Outil"
    override val findingsSearchPlaceholder = "Rechercher une faille (titre, règle, fichier)…"
    override val findingsTriageButton = "Triage IA du projet"
    override val findingsTriaging = "Triage en cours…"
    override val findingsExplainButton = "Expliquer avec l'IA"
    override val findingsExplaining = "Génération…"

    override val pipelineTitle = "Pipeline d'exécution"
    override val pipelineSubtitle = "Console interactive et logs en direct des moteurs CLI"
    override val pipelineNoOutputTitle = "Aucune sortie pour le moment"
    override val pipelineNoOutputHint = "Lancez un scan depuis la vue d'ensemble ou cliquez sur Lancer le scan pour diffuser les logs."
    override val pipelineClearHistory = "Effacer l'historique"
    override val pipelineHistoryTitle = "Historique des scans passés"

    override val syncTitle = "Synchronisation Cloud"
    override val syncSubtitle = "Envoi sécurisé des métadonnées vers l'API collaborative CodeShield"
    override val syncServerUrl = "URL du serveur API"
    override val syncProjectId = "Identifiant du projet (UUID)"
    override val syncApiToken = "Jeton d'accès API (Bearer)"
    override val syncButton = "Synchroniser maintenant"
    override val syncing = "Synchronisation…"
    override val syncNoSyncTitle = "Aucune synchronisation effectuée"
    override val syncNoSyncHint = "Envoyez un rapport pour réconcilier les résultats locaux avec la base de données (résolution différentielle automatique)."
    override val syncLastSync = "Dernière synchronisation"
}

/**
 * Traductions en Anglais.
 */
object StringsEn : AppStrings {
    override val language: AppLanguage = AppLanguage.EN

    override val sidebarWorkspace = "Workspace"
    override val sidebarOverview = "Overview"
    override val sidebarFindings = "Findings"
    override val sidebarPipeline = "Pipeline"
    override val sidebarPentest = "Pentest"
    override val sidebarSync = "Sync"
    override val sidebarSettings = "Settings"
    override val sidebarSelectProject = "Select a project…"
    override val sidebarNoRecent = "No recent projects"
    override val sidebarOpenFolder = "Open folder…"
    override val sidebarClearHistory = "Clear history"
    override val sidebarBackendOnline = "Backend online"
    override val sidebarBackendOffline = "Backend offline"
    override fun sidebarEnginesReady(ready: Int, total: Int) = "$ready/$total engines ready"
    override val sidebarLanguageSwitch = "Language"

    override val tabUntitledProject = "Untitled Project"
    override val tabNew = "New tab"

    override val settingsTitle = "Settings"
    override val settingsSubtitle = "Security assistant configuration and application preferences"
    override val settingsTestLlm = "Test LLM connection"
    override val settingsTesting = "Testing…"
    override val settingsZeroCodeNotice = "Zero code exposure policy: Prompts sent to your LLM provider contain only rule identifiers, file path locations, and severity levels. Raw source code is never transmitted."
    override val settingsAiTitle = "AI Assistant & Explanations"
    override val settingsAiSubtitle = "Enables automated vulnerability triage and contextual fix explanations"
    override val settingsAiActive = "Active"
    override val settingsAiDisabled = "Disabled"
    override val settingsAiEnableLabel = "Enable AI Assistant"
    override val settingsAiEnableDesc = "When disabled, built-in offline security rules are used instead."
    override val settingsProviderLabel = "Provider"
    override val settingsProviderDesc = "Select your LLM vendor or choose local Ollama for total privacy."
    override val settingsApiKeyLabel = "API Key"
    override fun settingsApiKeyDesc(provider: String) = "Access key for $provider (encrypted and stored locally)."
    override val settingsModelLabel = "Model"
    override val settingsModelDesc = "LLM model used for triage and explanation."
    override val settingsFetchModels = "Fetch"
    override val settingsFetchingModels = "Loading…"
    override val settingsMaxTokensLabel = "Token budget"
    override val settingsMaxTokensDesc = "Maximum token limit for AI responses to prevent cutoffs (recommended: 4096)."
    override val settingsPreferencesTitle = "Appearance & Language"
    override val settingsPreferencesSubtitle = "Customize display language and dark visual theme"
    override val settingsLanguageLabel = "Interface Language"
    override val settingsLanguageDesc = "Choose the global display language for CodeShield Desktop."
    override val settingsThemeLabel = "Color Theme"
    override val settingsThemeDesc = "Linear-style neutral dark palette with high-contrast syntax highlighting."
    override val settingsThemeDark = "Dark"
    override val settingsThemeLight = "Light"

    override val overviewTitle = "Overview"
    override val overviewSubtitle = "Overall security health and scanner engine configuration"
    override val overviewKpiScore = "SECURITY SCORE"
    override val overviewKpiCritical = "CRITICAL"
    override val overviewKpiHigh = "HIGH"
    override val overviewKpiTotal = "TOTAL FINDINGS"
    override val overviewConfigTitle = "Scanner configuration"
    override val overviewConfigSubtitle = "Select local directory and enable engines"
    override val overviewProjectDirLabel = "Project directory"
    override val overviewBrowse = "Browse…"
    override val overviewDirNotFound = "Directory not found — verify the path."
    override val overviewRunScan = "Run scan"
    override val overviewScanning = "Scanning…"
    override val overviewEnginesTitle = "Detection engines"
    override val overviewEnginesSubtitle = "SAST static analysis, secrets detection and SCA dependencies"
    override val overviewInstall = "Install"
    override val overviewReady = "Ready"
    override val overviewMissing = "Missing"

    override val pentestTitle = "Backend Pentest"
    override val pentestSubtitle = "Dynamic security testing (DAST, injections, auth bypass, rate limiting)"
    override val pentestPingHealth = "Ping health"
    override val pentestChecking = "Checking…"
    override val pentestRunPentest = "Run Pentest"
    override val pentestAuditing = "Auditing…"
    override val pentestLifecycleTitle = "Backend service lifecycle"
    override fun pentestActiveTarget(url: String) = "Active target: $url"
    override fun pentestTargetProject(dir: String) = if (dir.isBlank()) "Target project: None selected" else "Target project: $dir"
    override val pentestStartBackend = "Start backend"
    override val pentestStarting = "Starting…"
    override val pentestTargetOptions = "Target options"
    override val pentestDirectModeBadge = "Direct / External Mode"
    override val pentestLocalModeBadge = "Automatic Local Start"
    override fun pentestStatusOnline(port: Int) = "Online (port $port)"
    override val pentestStatusOffline = "Offline"
    override val pentestStatusStarting = "Starting…"
    override val pentestStatusDirect = "Direct (no local process)"
    override val pentestReconTitle = "Project reconnaissance & attack surface"
    override val pentestReconSubtitle = "Local static inspection · Framework & route mapping · Multi-stack detection"
    override val pentestNoTargetFolder = "No target folder selected"
    override val pentestBrowseFolder = "Browse folder…"
    override val pentestRescan = "Re-scan"
    override val pentestAnalyzing = "Analyzing…"
    override val pentestNoFolderTitle = "No project folder selected"
    override val pentestNoFolderHint = "Choose a source code directory to detect framework endpoints, package manager, and attack vectors."
    override val pentestSelectDirectory = "Select project directory"
    override val pentestScanningCodebase = "Scanning codebase structure (stack, package manager, route decorators, entry points)…"
    override val pentestNoReportTitle = "No audit report available"
    override val pentestNoReportYet = "No penetration test executed yet"
    override val pentestNoReportHint = "Probes your backend for security headers, SQL injection vectors, auth bypass flaws and burst request handling."
    override val pentestStartAudit = "Start robustness audit"
    override fun pentestDiscoveredEndpoints(filtered: Int, total: Int) = "Discovered Endpoints ($filtered/$total)"
    override val pentestFilterRoutesPlaceholder = "Filter routes (e.g. /api, auth, post)…"
    override fun pentestNoMatchingRoutes(query: String) = "No endpoints match the filter '$query'."
    override val pentestAuditResultsTitle = "Detailed audit results"
    override fun pentestVectorsTested(count: Int) = "$count security vectors tested"

    override val pentestTargetUrlLabel = "Target application URL"
    override val pentestCustomCommandLabel = "Custom launch command (optional)"

    override val findingsTitle = "Detected findings"
    override val findingsNoFindingsTitle = "No findings yet"
    override val findingsNoFindingsHint = "Select a project and run a scan to see vulnerabilities here."
    override val findingsNoFilterMatch = "No findings match the current filters."
    override val findingsTableSeverity = "Severity"
    override val findingsTableRule = "Rule"
    override val findingsTableFile = "File"
    override val findingsTableLine = "Line"
    override val findingsTableTool = "Tool"
    override val findingsSearchPlaceholder = "Search findings (title, rule, file)…"
    override val findingsTriageButton = "AI Project Triage"
    override val findingsTriaging = "Triaging…"
    override val findingsExplainButton = "Explain with AI"
    override val findingsExplaining = "Generating…"

    override val pipelineTitle = "Pipeline Console"
    override val pipelineSubtitle = "Interactive console and live logs from CLI engines"
    override val pipelineNoOutputTitle = "No output yet"
    override val pipelineNoOutputHint = "Run a scan from Overview or click Run scan above to stream logs."
    override val pipelineClearHistory = "Clear history"
    override val pipelineHistoryTitle = "Past scan history"

    override val syncTitle = "Cloud Synchronization"
    override val syncSubtitle = "Secure metadata transmission to collaborative CodeShield API"
    override val syncServerUrl = "Server API URL"
    override val syncProjectId = "Project ID (UUID)"
    override val syncApiToken = "API Bearer Token"
    override val syncButton = "Sync now"
    override val syncing = "Syncing…"
    override val syncNoSyncTitle = "No sync performed"
    override val syncNoSyncHint = "Submit a report to reconcile local findings with central database (differential auto-resolution)."
    override val syncLastSync = "Last synchronization"
}

/**
 * Retourne le dictionnaire de chaînes correspondant à la langue donnée.
 */
fun stringsFor(language: AppLanguage): AppStrings = when (language) {
    AppLanguage.FR -> StringsFr
    AppLanguage.EN -> StringsEn
}

/**
 * CompositionLocal donnant accès aux textes localisés dans toute l'arborescence UI.
 */
val LocalStrings: ProvidableCompositionLocal<AppStrings> = compositionLocalOf { StringsFr }
