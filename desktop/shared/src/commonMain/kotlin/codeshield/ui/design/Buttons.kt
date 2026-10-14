package codeshield.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import codeshield.ui.theme.Radius
import codeshield.ui.theme.Shield
import codeshield.ui.theme.TypeScale

enum class ButtonVariant { Primary, Secondary, Ghost, Danger }
enum class ButtonSize(val height: Dp, val padding: PaddingValues, val icon: Dp) {
    Sm(28.dp, PaddingValues(horizontal = 10.dp), 14.dp),
    Md(32.dp, PaddingValues(horizontal = 14.dp), 16.dp),
}

/**
 * Bouton du design system.
 * - Primary : aplat clair (texte sombre) — action principale de l'écran.
 * - Secondary : surface relevée + bordure fine.
 * - Ghost : transparent, se révèle au survol.
 * - Danger : destructif.
 */
@Composable
fun ShieldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Secondary,
    size: ButtonSize = ButtonSize.Md,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = enabled && !loading
    // Léger enfoncement au clic : le bouton répond sous le doigt.
    val pressScale by animateFloatAsState(if (pressed && active) 0.97f else 1f, Motion.fast(), label = "btnPress")
    val (bgRest, bgHover, fg, border) = when (variant) {
        ButtonVariant.Primary -> Quad(Color(0xFFEDEDEF), Color.White, Color(0xFF09090B), Color.Transparent)
        ButtonVariant.Secondary -> Quad(Shield.Raised, Shield.Hover, Shield.Text, Shield.BorderStrong)
        ButtonVariant.Ghost -> Quad(Color.Transparent, Shield.Hover, Shield.TextDim, Color.Transparent)
        ButtonVariant.Danger -> Quad(Shield.Error.copy(alpha = 0.12f), Shield.Error.copy(alpha = 0.2f), Shield.Error, Shield.Error.copy(alpha = 0.35f))
    }
    val bg by animateColorAsState(
        when {
            !enabled && variant == ButtonVariant.Primary -> Shield.Pill
            hovered && active -> bgHover
            else -> bgRest
        },
        label = "btnBg",
    )
    val fgColor = when {
        !enabled -> Shield.TextMute
        variant == ButtonVariant.Ghost && hovered -> Shield.Text
        else -> fg
    }
    Row(
        modifier = modifier
            .scale(pressScale)
            .height(size.height)
            .clip(Radius.md)
            .background(bg)
            .border(1.dp, if (enabled) border else Shield.Border, Radius.md)
            .hoverable(interaction)
            .clickable(enabled = active, interactionSource = interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(if (active) PointerIcon.Hand else PointerIcon.Default)
            .padding(size.padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(size.icon - 2.dp), color = fgColor, strokeWidth = 1.5.dp)
        } else if (leadingIcon != null) {
            Icon(leadingIcon, null, tint = fgColor, modifier = Modifier.size(size.icon))
        }
        // Texte vide : bouton icône seule (en-têtes compactés).
        if (text.isNotEmpty()) Text(text, color = fgColor, fontSize = if (size == ButtonSize.Sm) TypeScale.label else TypeScale.body, fontWeight = FontWeight.Medium, maxLines = 1)
        trailing?.invoke()
    }
}

/** Bouton icône carré (28 dp), survol discret. */
@Composable
fun ShieldIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Shield.TextDim,
    size: Dp = 28.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(if (hovered && enabled) Shield.Hover else Color.Transparent, Motion.fast(), label = "iconBtnBg")
    val pressScale by animateFloatAsState(if (pressed && enabled) 0.92f else 1f, Motion.fast(), label = "iconBtnPress")
    Box(
        modifier = modifier.scale(pressScale).size(size).clip(Radius.sm)
            .background(bg)
            .hoverable(interaction)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (!enabled) Shield.TextMute else if (hovered) Shield.Text else tint, modifier = Modifier.size(16.dp))
    }
}

private data class Quad(val a: Color, val b: Color, val c: Color, val d: Color)
