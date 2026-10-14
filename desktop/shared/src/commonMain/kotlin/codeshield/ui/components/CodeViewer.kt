package codeshield.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import codeshield.model.Finding
import codeshield.model.Severity
import codeshield.ui.code.Languages
import codeshield.ui.code.SyntaxHighlighter
import codeshield.ui.code.Token
import codeshield.ui.code.TokenKind
import codeshield.ui.design.ButtonSize
import codeshield.ui.design.ButtonVariant
import codeshield.ui.design.Motion
import codeshield.ui.design.ShieldHScrollbar
import codeshield.ui.design.ShieldVScrollbar
import codeshield.ui.design.ShieldButton
import codeshield.ui.theme.Radius
import codeshield.ui.theme.Shield
import codeshield.ui.theme.Space
import codeshield.ui.theme.TypeScale
import kotlinx.coroutines.delay

/** Palette de coloration (sombre, contrastes doux pour laisser ressortir la faille). */
private object CodeColors {
    val Keyword = Color(0xFFC792EA)
    val String = Color(0xFFA5D6A7)
    val Comment = Color(0xFF6B6B73)
    val Number = Color(0xFFF2AE72)
    val Annotation = Color(0xFF82AAFF)
    val Type = Color(0xFF7FDBCA)
    val Plain = Color(0xFFD4D4DA)

    fun of(kind: TokenKind): SpanStyle = when (kind) {
        TokenKind.KEYWORD -> SpanStyle(color = Keyword, fontWeight = FontWeight.Medium)
        TokenKind.STRING -> SpanStyle(color = String)
        TokenKind.COMMENT -> SpanStyle(color = Comment, fontStyle = FontStyle.Italic)
        TokenKind.NUMBER -> SpanStyle(color = Number)
        TokenKind.ANNOTATION -> SpanStyle(color = Annotation)
        TokenKind.TYPE -> SpanStyle(color = Type)
        TokenKind.PLAIN -> SpanStyle(color = Plain)
    }
}

private const val TAB = "    "

/**
 * Ligne colorée. [mark] = plage de colonnes (1-based, inclusives/exclusives
 * selon l'outil) soulignée dans la couleur de sévérité.
 */
private fun highlightLine(text: String, tokens: List<Token>, mark: IntRange?, markColor: Color): AnnotatedString =
    buildAnnotatedString {
        append(text)
        addStyle(SpanStyle(color = CodeColors.Plain), 0, text.length)
        for (t in tokens) {
            val s = t.start.coerceIn(0, text.length)
            val e = t.end.coerceIn(s, text.length)
            if (e > s) addStyle(CodeColors.of(t.kind), s, e)
        }
        if (mark != null) {
            val s = mark.first.coerceIn(0, text.length)
            val e = (mark.last + 1).coerceIn(s, text.length)
            if (e > s) addStyle(SpanStyle(background = markColor.copy(alpha = 0.28f), textDecoration = TextDecoration.Underline), s, e)
        }
    }

