package org.example.desktop

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import javax.imageio.ImageIO
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import codeshield.backend.JvmBackendRunner
import codeshield.cache.JvmFileHistoryStore
import codeshield.llm.JvmSettingsStore
import codeshield.llm.KtorLlmClient
import codeshield.pentest.JvmProjectAnalyzer
import codeshield.pentest.KtorPentestEngine
import codeshield.pentest.LlmProjectAnalyzer
import codeshield.tools.JvmToolInstaller
import codeshield.ui.JvmBinaryLocator
import codeshield.ui.JvmCommandRunner
import codeshield.ui.JvmInteractiveCommandRunner
import codeshield.ui.JvmShell
import codeshield.ui.JvmDirChecker
import codeshield.ui.JvmFileReader
import codeshield.ui.JvmProjectProfiler
import codeshield.ui.JvmReportReader
import codeshield.ui.JvmScanWorkDirs
import codeshield.ui.JvmSyncFactory
import codeshield.ui.pickJvmDirectory
import java.awt.Dimension

fun main() {
    val isMac = System.getProperty("os.name").contains("Mac", ignoreCase = true)
    if (isMac) {
        // Nom dans la barre de menus macOS (sinon « MainKt » hors installeur).
        System.setProperty("apple.awt.application.name", "CodeShield")
        System.setProperty("apple.awt.application.appearance", "system")
        System.setProperty("apple.awt.transparentTitleBar", "true")
        System.setProperty("apple.awt.fullWindowContent", "true")
    }

    // Icône de l'app (le d-bouclier), embarquée dans les ressources.
    val iconImage = Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")?.use { ImageIO.read(it) }
    val appIcon = iconImage?.let { BitmapPainter(it.toComposeImageBitmap()) }
    // Dock macOS / barre des tâches : `Window(icon)` ne les change pas quand l'app tourne hors
    // installeur (`./gradlew run`, IDE) ; l'app installée prend déjà l'icône du bundle (.icns).
    if (iconImage != null && java.awt.Taskbar.isTaskbarSupported()) {
        val taskbar = java.awt.Taskbar.getTaskbar()
        if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) runCatching { taskbar.iconImage = iconImage }
    }

    // Dépendances créées une seule fois : hors composition, elles ne sont jamais recréées
    // (un HttpClient Ktor par recomposition épuisait les descripteurs : « Too many open files »).
    val llmClient = KtorLlmClient()
    val settingsStore = JvmSettingsStore()
    val installer = JvmToolInstaller()
    val historyStore = JvmFileHistoryStore()
    val backendRunner = JvmBackendRunner()
    val pentestEngine = KtorPentestEngine()
    val projectAnalyzer = LlmProjectAnalyzer(llm = llmClient, settingsStore = settingsStore, staticAnalyzer = JvmProjectAnalyzer())

    application {
        val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
        // Commandes de l'app, partagées entre la fenêtre et l'icône de la barre de menus.
        val commands = remember { mutableStateOf<AppCommands?>(null) }
        val trayState = rememberTrayState()
        var visible by remember { mutableStateOf(true) }

        if (appIcon != null) {
            CodeShieldTray(appIcon, trayState, commands, onShow = { visible = true })
        }

        Window(
            // Un scan ou un agent en cours continue en arrière-plan : la fenêtre se masque.
            onCloseRequest = {
                val s = commands.value?.state
                if (s != null && (s.isScanning || s.claudeFix?.running == true)) {
                    // Lu ici (pas pendant la composition) : l'état de l'app ne recompose pas toute l'application.
                    val fr = s.llmSettings.language != codeshield.ui.i18n.AppLanguage.EN
                    visible = false
                    trayState.notify(
                        "CodeShield",
                        if (fr) "Le travail continue en arrière-plan : l'icône de la barre de menus permet de revenir."
                        else "Work continues in the background: use the menu bar icon to come back.",
                        error = false,
                    )
                } else {
                    exitApplication()
                }
            },
            visible = visible,
            title = "CodeShield",
            icon = appIcon,
            state = windowState,
        ) {
            LaunchedEffect(window) {
                window.minimumSize = Dimension(1100, 700)
            }
            LaunchedEffect(visible) { if (visible) window.toFront() }

            App(
                runner = JvmCommandRunner,
                fileReader = JvmFileReader,
                syncFactory = JvmSyncFactory,
                locator = JvmBinaryLocator,
                dirChecker = JvmDirChecker,
                installer = installer,
                onBrowse = ::pickJvmDirectory,
                historyStore = historyStore,
                settingsStore = settingsStore,
                llm = llmClient,
                backendRunner = backendRunner,
                pentestEngine = pentestEngine,
                projectAnalyzer = projectAnalyzer,
                profiler = JvmProjectProfiler,
                workDirs = JvmScanWorkDirs,
                reportReader = JvmReportReader,
                interactiveRunner = JvmInteractiveCommandRunner,
                shell = JvmShell,
                titleBarInset = if (isMac) 28.dp else 0.dp,
                nativeChrome = { c ->
                    SideEffect { commands.value = c }
                    CodeShieldMenuBar(c)
                    DockIntegration(c, window) { title, message, error -> trayState.notify(title, message, error) }
                },
            )
        }
    }
}
