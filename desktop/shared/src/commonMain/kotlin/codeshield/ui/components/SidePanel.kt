package codeshield.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import codeshield.model.Finding
import codeshield.ui.FindingGroup
import codeshield.ui.GroupTab
import codeshield.ui.design.CountBadge
import codeshield.ui.design.HDivider
import codeshield.ui.design.Motion
import codeshield.ui.design.ShieldVScrollbar
import codeshield.ui.design.SegmentedControl
import codeshield.ui.design.ShieldIconButton
import codeshield.ui.design.StatusDot
import codeshield.ui.theme.Radius
import codeshield.ui.theme.Shield
import codeshield.ui.theme.Space
import codeshield.ui.theme.TypeScale

/**
 * Panneau des groupes de findings : segmented Folders | Tools,
 * arborescence avec compteurs, fichiers dépliables.
 */
@Composable
fun SidePanel(
    tab: GroupTab,
    onTab: (GroupTab) -> Unit,
    groups: List<FindingGroup>,
    expanded: Set<String>,
    onToggleGroup: (String) -> Unit,
    selectedGroup: String?,
    onSelectGroup: (String?) -> Unit,
    selectedFinding: Finding?,
    onSelectFinding: (Finding) -> Unit,
    onCollapse: () -> Unit,
    totalCount: Int,
    /** Largeur (réglée à la souris par le séparateur de App). */
    width: androidx.compose.ui.unit.Dp = 260.dp,
) {
    Column(
        modifier = Modifier.width(width).fillMaxHeight().background(Shield.Surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(start = Space.lg, end = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Groups", color = Shield.Text, fontSize = TypeScale.body, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            ShieldIconButton(Icons.Outlined.KeyboardDoubleArrowLeft, "Collapse panel", onCollapse)
        }
        HDivider()
        SegmentedControl(
            options = listOf("Folders", "Tools"),
            selectedIndex = if (tab == GroupTab.FOLDERS) 0 else 1,
            onSelect = { onTab(if (it == 0) GroupTab.FOLDERS else GroupTab.TOOLS) },
            modifier = Modifier.fillMaxWidth().padding(Space.md),
        )
        val listState = rememberLazyListState()
        Box(Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = Space.sm)) {
            item {
                TreeRow(
                    title = "All findings",
                    icon = Icons.Outlined.Layers,
                    count = totalCount,
                    selected = selectedGroup == null,
                    onClick = { onSelectGroup(null) },
                )
            }
            items(groups, key = { it.key }) { group ->
                val isOpen = group.key in expanded
                Column {
                TreeRow(
                    title = group.title,
                    icon = if (tab == GroupTab.FOLDERS) Icons.Outlined.Folder else Icons.Outlined.Inventory2,
                    count = group.count,
                    selected = selectedGroup == group.key,
                    onClick = { onSelectGroup(group.key) },
                    expanded = isOpen,
                    onToggleExpand = { onToggleGroup(group.key) },
                )
                // Les fichiers se déplient sous le dossier au lieu d'apparaître d'un coup.
                AnimatedVisibility(isOpen, enter = expandVertically(Motion.base()) + fadeIn(Motion.base()), exit = shrinkVertically(Motion.fast()) + fadeOut(Motion.fast())) {
                  Column {
                    for (finding in group.findings.take(8)) {
                        TreeRow(
                            title = "${finding.filePath.substringAfterLast('/')}:${finding.lineNumber}",
                            icon = Icons.AutoMirrored.Outlined.InsertDriveFile,
                            count = null,
                            selected = selectedFinding == finding,
                            onClick = { onSelectFinding(finding) },
                            indent = 1,
                            dot = Shield.severity(finding.severity),
                        )
                    }
                    if (group.findings.size > 8) {
                        Text(
                            "+${group.findings.size - 8} more",
                            color = Shield.TextMute,
                            fontSize = TypeScale.caption,
                            modifier = Modifier.padding(start = 48.dp, top = 4.dp, bottom = 6.dp),
                        )
                    }
                  }
                }
                }
            }
        }
        ShieldVScrollbar(listState)
        }
    }
}

@Composable
private fun TreeRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    indent: Int = 0,
    expanded: Boolean? = null,
    onToggleExpand: (() -> Unit)? = null,
    dot: Color? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val rotation by animateFloatAsState(if (expanded == true) 90f else 0f, Motion.base(), label = "chevron")
    val bg by animateColorAsState(
        when {
            selected -> Shield.Selected
            hovered -> Shield.Hover
            else -> Color.Transparent
        },
        Motion.fast(),
        label = "treeBg",
    )
    Row(
        modifier = Modifier.fillMaxWidth().height(30.dp).clip(Radius.md)
            .background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(start = (4 + indent * 22).dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            if (expanded != null && onToggleExpand != null) {
                Icon(
                    Icons.Outlined.ChevronRight, if (expanded) "Collapse" else "Expand",
                    tint = Shield.TextMute,
                    modifier = Modifier.size(14.dp).rotate(rotation).clip(Radius.sm).clickable(onClick = onToggleExpand),
                )
            } else if (dot != null) {
                StatusDot(dot)
            }
        }
        Icon(icon, null, tint = if (selected) Shield.Text else Shield.TextMute, modifier = Modifier.size(15.dp))
        Text(
            title,
            color = if (selected || hovered) Shield.Text else Shield.TextDim,
            fontSize = TypeScale.body,
            fontFamily = if (indent > 0) Shield.Mono else null,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (count != null) CountBadge(count, active = selected)
    }
}
