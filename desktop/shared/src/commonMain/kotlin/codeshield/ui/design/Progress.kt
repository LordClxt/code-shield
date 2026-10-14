package codeshield.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import codeshield.ui.theme.Shield

/**
 * Jauge fine du design system (remplace LinearProgressIndicator de Material 3,
 * dont le point « stop » au bout de la piste et l'espace avant celle-ci se lisent
 * comme un état en fin de jauge).
 *
 * - [progress] dans 0..1 : remplissage déterminé, animé entre deux valeurs ;
 * - [progress] null : indéterminé, un segment balaie la piste (travail en cours
 *   dont la durée est inconnue : vérification, extraction, scan).
 */
@Composable
fun ShieldProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    color: Color = Shield.Accent,
    trackColor: Color = Shield.Pill,
    height: Dp = 4.dp,
) {
    if (progress != null) {
        // Le remplissage glisse d'une valeur à l'autre au lieu de sauter par paliers.
        val shown by animateFloatAsState(progress.coerceIn(0f, 1f), Motion.base(), label = "progress")
        Canvas(modifier.fillMaxWidth().height(height)) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(trackColor, cornerRadius = radius)
            if (shown > 0f) drawRoundRect(color, size = Size(size.width * shown, size.height), cornerRadius = radius)
        }
    } else {
        val t = rememberInfiniteTransition(label = "indeterminate")
        val x by t.animateFloat(-0.35f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "sweep")
        Canvas(modifier.fillMaxWidth().height(height)) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(trackColor, cornerRadius = radius)
            val start = (x * size.width).coerceAtLeast(0f)
            val end = ((x + 0.35f) * size.width).coerceAtMost(size.width)
            if (end > start) drawRoundRect(color, Offset(start, 0f), Size(end - start, size.height), radius)
        }
    }
}
