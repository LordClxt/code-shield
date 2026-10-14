package codeshield.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import codeshield.ui.theme.Radius
import codeshield.ui.theme.Shield
import codeshield.ui.theme.Space
import codeshield.ui.theme.TypeScale
import kotlinx.coroutines.launch

@Composable
fun HDivider(modifier: Modifier = Modifier, color: Color = Shield.Border) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun VDivider(modifier: Modifier = Modifier, color: Color = Shield.Border) {
    Box(modifier.width(1.dp).fillMaxHeight().background(color))
}

/**
 * Panneau plat : surface relevée, bordure fine, rayon lg.
 * Header optionnel (titre + sous-titre + actions) séparé par un diviseur.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Space.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().clip(Radius.lg).background(Shield.Surface).border(1.dp, Shield.Border, Radius.lg),
    ) {
        if (title != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = Shield.Text, fontSize = TypeScale.body, fontWeight = FontWeight.SemiBold)
                    if (subtitle != null) Text(subtitle, color = Shield.TextMute, fontSize = TypeScale.label)
                }
                actions?.invoke(this)
            }
            HDivider()
        }
        Column(Modifier.fillMaxWidth().padding(contentPadding), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            content()
        }
    }
}

/** En-tête de page : titre, sous-titre, actions à droite. */
@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(52.dp).padding(horizontal = Space.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        leading?.invoke()
        Text(title, color = Shield.Text, fontSize = TypeScale.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (subtitle != null) {
            Text(subtitle, color = Shield.TextMute, fontSize = TypeScale.label, maxLines = 1, modifier = Modifier.padding(start = 4.dp))
        }
        Box(Modifier.weight(1f))
        actions?.invoke(this)
    }
}

/** Petit titre de groupe (MAJUSCULES discrètes). */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = Shield.TextMute, fontSize = TypeScale.caption, fontWeight = FontWeight.Medium, modifier = modifier)
}

/** État vide centré : icône, titre, indice, action. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Box(
            Modifier.size(40.dp).clip(Radius.lg).background(Shield.Raised).border(1.dp, Shield.Border, Radius.lg),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Shield.TextDim, modifier = Modifier.size(20.dp)) }
        Text(
            text = title,
            color = Shield.Text,
            fontSize = TypeScale.body,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (hint != null) {
            Text(
                text = hint,
                color = Shield.TextMute,
                fontSize = TypeScale.label,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Box(Modifier.padding(top = Space.sm), contentAlignment = Alignment.Center) {
                action()
            }
        }
    }
}

enum class CalloutKind { Info, Success, Warning, Error }

/** Bandeau d'information teinté. */
@Composable
fun Callout(text: String, kind: CalloutKind = CalloutKind.Info, modifier: Modifier = Modifier) {
    val (color, icon) = when (kind) {
        CalloutKind.Info -> Shield.TextDim to Icons.Outlined.Info
        CalloutKind.Success -> Shield.Success to Icons.Outlined.CheckCircle
        CalloutKind.Warning -> Shield.Warning to Icons.Outlined.WarningAmber
        CalloutKind.Error -> Shield.Error to Icons.Outlined.ErrorOutline
    }
    Row(
        modifier = modifier.fillMaxWidth().clip(Radius.md)
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.25f), Radius.md)
            .padding(horizontal = Space.md, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(15.dp).padding(top = 1.dp))
        Text(text, color = if (kind == CalloutKind.Info) Shield.TextDim else color, fontSize = TypeScale.label)
    }
}

/** Onglets soulignés (Code | Guide | AI) : le trait glisse d'un onglet à l'autre. */
@Composable
fun Tabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // Position et largeur de chaque onglet, mesurées après mise en page.
    val bounds = remember(options) { mutableStateListOf<Pair<Dp, Dp>>().apply { repeat(options.size) { add(0.dp to 0.dp) } } }
    val x = remember { Animatable(0.dp, Dp.VectorConverter) }
    val w = remember { Animatable(0.dp, Dp.VectorConverter) }
    val target = bounds.getOrElse(selectedIndex) { 0.dp to 0.dp }
    LaunchedEffect(target) {
        if (w.value == 0.dp) {
            // Première mesure : le trait apparaît en place, sans balayage.
            x.snapTo(target.first); w.snapTo(target.second)
        } else {
            launch { x.animateTo(target.first, Motion.base()) }
            w.animateTo(target.second, Motion.base())
        }
    }
    Box(modifier.height(36.dp)) {
        Row(Modifier.fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
            options.forEachIndexed { i, label ->
                val active = i == selectedIndex
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                val color by animateColorAsState(if (active || hovered) Shield.Text else Shield.TextMute, Motion.fast(), label = "tabText")
                Box(
                    modifier = Modifier.fillMaxHeight()
                        .onGloballyPositioned { c ->
                            with(density) { bounds[i] = c.positionInParent().x.toDp() to c.size.width.toDp() }
                        }
                        .hoverable(interaction)
                        .clickable(interactionSource = interaction, indication = null) { onSelect(i) }
                        .pointerHoverIcon(PointerIcon.Hand),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = color, fontSize = TypeScale.body, fontWeight = FontWeight.Medium)
                }
            }
        }
        Box(Modifier.align(Alignment.BottomStart).offset(x = x.value).width(w.value).height(2.dp).clip(Radius.full).background(Shield.Text))
    }
}

/** Ligne de formulaire « réglage » : libellé + description à gauche, contrôle à droite. */
@Composable
fun SettingRow(
    label: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xl),
    ) {
        Column(Modifier.weight(0.42f)) {
            Text(label, color = Shield.Text, fontSize = TypeScale.body, fontWeight = FontWeight.Medium)
            if (description != null) Text(description, color = Shield.TextMute, fontSize = TypeScale.label)
        }
        Box(Modifier.weight(0.58f), contentAlignment = Alignment.CenterEnd) { control() }
    }
}

/** Ligne de tableau dense (hover + sélection avec liseré accent). */
@Composable
fun TableRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        when {
            selected -> Shield.Selected
            hovered -> Shield.Hover.copy(alpha = 0.6f)
            else -> Color.Transparent
        },
        Motion.fast(),
        label = "rowBg",
    )
    // Le liseré accent pousse depuis la gauche quand la ligne est sélectionnée.
    val mark by androidx.compose.animation.core.animateFloatAsState(if (selected) 1f else 0f, Motion.base(), label = "rowMark")
    Row(
        modifier = modifier.fillMaxWidth().height(height)
            .background(bg)
            .drawBehind {
                if (mark > 0f) drawRect(Shield.Accent, size = size.copy(width = 2.dp.toPx() * mark))
                drawLine(Shield.Border, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1f)
            }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Space.lg),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** En-tête de tableau. */
@Composable
fun TableHeader(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().height(32.dp)
            .drawBehind {
                drawLine(Shield.Border, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1f)
            }
            .padding(horizontal = Space.lg),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun HeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Shield.TextMute, fontSize = TypeScale.label, fontWeight = FontWeight.Medium, modifier = modifier, maxLines = 1)
}
