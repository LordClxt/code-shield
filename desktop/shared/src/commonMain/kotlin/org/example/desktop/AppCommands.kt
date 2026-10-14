package org.example.desktop

import codeshield.agent.CodingAgentKind
import codeshield.llm.AppTheme
import codeshield.ui.ScannerUiState
import codeshield.ui.components.AppSection
import codeshield.ui.i18n.AppLanguage

/**
 * Tout ce que l'app sait faire, exposé à l'habillage natif (barre de menus, icône de la
 * barre de menus, Dock) fourni par desktopApp : mêmes actions que l'interface.
 */
class AppCommands(
    val state: ScannerUiState,
    val section: AppSection,
    val goTo: (AppSection) -> Unit,
    val openProject: () -> Unit,
    val newTab: () -> Unit,
    val closeTab: () -> Unit,
    val openRecent: (String) -> Unit,
    val recentProjects: List<String>,
    val canScan: Boolean,
    val runScan: () -> Unit,
    val cancelScan: () -> Unit,
    val sync: () -> Unit,
    /** Failles CRITICAL/HIGH qu'un agent peut corriger en lot. */
    val urgentCount: Int,
    val fixUrgent: () -> Unit,
    val cancelFix: () -> Unit,
    val setAgent: (CodingAgentKind) -> Unit,
    val setAutoApprove: (Boolean) -> Unit,
    val respondPermission: (requestId: String, allow: Boolean, approveAll: Boolean) -> Unit,
    val focusSearch: () -> Unit,
    val setTheme: (AppTheme) -> Unit,
    val setLanguage: (AppLanguage) -> Unit,
)
