package org.example.desktop

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import javax.imageio.ImageIO
import androidx.compose.runtime.LaunchedEffect
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
import codeshield.ui.JvmInputCommandRunner
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

    application {
        val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
        Window(
            onCloseRequest = ::exitApplication,
            title = "CodeShield",
            icon = appIcon,
            state = windowState,
        ) {
            LaunchedEffect(window) {
                window.minimumSize = Dimension(1100, 700)
            }

            val llmClient = KtorLlmClient()
            val settingsStore = JvmSettingsStore()
            App(
                runner = JvmCommandRunner,
                fileReader = JvmFileReader,
                syncFactory = JvmSyncFactory,
                locator = JvmBinaryLocator,
                dirChecker = JvmDirChecker,
                installer = JvmToolInstaller(),
                onBrowse = ::pickJvmDirectory,
                historyStore = JvmFileHistoryStore(),
                settingsStore = settingsStore,
                llm = llmClient,
                backendRunner = JvmBackendRunner(),
                pentestEngine = KtorPentestEngine(),
                projectAnalyzer = LlmProjectAnalyzer(
                    llm = llmClient,
                    settingsStore = settingsStore,
                    staticAnalyzer = JvmProjectAnalyzer(),
                ),
                profiler = JvmProjectProfiler,
                workDirs = JvmScanWorkDirs,
                reportReader = JvmReportReader,
                inputRunner = JvmInputCommandRunner,
                titleBarInset = if (isMac) 28.dp else 0.dp,
            )
        }
    }
}
