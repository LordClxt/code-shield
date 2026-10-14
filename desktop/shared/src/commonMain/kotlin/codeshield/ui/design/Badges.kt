package codeshield.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import codeshield.model.Severity
import codeshield.model.ToolName
import codeshield.ui.theme.Radius
import codeshield.ui.theme.Shield
import codeshield.ui.theme.TypeScale

/** Pastille ronde de statut. */
@Composable
fun StatusDot(color: Color, size: Dp = 6.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/** Badge teinté générique (fond alpha 0.12, bordure alpha 0.3). */
@Composable
fun TintBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.height(20.dp).clip(Radius.sm)
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), Radius.sm)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontSize = TypeScale.caption, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

fun severityLabel(s: Severity): String = s.name.lowercase().replaceFirstChar { it.uppercase() }

@Composable
fun SeverityBadge(severity: Severity, modifier: Modifier = Modifier) =
    TintBadge(severityLabel(severity), Shield.severity(severity), modifier)

/** Badge outil : pastille colorée + nom. */
@Composable
fun ToolBadge(tool: ToolName, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    val (_, color) = Shield.toolBadge(tool)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(Radius.sm).background(color))
        if (showLabel) Text(Shield.toolLabel(tool), color = Shield.TextDim, fontSize = TypeScale.label, maxLines = 1)
    }
}

/** Compteur neutre (sidebar, onglets). */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier, active: Boolean = false) {
    Box(
        modifier = modifier.height(18.dp).clip(Radius.sm)
            .background(if (active) Shield.BorderStrong else Shield.Border)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(count.toString(), color = if (active) Shield.Text else Shield.TextDim, fontSize = TypeScale.caption, fontWeight = FontWeight.Medium)
    }
}

/** Touche clavier (⌘K…). */
@Composable
fun Kbd(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.height(18.dp).clip(Radius.sm)
            .border(1.dp, Shield.BorderStrong, Radius.sm)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Shield.TextMute, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

/** Chip de filtre : point coloré optionnel + libellé + compteur, état actif. */
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    count: Int? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = modifier.height(26.dp).clip(Radius.md)
            .background(
                when {
                    selected -> Shield.Selected
                    hovered -> Shield.Hover
                    else -> Color.Transparent
                },
            )
            .border(1.dp, if (selected) Shield.BorderStrong else Shield.Border, Radius.md)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) StatusDot(dot, 7.dp)
        Text(label, color = if (selected) Shield.Text else Shield.TextDim, fontSize = TypeScale.label, fontWeight = FontWeight.Medium)
        if (count != null) Text(count.toString(), color = Shield.TextMute, fontSize = TypeScale.label)
    }
}
