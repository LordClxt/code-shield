package codeshield.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import codeshield.ui.theme.Shield

/** Curseur ↔ du système (fourni par la plateforme). */
expect val HorizontalResizePointerIcon: PointerIcon

/**
 * Séparateur vertical à glisser à la souris entre deux panneaux.
 * [onDrag] reçoit le déplacement horizontal (dp, positif vers la droite) ;
 * [onDragEnd] pour enregistrer la largeur ; double-clic -> [onReset] (largeur automatique).
 */
@Composable
fun ResizeHandle(
    onDrag: (Dp) -> Unit,
    onDragEnd: () -> Unit = {},
    onReset: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val dragged by interaction.collectIsDraggedAsState()
    Box(
        modifier
            .width(7.dp)
            .fillMaxHeight()
            .hoverable(interaction)
            .pointerHoverIcon(HorizontalResizePointerIcon)
            .draggable(
                state = rememberDraggableState { px -> onDrag(with(density) { px.toDp() }) },
                orientation = Orientation.Horizontal,
                interactionSource = interaction,
                onDragStopped = { onDragEnd() },
            )
            .pointerInput(onReset) { detectTapGestures(onDoubleTap = { onReset() }) },
        contentAlignment = Alignment.Center,
    ) {
        // Trait fin au repos, accent au survol / pendant le glissement.
        val active = hovered || dragged
        Box(
            Modifier.width(if (active) 2.dp else 1.dp).fillMaxHeight()
                .background(if (active) Shield.Accent else Shield.Border),
        )
    }
}
