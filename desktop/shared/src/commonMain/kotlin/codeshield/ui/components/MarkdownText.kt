package codeshield.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import codeshield.ui.theme.Shield

/**
 * Rendu Markdown minimaliste sans dépendance externe (100% commonMain).
 *
 * Gère le sous-ensemble réellement renvoyé par les LLM :
 * - titres `##`, `###`
 * - listes `-`, `*`, `•` et numérotées `1.`, `1)`
 * - inline `**gras**`, `*italique*`, `` `code` ``, `[label](url)`
 * - blocs de code ``` fencés
 *
 * Volontairement tolérant : les marqueurs non fermés sont affichés tels quels
 * au lieu de casser le rendu.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = Shield.TextDim,
    fontSize: TextUnit = 12.sp,
    lineHeight: TextUnit = 18.sp,
) {
    val blocks = remember(markdown) { parseBlocks(markdown) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Space -> Spacer(Modifier.height(2.dp))
                is MdBlock.Heading -> Text(
                    text = inlineToAnnotated(block.text, color = Shield.Text, fontSize = block.fontSize),
                    fontSize = block.fontSize,
                    lineHeight = block.fontSize * 1.3f,
                    fontWeight = FontWeight.Bold,
                    color = Shield.Text,
                )
                is MdBlock.Code -> Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Shield.Terminal)
                        .padding(8.dp),
                ) {
                    Text(
                        block.code.trim('\n'),
                        color = Shield.Accent,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                is MdBlock.Bullet -> Row(Modifier.fillMaxWidth()) {
                    Text("•  ", color = Shield.TextMute, fontSize = fontSize)
                    Text(
                        text = inlineToAnnotated(block.text, color, fontSize),
                        color = color, fontSize = fontSize, lineHeight = lineHeight,
                        modifier = Modifier.weight(1f),
                    )
                }
                is MdBlock.Ordered -> Row(Modifier.fillMaxWidth()) {
                    Text("${block.number}.  ", color = Shield.TextMute, fontSize = fontSize)
                    Text(
                        text = inlineToAnnotated(block.text, color, fontSize),
                        color = color, fontSize = fontSize, lineHeight = lineHeight,
                        modifier = Modifier.weight(1f),
                    )
                }
                is MdBlock.Paragraph -> Text(
                    text = inlineToAnnotated(block.text, color, fontSize),
                    color = color, fontSize = fontSize, lineHeight = lineHeight,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Modèle interne
// ---------------------------------------------------------------------------

private sealed interface MdBlock {
    data object Space : MdBlock
    data class Heading(val text: String, val fontSize: TextUnit) : MdBlock
    data class Code(val code: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Ordered(val number: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
}

private fun parseBlocks(markdown: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val lines = markdown.replace("\r\n", "\n").split("\n")
    var i = 0
    var inFence = false
    val fenceBuf = StringBuilder()
    while (i < lines.size) {
        val raw = lines[i]
        val trimmed = raw.trim()
        // Blocs de code fencés ```
        if (trimmed.startsWith("```")) {
            if (!inFence) {
                inFence = true
                fenceBuf.clear()
            } else {
                inFence = false
                out += MdBlock.Code(fenceBuf.toString())
            }
            i++
            continue
        }
        if (inFence) {
            fenceBuf.append(raw).append('\n')
            i++
            continue
        }
        if (trimmed.isBlank()) {
            out += MdBlock.Space
            i++
            continue
        }
        // Titres #..####
        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 4)
            val text = trimmed.drop(level).trimStart().trim()
            val size = when (level) {
                1 -> 16.sp
                2 -> 14.sp
                else -> 13.sp
            }
            out += MdBlock.Heading(text.ifBlank { trimmed }, size)
            i++
            continue
        }
        // Puces -, *, •
        val bulletMatch = Regex("""^\s*[-*•]\s+(.*)$""").find(raw)
        if (bulletMatch != null) {
            out += MdBlock.Bullet(bulletMatch.groupValues[1].trim())
            i++
            continue
        }
        // Numéroté "1. " ou "1) " (les LLM renvoient souvent "1)")
        val orderedMatch = Regex("""^\s*(\d+)[.)]\s+(.*)$""").find(raw)
        if (orderedMatch != null) {
            out += MdBlock.Ordered(
                orderedMatch.groupValues[1].toIntOrNull() ?: (out.size + 1),
                orderedMatch.groupValues[2].trim(),
            )
            i++
            continue
        }
        out += MdBlock.Paragraph(trimmed)
        i++
    }
    if (inFence && fenceBuf.isNotEmpty()) {
        // Fence non fermé : afficher quand même.
        out += MdBlock.Code(fenceBuf.toString())
    }
    // Compacte les espaces multiples.
    return out.fold(mutableListOf<MdBlock>()) { acc, b ->
        if (b is MdBlock.Space && acc.lastOrNull() is MdBlock.Space) acc else acc.apply { add(b) }
    }
}

private fun inlineToAnnotated(
    source: String,
    color: Color,
    fontSize: TextUnit,
): AnnotatedString = buildAnnotatedString {
    val base = SpanStyle(color = color, fontSize = fontSize)
    pushStyle(base)
    parseInlineInto(this, source)
    pop()
}

private fun parseInlineInto(builder: AnnotatedString.Builder, source: String) {
    var i = 0
    while (i < source.length) {
        when {
            source.startsWith("**", i) -> {
                val end = source.indexOf("**", i + 2)
                if (end == -1) {
                    builder.append(source.substring(i))
                    return
                }
                val inner = source.substring(i + 2, end)
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Shield.Text))
                parseInlineInto(builder, inner)
                builder.pop()
                i = end + 2
            }
            source.startsWith("`", i) -> {
                val end = source.indexOf("`", i + 1)
                if (end == -1) {
                    builder.append(source.substring(i))
                    return
                }
                val inner = source.substring(i + 1, end)
                builder.pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = Shield.Accent,
                        background = Shield.CardHi.copy(alpha = 0.9f),
                    ),
                )
                builder.append(inner)
                builder.pop()
                i = end + 1
            }
            source.startsWith("[", i) -> {
                // [label](url) -> label accentué + url atténuée
                val closeLabel = source.indexOf("]", i + 1)
                val openParen = if (closeLabel != -1) source.indexOf("(", closeLabel) else -1
                val closeParen = if (openParen != -1) source.indexOf(")", openParen) else -1
                if (closeLabel != -1 && openParen == closeLabel + 1 && closeParen != -1) {
                    val label = source.substring(i + 1, closeLabel)
                    val url = source.substring(openParen + 1, closeParen)
                    builder.pushStyle(SpanStyle(color = Shield.Accent, textDecoration = TextDecoration.Underline))
                    builder.append(label)
                    builder.pop()
                    if (url.isNotBlank()) {
                        builder.pushStyle(SpanStyle(color = Shield.TextMute))
                        builder.append(" ($url)")
                        builder.pop()
                    }
                    i = closeParen + 1
                } else {
                    builder.append("[")
                    i++
                }
            }
            source[i] == '*' -> {
                // *italique* (un seul astérisque)
                val end = source.indexOf("*", i + 1)
                if (end == -1) {
                    builder.append(source.substring(i))
                    return
                }
                // Éviter de confondre "**" déjà traité.
                if (source.startsWith("**", i)) {
                    builder.append("*")
                    i++
                } else {
                    val inner = source.substring(i + 1, end)
                    builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    parseInlineInto(builder, inner)
                    builder.pop()
                    i = end + 1
                }
            }
            else -> {
                builder.append(source[i].toString())
                i++
            }
        }
    }
}
