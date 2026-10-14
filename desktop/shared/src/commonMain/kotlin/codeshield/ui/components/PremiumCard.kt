package codeshield.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import codeshield.ui.design.Panel
import codeshield.ui.design.StatusDot
import codeshield.ui.design.TintBadge
import codeshield.ui.theme.Shield
import codeshield.ui.theme.TypeScale

/** Carte héritée : délègue au [Panel] du design system. */
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Panel(
        modifier = modifier,
        title = title,
        actions = trailing?.let { t -> { t() } },
        content = content,
    )
}

/** Pastille de statut : point coloré + libellé. */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusDot(color)
        Text(text, color = Shield.TextDim, fontSize = TypeScale.label, fontWeight = FontWeight.Medium)
    }
}

/** Indicateur neutre pour les métriques (ex. stack, source d'analyse). */
@Composable
fun MonoPill(text: String, modifier: Modifier = Modifier) {
    TintBadge(text, Shield.TextDim, modifier)
}
