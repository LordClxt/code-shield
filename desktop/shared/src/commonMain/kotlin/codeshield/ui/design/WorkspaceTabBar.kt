package codeshield.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import codeshield.ui.WorkspaceTabState
import codeshield.ui.theme.Radius
import codeshield.ui.theme.Shield
import codeshield.ui.theme.TypeScale

/**
 * Barre d'onglets de travail multi-projets (WorkspaceTabBar).
 * Style Linear / Vercel sombre : onglets compacts, défilement horizontal,
 * indicateur d'onglet actif avec liseré supérieur indigo, badge de vulnérabilités,
 * bouton de fermeture et bouton d'ajout de projet (+ New Tab).
 */
@Composable
fun WorkspaceTabBar(
    tabs: List<WorkspaceTabState>,
    activeTabId: String,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier,
    leadingInset: Dp = 0.dp,
    height: Dp = 38.dp,
) {
    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxWidth().background(Shield.Sidebar)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .padding(top = 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Espace réservé pour les feux tricolores macOS si intégré dans la titlebar
            if (leadingInset > 0.dp) {
                Spacer(Modifier.width(leadingInset))
            }

            // Liste des onglets défilable horizontalement
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .horizontalScroll(scrollState),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                tabs.forEach { tab ->
                    WorkspaceTabItem(
                        tab = tab,
                        isActive = tab.id == activeTabId,
                        canClose = tabs.size > 1 || tab.projectDir.isNotBlank(),
                        onSelect = { onSelectTab(tab.id) },
                        onClose = { onCloseTab(tab.id) },
                    )
                }
            }

            // Bouton "+ New Tab"
            NewTabButton(onClick = onNewTab)

            Spacer(Modifier.weight(1f))
        }

        // Ligne de séparation inférieure fine de 1px
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Shield.Border),
        )
    }
}

@Composable
private fun WorkspaceTabItem(
    tab: WorkspaceTabState,
    isActive: Boolean,
    canClose: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()

    val tabShape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)

    val bgColor by animateColorAsState(
        when {
            isActive -> Shield.Surface
            isHovered -> Shield.Hover
            else -> Color.Transparent
        },
    )

    val borderColor = if (isActive) Shield.BorderStrong else Color.Transparent

    Column(
        modifier = Modifier
            .widthIn(min = 120.dp, max = 220.dp)
            .height(34.dp)
            .clip(tabShape)
            .background(bgColor)
            .border(1.dp, borderColor, tabShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onSelect,
            )
            .pointerHoverIcon(PointerIcon.Hand),
    ) {
        // Liseré supérieur indigo de l'onglet actif
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (isActive) Shield.Accent else Color.Transparent),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Icône projet / bouclier
            Icon(
                imageVector = if (tab.projectDir.isBlank()) Icons.Outlined.Folder else Icons.Outlined.Security,
                contentDescription = null,
                tint = if (isActive) Shield.Accent else Shield.TextMute,
                modifier = Modifier.size(13.dp),
            )

            // Titre du projet
            val strings = codeshield.ui.i18n.LocalStrings.current
            Text(
                text = tab.displayTitle(strings.tabUntitledProject),
                fontSize = TypeScale.caption,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isActive) Shield.Text else Shield.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // Badge d'état (spinner ou nombre de findings)
            if (tab.isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(11.dp),
                    strokeWidth = 1.5.dp,
                    color = Shield.Accent,
                )
            } else if (tab.findings.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Shield.Error.copy(alpha = 0.15f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${tab.findings.size}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Shield.Error,
                    )
                }
            } else if (tab.projectDir.isNotBlank()) {
                // Projet sain sans vulnérabilités
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Shield.Success),
                )
            }

            // Bouton de fermeture (visible si hover ou actif et refermable)
            if (canClose) {
                val closeHoverInteraction = remember { MutableInteractionSource() }
                val isCloseHovered by closeHoverInteraction.collectIsHoveredAsState()

                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isCloseHovered) Shield.Raised else Color.Transparent)
                        .clickable(
                            interactionSource = closeHoverInteraction,
                            indication = null,
                            onClick = onClose,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close tab",
                        tint = if (isCloseHovered) Shield.Text else Shield.TextMute,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NewTabButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()

    val strings = codeshield.ui.i18n.LocalStrings.current
    Box(
        modifier = Modifier
            .padding(start = 4.dp, bottom = 4.dp)
            .size(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isHovered) Shield.Hover else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = "${strings.tabNew} (⌘T)",
            tint = if (isHovered) Shield.Text else Shield.TextMute,
            modifier = Modifier.size(14.dp),
        )
    }
}
