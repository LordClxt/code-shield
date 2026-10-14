package codeshield.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import codeshield.ui.ConsultedProject
import codeshield.ui.design.CountBadge
import codeshield.ui.design.Eyebrow
import codeshield.ui.design.HDivider
import codeshield.ui.design.Kbd
import codeshield.ui.design.Motion
import codeshield.ui.design.StatusDot
import codeshield.ui.theme.Radius
import codeshield.ui.theme.Shield
import codeshield.ui.theme.Space
import codeshield.ui.theme.TypeScale

/** Sections de l'application (navigation latérale). */
enum class AppSection(val label: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Outlined.GridView),
    FINDINGS("Findings", Icons.AutoMirrored.Outlined.ListAlt),
    PIPELINE("Pipeline", Icons.Outlined.Terminal),
    PENTEST("Pentest", Icons.Outlined.Security),
    HEALTH("Sync", Icons.Outlined.CloudSync),
    SETTINGS("Settings", Icons.Outlined.Settings);

    fun localizedLabel(strings: codeshield.ui.i18n.AppStrings): String = when (this) {
        OVERVIEW -> strings.sidebarOverview
        FINDINGS -> strings.sidebarFindings
        PIPELINE -> strings.sidebarPipeline
        PENTEST -> strings.sidebarPentest
        HEALTH -> strings.sidebarSync
        SETTINGS -> strings.sidebarSettings
    }
}

/** Ancien nom conservé : la navigation est désormais une [Sidebar]. */
@Composable
fun Rail(active: AppSection, onSelect: (AppSection) -> Unit) =
    Sidebar(active = active, onSelect = onSelect)

/**
 * Sidebar façon Linear : wordmark, sélecteur déroulant de projets consultés,
 * navigation libellée avec compteurs et raccourcis, bloc de statut en pied.
 */
@Composable
fun Sidebar(
    active: AppSection,
    onSelect: (AppSection) -> Unit,
    projectDir: String = "",
    consultedProjects: List<ConsultedProject> = emptyList(),
    onSelectProject: (String) -> Unit = {},
    onPickProject: () -> Unit = {},
    onClearRecentProjects: () -> Unit = {},
    findingsCount: Int = 0,
    isScanning: Boolean = false,
    backendOnline: Boolean = false,
    enginesReady: Int = 0,
    enginesTotal: Int = 3,
    topInset: androidx.compose.ui.unit.Dp = 0.dp,
    onToggleLanguage: (() -> Unit)? = null,
) {
    val strings = codeshield.ui.i18n.LocalStrings.current

    Column(
        modifier = Modifier
            .width(224.dp)
            .fillMaxHeight()
            .background(Shield.Sidebar)
            .padding(top = topInset)
            .padding(horizontal = Space.md, vertical = Space.md),
    ) {
        // Wordmark
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            codeshield.ui.design.BrandWordmark(height = 17.dp)
        }

        // Sélecteur de projets consultés (Dropdown)
        ProjectSwitcher(
            projectDir = projectDir,
            consultedProjects = consultedProjects,
            onSelectProject = onSelectProject,
            onPickProject = onPickProject,
            onClearRecentProjects = onClearRecentProjects,
            modifier = Modifier.padding(top = Space.md),
        )

        Spacer(Modifier.height(Space.lg))
        Eyebrow(strings.sidebarWorkspace, Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        // Un seul fond de sélection, qui glisse d'un élément à l'autre (hauteur fixe des items).
        val indicatorY by animateDpAsState(NavItemHeight * AppSection.entries.indexOf(active), Motion.base(), label = "navIndicator")
        Box {
            Box(
                Modifier.offset(y = indicatorY).fillMaxWidth().height(NavItemHeight).clip(Radius.md).background(Shield.Selected),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(Modifier.width(2.dp).height(14.dp).clip(Radius.full).background(Shield.Accent))
            }
            Column {
                AppSection.entries.forEachIndexed { index, section ->
                    NavItem(
                        section = section,
                        selected = section == active,
                        count = if (section == AppSection.FINDINGS && findingsCount > 0) findingsCount else null,
                        busy = section == AppSection.PIPELINE && isScanning,
                        shortcut = "⌘${index + 1}",
                        onClick = { onSelect(section) },
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Statut
        Column(
            modifier = Modifier.fillMaxWidth().clip(Radius.md)
                .border(1.dp, Shield.Border, Radius.md)
                .padding(horizontal = Space.md, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusLine(
                color = if (backendOnline) Shield.Success else Shield.TextMute,
                text = if (backendOnline) strings.sidebarBackendOnline else strings.sidebarBackendOffline,
            )
            StatusLine(
                color = when (enginesReady) {
                    enginesTotal -> Shield.Success
                    0 -> Shield.Error
                    else -> Shield.Warning
                },
                text = strings.sidebarEnginesReady(enginesReady, enginesTotal),
            )
        }

        if (onToggleLanguage != null) {
            Spacer(Modifier.height(Space.sm))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Radius.md)
                    .background(Shield.Raised)
                    .border(1.dp, Shield.Border, Radius.md)
                    .clickable { onToggleLanguage() }
                    .padding(horizontal = Space.md, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(strings.language.flag, fontSize = TypeScale.body)
                    Text(
                        strings.language.nativeLabel,
                        color = Shield.Text,
                        fontSize = TypeScale.label,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Text(
                    if (strings.language == codeshield.ui.i18n.AppLanguage.FR) "Passer en EN" else "Passer en FR",
                    color = Shield.Accent,
                    fontSize = TypeScale.caption,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun StatusLine(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusDot(color)
        Text(text, color = Shield.TextDim, fontSize = TypeScale.label)
    }
}

/**
 * Sélecteur de projet façon Workspace Switcher :
 * - Affiche le projet actuellement actif
 * - Ouvre au clic un menu déroulant listant tous les projets récemment consultés
 * - Permet de basculer instantanément de projet ou d'en ouvrir un nouveau
 */
@Composable
private fun ProjectSwitcher(
    projectDir: String,
    consultedProjects: List<ConsultedProject>,
    onSelectProject: (String) -> Unit,
    onPickProject: () -> Unit,
    onClearRecentProjects: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val name = projectDir.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')

    val strings = codeshield.ui.i18n.LocalStrings.current
    Box(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().height(36.dp).clip(Radius.md)
                .background(if (menuOpen || hovered) Shield.Hover else Shield.Raised)
                .border(1.dp, if (menuOpen) Shield.BorderStrong else Shield.Border, Radius.md)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null) { menuOpen = true }
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(20.dp).clip(Radius.sm).background(Shield.Accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                if (name.isNotBlank()) {
                    Text(name.take(1).uppercase(), color = Shield.Accent, fontSize = TypeScale.caption, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Outlined.FolderOpen, null, tint = Shield.Accent, modifier = Modifier.size(12.dp))
                }
            }
            Text(
                name.ifBlank { strings.sidebarSelectProject },
                color = if (name.isBlank()) Shield.TextMute else Shield.Text,
                fontSize = TypeScale.body,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Outlined.UnfoldMore, null, tint = Shield.TextMute, modifier = Modifier.size(14.dp))
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier
                .width(280.dp)
                .background(Shield.Raised)
                .border(1.dp, Shield.BorderStrong, Radius.md),
        ) {
            // En-tête de section
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Eyebrow("Consulted Projects")
                if (consultedProjects.isNotEmpty()) {
                    Text(
                        strings.sidebarClearHistory,
                        color = Shield.TextMute,
                        fontSize = TypeScale.caption,
                        modifier = Modifier
                            .clip(Radius.sm)
                            .clickable {
                                onClearRecentProjects()
                                menuOpen = false
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }

            if (consultedProjects.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            strings.sidebarNoRecent,
                            color = Shield.TextMute,
                            fontSize = TypeScale.label,
                        )
                    },
                    onClick = {},
                    enabled = false,
                )
            } else {
                for (p in consultedProjects) {
                    val isSelected = p.path.trimEnd('/', '\\') == projectDir.trimEnd('/', '\\')
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Folder,
                                    null,
                                    tint = if (isSelected) Shield.Accent else Shield.TextMute,
                                    modifier = Modifier.size(16.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        p.name,
                                        color = if (isSelected) Shield.Text else Shield.TextDim,
                                        fontSize = TypeScale.body,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        p.path,
                                        color = Shield.TextMute,
                                        fontSize = TypeScale.caption,
                                        fontFamily = Shield.Mono,
                                        maxLines = 1,
                                        overflow = TextOverflow.StartEllipsis,
                                    )
                                }
                                if (p.findingsCount != null) {
                                    CountBadge(p.findingsCount, active = isSelected)
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        "Active",
                                        tint = Shield.Accent,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSelectProject(p.path)
                            menuOpen = false
                        },
                    )
                }
            }

            HDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Action pour ouvrir un autre dossier
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Outlined.FolderOpen,
                            null,
                            tint = Shield.Text,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            strings.sidebarOpenFolder,
                            color = Shield.Text,
                            fontSize = TypeScale.body,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                },
                onClick = {
                    menuOpen = false
                    onPickProject()
                },
            )
        }
    }
}

@Composable
private fun NavItem(
    section: AppSection,
    selected: Boolean,
    count: Int?,
    busy: Boolean,
    shortcut: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    // La sélection est dessinée par l'indicateur glissant de la Sidebar ; ici, seulement le survol.
    val bg by animateColorAsState(if (hovered && !selected) Shield.Hover.copy(alpha = 0.7f) else Color.Transparent, Motion.fast(), label = "navBg")
    val fg by animateColorAsState(if (selected || hovered) Shield.Text else Shield.TextDim, Motion.fast(), label = "navFg")
    val iconTint by animateColorAsState(if (selected) Shield.Text else Shield.TextMute, Motion.fast(), label = "navIcon")
    Row(
        modifier = Modifier.fillMaxWidth().height(NavItemHeight).clip(Radius.md)
            .background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            section.icon, section.label,
            tint = iconTint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            section.localizedLabel(codeshield.ui.i18n.LocalStrings.current),
            color = fg,
            fontSize = TypeScale.body,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        when {
            busy -> PulsingDot(Shield.Accent)
            count != null -> CountBadge(count, active = selected)
            hovered -> Kbd(shortcut)
        }
    }
}

private val NavItemHeight = 32.dp

/** Pastille qui respire : une activité en cours (scan). */
@Composable
private fun PulsingDot(color: Color) {
    val t = rememberInfiniteTransition(label = "pulse")
    val halo by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "halo")
    Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(7.dp).graphicsLayer { scaleX = 1f + halo * 1.2f; scaleY = 1f + halo * 1.2f; alpha = 1f - halo }
                .clip(CircleShape).background(color.copy(alpha = 0.5f)),
        )
        StatusDot(color, 7.dp)
    }
}