/**
 * Visualiseur de code local (lecture seule ; le contenu ne quitte jamais la machine).
 *
 * - fichier entier virtualisé, défilement automatique vers la faille ;
 * - coloration syntaxique selon l'extension ;
 * - plage `line..endLine` surlignée, colonnes soulignées si connues ;
 * - gouttière : un marqueur par faille du fichier (couleur = sévérité max),
 *   info-bulle (règle, CWE) et clic pour sélectionner la faille.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CodeViewer(
    selected: Finding,
    lines: List<String>,
    error: String?,
    fileFindings: List<Finding>,
    onSelectFinding: (Finding) -> Unit,
    onOpenInIde: ((Finding) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1200); copied = false } }

    Column(modifier.fillMaxSize().background(Shield.Code)) {
        // Barre d'outils : position + actions
        Row(
            Modifier.fillMaxWidth().background(Shield.Surface).padding(horizontal = Space.md, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            val range = if (selected.lastLine != selected.lineNumber) "${selected.lineNumber}-${selected.lastLine}" else "${selected.lineNumber}"
            Text(
                "${selected.filePath}:$range",
                color = Shield.TextDim, fontSize = TypeScale.caption, fontFamily = Shield.Mono,
                maxLines = 1, overflow = TextOverflow.StartEllipsis, modifier = Modifier.weight(1f),
            )
            if (fileFindings.size > 1) {
                Text("${fileFindings.size} failles dans ce fichier", color = Shield.TextMute, fontSize = TypeScale.caption)
            }
            ShieldButton(
                if (copied) "Copié" else "path:line",
                onClick = {
                    @Suppress("DEPRECATION")
                    clipboard.setText(AnnotatedString("${selected.filePath}:${selected.lineNumber}"))
                    copied = true
                },
                variant = ButtonVariant.Ghost, size = ButtonSize.Sm, leadingIcon = Icons.Outlined.ContentCopy,
            )
            if (onOpenInIde != null) {
                ShieldButton("IDE", { onOpenInIde(selected) }, variant = ButtonVariant.Ghost, size = ButtonSize.Sm, leadingIcon = Icons.AutoMirrored.Outlined.OpenInNew)
            }
        }

        when {
            error != null -> Box(Modifier.fillMaxSize().padding(Space.xl), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = Shield.Warning, modifier = Modifier.size(16.dp))
                    Text(error, color = Shield.TextDim, fontSize = TypeScale.label)
                }
            }
            lines.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Lecture du fichier local…", color = Shield.TextMute, fontSize = TypeScale.label)
            }
            else -> CodeBody(selected, lines, fileFindings, onSelectFinding)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CodeBody(selected: Finding, lines: List<String>, fileFindings: List<Finding>, onSelectFinding: (Finding) -> Unit) {
    val lang = remember(selected.filePath) { Languages.forPath(selected.filePath) }
    // Tabs développés AVANT la tokenisation pour garder des offsets cohérents.
    val expanded = remember(lines) { lines.map { it.replace("\t", TAB) } }
    val tokens = remember(expanded, lang) { SyntaxHighlighter.tokenizeFile(expanded, lang) }
    val byLine = remember(fileFindings) {
        buildMap<Int, MutableList<Finding>> {
            for (f in fileFindings) for (l in f.lineNumber..f.lastLine) getOrPut(l) { mutableListOf() }.add(f)
        }
    }
    val first = selected.lineNumber.coerceIn(1, lines.size)
    val last = selected.lastLine.coerceIn(first, lines.size)
    val sevColor = Shield.severity(selected.severity)
    val gutterWidth = (lines.size.toString().length * 8 + 16).dp
    val listState = rememberLazyListState()
    // Première ouverture : on se place directement ; ensuite, on glisse jusqu'à la faille.
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        val target = (first - 1 - 8).coerceAtLeast(0)
        if (placed) listState.animateScrollToItem(target) else listState.scrollToItem(target)
        placed = true
    }
    // Éclat bref sur la plage sélectionnée, pour que l'œil la retrouve.
    val flash = remember(selected) { Animatable(1f) }
    LaunchedEffect(selected) { flash.animateTo(0f, tween(1100, 150, Motion.Emphasized)) }
    val hScroll = rememberScrollState()
    // Largeur de contenu = ligne la plus longue (mono ~7.3dp/caractère à 12sp) :
    // un seul défilement horizontal pour tout le fichier.
    val contentWidth = remember(expanded) { ((expanded.maxOfOrNull { it.length } ?: 0) * 7.3f).dp + gutterWidth + 64.dp }

    Box(Modifier.fillMaxSize()) {
    BoxWithConstraints(Modifier.fillMaxSize().horizontalScroll(hScroll)) {
        val width = if (contentWidth > maxWidth) contentWidth else maxWidth
        SelectionContainer {
        LazyColumn(state = listState, modifier = Modifier.width(width).fillMaxHeight().padding(vertical = Space.sm)) {
            items(lines.size, key = { it }) { idx ->
                val lineNo = idx + 1
                val inRange = lineNo in first..last
                val here = byLine[lineNo].orEmpty()
                val worst = here.minByOrNull { it.severity.ordinal }
                // Colonnes 1-based (semgrep/gitleaks) -> offsets 0-based sur la ligne développée.
                val mark = if (inRange && selected.column != null) {
                    val startCol = if (lineNo == first) selected.column - 1 else 0
                    val endCol = if (lineNo == last && selected.endColumn != null) selected.endColumn - 2 else expanded[idx].length - 1
                    if (endCol >= startCol) startCol..endCol else null
                } else null
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (inRange) sevColor.copy(alpha = 0.10f + 0.22f * flash.value) else Color.Transparent)
                        .height(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(3.dp).height(20.dp).background(if (inRange) sevColor else Color.Transparent))
                    // Marqueur de faille (toutes les failles du fichier)
                    Box(Modifier.width(14.dp), contentAlignment = Alignment.Center) {
                        if (worst != null) {
                            TooltipArea(
                                tooltip = { GutterTooltip(here) },
                                delayMillis = 250,
                                tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(8.dp, 12.dp)),
                            ) {
                                Box(
                                    Modifier.size(if (worst == selected || selected in here) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(Shield.severity(worst.severity))
                                        .clickable { onSelectFinding(here.firstOrNull { it != selected } ?: worst) },
                                )
                            }
                        }
                    }
                    Text(
                        lineNo.toString(),
                        color = if (inRange) sevColor else Shield.TextMute.copy(alpha = 0.55f),
                        fontSize = TypeScale.label, fontFamily = Shield.Mono,
                        fontWeight = if (inRange) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.width(gutterWidth).padding(end = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        maxLines = 1,
                    )
                    Text(
                        highlightLine(expanded[idx], tokens[idx], mark = mark, markColor = sevColor),
                        fontSize = TypeScale.label, fontFamily = Shield.Mono, softWrap = false, maxLines = 1,
                        modifier = Modifier.padding(end = Space.lg),
                    )
                }
            }
        }
        }
    }
    ShieldVScrollbar(listState)
    ShieldHScrollbar(hScroll)
    }
}

@Composable
private fun GutterTooltip(findings: List<Finding>) {
    Column(
        Modifier.widthIn(max = 360.dp).clip(Radius.md).background(Shield.Raised)
            .border(1.dp, Shield.BorderStrong, Radius.md).padding(Space.sm),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (f in findings.distinct().take(5)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Shield.severity(f.severity)))
                Text(f.severity.label(), color = Shield.severity(f.severity), fontSize = TypeScale.caption, fontWeight = FontWeight.SemiBold)
                Text(Shield.toolLabel(f.tool), color = Shield.TextMute, fontSize = TypeScale.caption)
            }
            Text(f.title, color = Shield.Text, fontSize = TypeScale.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = (listOf(f.ruleId) + f.cwe).joinToString(" · ")
            Text(meta, color = Shield.TextMute, fontSize = TypeScale.caption, fontFamily = Shield.Mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (findings.size > 5) Text("+${findings.size - 5} autres", color = Shield.TextMute, fontSize = TypeScale.caption)
    }
}

private fun Severity.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }
