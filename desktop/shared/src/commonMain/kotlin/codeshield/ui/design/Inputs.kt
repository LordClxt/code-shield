package codeshield.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import codeshield.ui.theme.Radius
import codeshield.ui.theme.Shield
import codeshield.ui.theme.TypeScale

/**
 * Champ texte compact (32 dp) : fond relevé, bordure fine, anneau accent au focus.
 * Remplace les OutlinedTextField / TextField Material.
 */
@Composable
fun ShieldTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    mono: Boolean = false,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    focusRequester: FocusRequester? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val border by animateColorAsState(
        when {
            focused -> Shield.Accent.copy(alpha = 0.8f)
            hovered -> Shield.BorderStrong
            else -> Shield.Border
        },
        label = "fieldBorder",
    )
    val style = TextStyle(
        color = if (enabled) Shield.Text else Shield.TextMute,
        fontSize = TypeScale.body,
        fontFamily = if (mono) Shield.Mono else null,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(Shield.Accent),
        visualTransformation = visualTransformation,
        interactionSource = interaction,
        modifier = modifier
            .hoverable(interaction)
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
        decorationBox = { inner ->
            Row(
                modifier = Modifier.fillMaxWidth().height(32.dp).clip(Radius.md)
                    .background(Shield.Surface)
                    .border(1.dp, border, Radius.md)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (leadingIcon != null) Icon(leadingIcon, null, tint = Shield.TextMute, modifier = Modifier.size(15.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, color = Shield.TextMute, fontSize = TypeScale.body, maxLines = 1)
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}

/** Interrupteur compact (32×18) façon Linear. */
@Composable
fun ShieldSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val track by animateColorAsState(if (checked) Shield.Accent else Shield.Pill, label = "swTrack")
    val offset by animateDpAsState(if (checked) 16.dp else 2.dp, label = "swThumb")
    Box(
        modifier = Modifier.width(34.dp).height(20.dp).clip(Radius.full)
            .background(if (enabled) track else track.copy(alpha = 0.4f))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = offset).size(16.dp).clip(CircleShape).background(Color.White))
    }
}

/** Case à cocher compacte (16 dp). */
@Composable
fun ShieldCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Box(
        modifier = Modifier.size(16.dp).clip(Radius.sm)
            .background(if (checked) Shield.Accent else Color.Transparent)
            .border(1.dp, if (checked) Shield.Accent else Shield.BorderStrong, Radius.sm)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
    }
}

/** Contrôle segmenté (Folders | Tools…) : le curseur glisse vers l'option choisie. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier.height(30.dp).clip(Radius.md).background(Shield.Surface)
            .border(1.dp, Shield.Border, Radius.md).padding(2.dp),
    ) {
        val segment = maxWidth / options.size
        val thumbX by animateDpAsState(segment * selectedIndex, Motion.base(), label = "segThumb")
        Box(
            Modifier.offset(x = thumbX).width(segment).height(26.dp).clip(Radius.sm)
                .background(Shield.Selected).border(1.dp, Shield.Border, Radius.sm),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                val active = i == selectedIndex
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                val color by animateColorAsState(if (active || hovered) Shield.Text else Shield.TextDim, Motion.fast(), label = "segText")
                Box(
                    modifier = Modifier.weight(1f).height(26.dp).clip(Radius.sm)
                        .hoverable(interaction)
                        .clickable(interactionSource = interaction, indication = null) { onSelect(i) }
                        .pointerHoverIcon(PointerIcon.Hand),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = color, fontSize = TypeScale.label, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
