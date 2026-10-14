package codeshield.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import codeshield.model.Severity
import codeshield.ui.theme.Shield

/** Hauteurs normalisées 0..1 pour un bar chart (max -> 1, vide -> []). Pure, testée. */
fun barFractions(values: List<Int>): List<Float> {
    val max = values.maxOrNull() ?: return emptyList()
    if (max <= 0) return values.map { 0f }
    return values.map { it.toFloat() / max.toFloat() }
}

/**
 * Points d'une sparkline normalisés dans [0..width]×[0..height].
 * Une valeur unique -> point centré ; valeurs égales -> ligne médiane.
 */
fun sparkPoints(values: List<Float>, width: Float, height: Float): List<Offset> {
    if (values.isEmpty() || width <= 0 || height <= 0) return emptyList()
    if (values.size == 1) return listOf(Offset(width / 2f, height / 2f))
    val min = values.minOrNull() ?: 0f
    val max = values.maxOrNull() ?: 0f
    val span = max - min
    return values.mapIndexed { i, v ->
        val x = i.toFloat() / (values.size - 1).toFloat() * width
        val y = if (span > 0) height - (v - min) / span * height else height / 2f
        Offset(x, y)
    }
}

/**
 * Bar chart vertical : distribution des findings par sévérité.
 * Chaque colonne (barre, compteur et label) est rigoureusement centrée.
 */
@Composable
fun SeverityBars(counts: Map<Severity, Int>, modifier: Modifier = Modifier) {
    val ordered = Severity.entries.toList()
    val fractions = barFractions(ordered.map { counts[it] ?: 0 })
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ordered.forEachIndexed { i, sev ->
            val frac = fractions.getOrElse(i) { 0f }
            val count = counts[sev] ?: 0
            val color = Shield.severity(sev)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Jauge verticale centrée
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    // Fond de jauge
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Shield.Pill.copy(alpha = 0.5f)),
                    )
                    // Barre remplie
                    val barHeight = (130.dp * frac).coerceAtLeast(if (count > 0) 8.dp else 4.dp)
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(barHeight)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (count > 0) color.copy(alpha = 0.9f) else Shield.Pill),
                    )
                }

                // Compteur centré
                Text(
                    count.toString(),
                    color = if (count > 0) Shield.Text else Shield.TextMute,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                // Label centré
                Text(
                    sev.name.take(4),
                    color = Shield.TextMute,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Sparkline façon ref (courbe fine). */
@Composable
fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val pts = sparkPoints(values, size.width, size.height)
        if (pts.size < 2) {
            pts.firstOrNull()?.let { drawCircle(color, radius = 3f, center = it) }
            return@Canvas
        }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
    }
}
