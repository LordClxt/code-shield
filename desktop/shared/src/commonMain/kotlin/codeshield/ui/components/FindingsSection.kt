package codeshield.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import codeshield.agent.ClaudeCodeFix
import codeshield.logic.explainFinding
import codeshield.logic.severityBlurb
import codeshield.model.Finding
import codeshield.model.Severity
import codeshield.model.ToolName
import codeshield.ui.ClaudeFixState
import codeshield.ui.FindingGroup
import codeshield.ui.design.ButtonSize
import codeshield.ui.design.ButtonVariant
import codeshield.ui.design.Callout
import codeshield.ui.design.CalloutKind
import codeshield.ui.design.EmptyState
import codeshield.ui.design.FilterChip
import codeshield.ui.design.HDivider
import codeshield.ui.design.HeaderCell
import codeshield.ui.design.Kbd
import codeshield.ui.design.Motion
import codeshield.ui.design.ShieldVScrollbar
import codeshield.ui.design.SeverityBadge
import codeshield.ui.design.ShieldButton
import codeshield.ui.design.ShieldIconButton
import codeshield.ui.design.ShieldTextField
import codeshield.ui.design.TableHeader
import codeshield.ui.design.TableRow
import codeshield.ui.design.TintBadge
import codeshield.ui.design.Tabs
import codeshield.ui.design.ToolBadge
import codeshield.ui.design.VDivider
import codeshield.ui.theme.Radius
import codeshield.ui.theme.Shield
import codeshield.ui.theme.Space
import codeshield.ui.theme.TypeScale
import kotlinx.coroutines.delay

/**
 * Écran Findings : en-tête (fil d'Ariane + recherche + actions), chips de filtre,
 * liste dense pleine hauteur et panneau de détail à droite (Code / Guide / AI).
 */
@Composable
fun FindingsSection(
    breadcrumb: String,
    onClearFilter: () -> Unit,
    groups: List<FindingGroup>,
    selectedGroup: String?,
    onSelectGroup: (String?) -> Unit,
    visible: List<Finding>,
    selected: Finding?,
    fileLines: List<String>,
    onSelect: (Finding) -> Unit,
    onCloseDiff: () -> Unit,
    fileError: String? = null,
    allFindings: List<Finding> = visible,
    onOpenInIde: ((Finding) -> Unit)? = null,
    aiExplanation: String? = null,
    aiLoading: Boolean = false,
    aiError: String? = null,
    onExplainAi: () -> Unit = {},
    aiTriage: String? = null,
    aiTriageLoading: Boolean = false,
    aiTriageError: String? = null,
    onTriageAi: () -> Unit = {},
    onDismissTriage: () -> Unit = {},
    llmEnabled: Boolean = false,
    query: String = "",
    onQuery: (String) -> Unit = {},
    searchFocus: FocusRequester? = null,
    onRunScan: () -> Unit = {},
    isScanning: Boolean = false,
    canScan: Boolean = true,
    claudeFix: ClaudeFixState? = null,
    onFixWithClaude: ((Finding) -> Unit)? = null,
    onCancelClaudeFix: () -> Unit = {},
    onFixCriticalAndHigh: (() -> Unit)? = null,
    onDismissClaudeFix: () -> Unit = {},
) {
    // Filtres locaux (UI uniquement) : sévérités et outils.
    var sevFilter by remember { mutableStateOf(emptySet<Severity>()) }
    var toolFilter by remember { mutableStateOf(emptySet<ToolName>()) }
    val filtered = remember(visible, sevFilter, toolFilter) {
        visible.filter {
            (sevFilter.isEmpty() || it.severity in sevFilter) && (toolFilter.isEmpty() || it.tool in toolFilter)
        }.sortedBy { it.severity.ordinal }
    }
    val sevCounts = remember(visible) { visible.groupingBy { it.severity }.eachCount() }
    val toolCounts = remember(visible) { visible.groupingBy { it.tool }.eachCount() }

    val strings = codeshield.ui.i18n.LocalStrings.current
    var expanded by remember { mutableStateOf(false) }

    // Navigation clavier : j/↓ suivante, k/↑ précédente, Échap ferme le détail.
    // onKeyEvent (et non onPreviewKeyEvent) : la saisie dans la recherche n'est pas interceptée.
    fun move(delta: Int) {
        if (filtered.isEmpty()) return
        val idx = filtered.indexOf(selected)
        onSelect(filtered[if (idx < 0) 0 else (idx + delta).coerceIn(0, filtered.lastIndex)])
    }

    Column(
        Modifier.fillMaxSize().onKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (e.key) {
                Key.J, Key.DirectionDown -> { move(1); true }
                Key.K, Key.DirectionUp -> { move(-1); true }
                Key.Escape -> if (selected != null) { onCloseDiff(); true } else false
                else -> false
            }
        },
    ) {
        // ── En-tête ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = Space.xl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(strings.sidebarFindings, color = if (selectedGroup != null) Shield.TextMute else Shield.Text, fontSize = TypeScale.title, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(enabled = selectedGroup != null, onClick = onClearFilter))
            if (selectedGroup != null) {
                Text("/", color = Shield.TextMute, fontSize = TypeScale.title)
                Text(breadcrumb, color = Shield.Text, fontSize = TypeScale.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ShieldIconButton(Icons.Outlined.Close, "Clear filter", onClearFilter, size = 22.dp)
            }
            Box(Modifier.weight(1f))
            ShieldTextField(
                value = query,
                onValueChange = onQuery,
                placeholder = strings.findingsSearchPlaceholder,
                leadingIcon = Icons.Outlined.Search,
                trailing = { Kbd("⌘K") },
                focusRequester = searchFocus,
                modifier = Modifier.width(260.dp),
            )
            if (onFixCriticalAndHigh != null) {
                val urgent = remember(allFindings) { ClaudeCodeFix.batchTargets(allFindings).size }
                ShieldButton(
                    if (urgent > 0) "Fix $urgent critical/high" else "Fix critical/high",
                    onClick = onFixCriticalAndHigh,
                    leadingIcon = Icons.Outlined.AutoAwesome,
                    enabled = urgent > 0 && claudeFix?.running != true,
                    loading = claudeFix?.isBatch == true && claudeFix.running,
                )
            }
            ShieldButton(
                if (aiTriageLoading) strings.findingsTriaging else strings.findingsTriageButton,
                onClick = onTriageAi,
                leadingIcon = Icons.Outlined.AutoAwesome,
                enabled = llmEnabled && visible.isNotEmpty() && !aiTriageLoading,
                loading = aiTriageLoading,
            )
            ShieldButton(
                if (isScanning) strings.overviewScanning else strings.overviewRunScan,
                onClick = onRunScan,
                variant = ButtonVariant.Primary,
                leadingIcon = Icons.Outlined.PlayArrow,
                enabled = canScan && !isScanning,
                loading = isScanning,
            )
        }
        HDivider()

        // ── Chips de filtre ────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          // Les puces défilent horizontalement quand le détail réduit la largeur.
          Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            for (sev in Severity.entries) {
                FilterChip(
                    label = sev.name.lowercase().replaceFirstChar { it.uppercase() },
                    selected = sev in sevFilter,
                    onClick = { sevFilter = if (sev in sevFilter) sevFilter - sev else sevFilter + sev },
                    dot = Shield.severity(sev),
                    count = sevCounts[sev] ?: 0,
                )
            }
            Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(16.dp).background(Shield.Border))
            for (tool in ToolName.entries.filter { (toolCounts[it] ?: 0) > 0 || it in toolFilter }) {
                FilterChip(
                    label = Shield.toolLabel(tool),
                    selected = tool in toolFilter,
                    onClick = { toolFilter = if (tool in toolFilter) toolFilter - tool else toolFilter + tool },
                    count = toolCounts[tool] ?: 0,
                )
            }
          }
            AnimatedVisibility(sevFilter.isNotEmpty() || toolFilter.isNotEmpty(), enter = fadeIn(Motion.fast()), exit = fadeOut(Motion.fast())) {
                ShieldButton("Reset", { sevFilter = emptySet(); toolFilter = emptySet() }, variant = ButtonVariant.Ghost, size = ButtonSize.Sm)
            }
            Text("${filtered.size} / ${visible.size}", color = Shield.TextMute, fontSize = TypeScale.label, maxLines = 1, softWrap = false)
        }
        if (!llmEnabled && visible.isNotEmpty()) {
            // Indication discrète, sans bloquer la liste.
            Text(
                "Enable an LLM provider in Settings to unlock AI triage and explanations.",
                color = Shield.TextMute, fontSize = TypeScale.caption,
                modifier = Modifier.padding(start = Space.xl, bottom = 8.dp),
            )
        }
        HDivider()

        // ── Corps : liste + détail ─────────────────────────────────────────
        // Dernière faille affichée : le détail reste visible pendant son animation de fermeture.
        var shown by remember { mutableStateOf(selected) }
        if (selected != null) shown = selected
        BoxWithConstraints(Modifier.fillMaxSize()) {
        // Le détail prend un peu plus de la moitié, sans écraser la liste.
        val detailWidth = (maxWidth * 0.6f).coerceIn(480.dp, 760.dp)
        Row(Modifier.fillMaxSize()) {
            if (!(expanded && selected != null)) Column(Modifier.weight(1f).fillMaxHeight()) {
                if (claudeFix != null && claudeFix.isBatch) {
                    ClaudeBatchPanel(
                        fix = claudeFix,
                        onCancel = onCancelClaudeFix,
                        onDismiss = onDismissClaudeFix,
                        onRescan = onRunScan,
                        canRescan = canScan && !isScanning,
                        modifier = Modifier.padding(Space.lg),
                    )
                    HDivider()
                }
                if (aiTriageLoading || aiTriage != null || aiTriageError != null) {
                    TriagePanel(
                        loading = aiTriageLoading,
                        text = aiTriage,
                        error = aiTriageError,
                        onDismiss = onDismissTriage,
                        modifier = Modifier.padding(Space.lg),
                    )
                    HDivider()
                }
                FindingsTable(
                    rows = filtered,
                    selected = selected,
                    onSelect = onSelect,
                    empty = groups.isEmpty(),
                    onRunScan = onRunScan,
                    canScan = canScan && !isScanning,
                )
            }
            AnimatedVisibility(
                visible = selected != null,
                modifier = if (expanded && selected != null) Modifier.weight(1f) else Modifier,
                enter = fadeIn(Motion.base()) + slideInHorizontally(Motion.base()) { it / 8 },
                exit = fadeOut(Motion.fast()) + slideOutHorizontally(Motion.fast()) { it / 8 },
            ) {
                val current = shown ?: return@AnimatedVisibility
                Row(Modifier.fillMaxHeight()) {
                    VDivider()
                    val fileFindings = remember(allFindings, current.filePath) { allFindings.filter { it.filePath == current.filePath } }
                    DetailPane(
                        selected = current, lines = fileLines, fileError = fileError, fileFindings = fileFindings,
                        onSelect = onSelect, onClose = onCloseDiff, onOpenInIde = onOpenInIde,
                        expanded = expanded, onToggleExpand = { expanded = !expanded },
                        aiExplanation = aiExplanation, aiLoading = aiLoading, aiError = aiError,
                        onExplainAi = onExplainAi, llmEnabled = llmEnabled,
                        claudeFix = claudeFix?.takeIf { it.findings == listOf(current) },
                        claudeBusy = claudeFix?.running == true,
                        onFixWithClaude = onFixWithClaude, onCancelClaudeFix = onCancelClaudeFix,
                        onRescan = onRunScan, canRescan = canScan && !isScanning,
                        modifier = (if (expanded) Modifier.weight(1f) else Modifier.width(detailWidth)).fillMaxHeight(),
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun FindingsTable(
    rows: List<Finding>,
    selected: Finding?,
    onSelect: (Finding) -> Unit,
    empty: Boolean,
    onRunScan: () -> Unit,
    canScan: Boolean,
) {
    val strings = codeshield.ui.i18n.LocalStrings.current
    if (empty) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.AutoMirrored.Outlined.ManageSearch,
                title = strings.findingsNoFindingsTitle,
                hint = strings.findingsNoFindingsHint,
                action = { ShieldButton(strings.overviewRunScan, onRunScan, variant = ButtonVariant.Primary, leadingIcon = Icons.Outlined.PlayArrow, enabled = canScan) },
            )
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    // En dessous de cette largeur (détail ouvert), les colonnes fixes écraseraient
    // règle et fichier : on passe à une ligne compacte sur deux niveaux.
    val compact = maxWidth < 620.dp
    Column(Modifier.fillMaxSize()) {
    if (!compact) TableHeader {
        HeaderCell(strings.findingsTableSeverity, Modifier.width(88.dp))
        HeaderCell(strings.findingsTableRule, Modifier.weight(1.4f))
        HeaderCell(strings.findingsTableFile, Modifier.weight(1.6f))
        HeaderCell(strings.findingsTableLine, Modifier.width(72.dp))
        HeaderCell(strings.findingsTableTool, Modifier.width(140.dp))
    }
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(Space.xxl), contentAlignment = Alignment.Center) {
            Text(strings.findingsNoFilterMatch, color = Shield.TextMute, fontSize = TypeScale.body)
        }
        return@BoxWithConstraints
    }
    val listState: LazyListState = rememberLazyListState()
    // La ligne sélectionnée reste visible quand on navigue au clavier (j/k).
    LaunchedEffect(selected) {
        val idx = rows.indexOf(selected)
        val visibleRange = listState.layoutInfo.visibleItemsInfo.let { v -> (v.firstOrNull()?.index ?: 0)..(v.lastOrNull()?.index ?: 0) }
        if (idx >= 0 && (idx <= visibleRange.first || idx >= visibleRange.last)) listState.animateScrollToItem((idx - 3).coerceAtLeast(0))
    }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(rows, key = { index, f -> "${f.identityKey()}_$index" }) { _, f ->
            if (compact) {
                CompactFindingRow(f, selected == f, { onSelect(f) }, Modifier.animateItem())
                return@itemsIndexed
            }
            TableRow(selected = selected == f, onClick = { onSelect(f) }, modifier = Modifier.animateItem()) {
                Box(Modifier.width(88.dp)) { SeverityBadge(f.severity) }
                Text(
                    if (f.tool == ToolName.SEMGREP) f.ruleId.substringAfterLast('.') else f.title,
                    color = Shield.Text, fontSize = TypeScale.body, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1.4f).padding(end = Space.md),
                )
                Text(
                    f.filePath,
                    color = Shield.TextDim, fontSize = TypeScale.label, fontFamily = Shield.Mono,
                    maxLines = 1, overflow = TextOverflow.StartEllipsis,
                    modifier = Modifier.weight(1.6f).padding(end = Space.md),
                )
                Text(
                    if (f.lastLine != f.lineNumber) "${f.lineNumber}-${f.lastLine}" else f.lineNumber.toString(),
                    color = Shield.TextDim, fontSize = TypeScale.label, fontFamily = Shield.Mono, maxLines = 1,
                    modifier = Modifier.width(72.dp),
                )
                ToolBadge(f.tool, Modifier.width(140.dp))
            }
        }
    }
    ShieldVScrollbar(listState)
    }
    }
    }
}

/** Ligne compacte (liste étroite) : sévérité, titre, puis fichier:ligne et moteur. */
@Composable
private fun CompactFindingRow(f: Finding, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TableRow(selected = selected, onClick = onClick, modifier = modifier, height = 54.dp) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                SeverityBadge(f.severity)
                Text(
                    if (f.tool == ToolName.SEMGREP) f.ruleId.substringAfterLast('.') else f.title,
                    color = Shield.Text, fontSize = TypeScale.body, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(
                    "${f.filePath}:${f.lineNumber}",
                    color = Shield.TextDim, fontSize = TypeScale.caption, fontFamily = Shield.Mono,
                    maxLines = 1, overflow = TextOverflow.StartEllipsis, modifier = Modifier.weight(1f, fill = false),
                )
                ToolBadge(f.tool)
            }
        }
    }
}

/**
 * Panneau de réponse du triage IA (métadonnées uniquement).
 * Chargement → spinner ; succès → markdown scrollable et copiable ; échec → callout.
 */
@Composable
private fun TriagePanel(
    loading: Boolean,
    text: String?,
    error: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            androidx.compose.material3.Icon(Icons.Outlined.AutoAwesome, null, tint = Shield.Accent, modifier = Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text("AI triage", color = Shield.Text, fontSize = TypeScale.body, fontWeight = FontWeight.SemiBold)
                Text("Metadata only — source code never leaves your machine.", color = Shield.TextMute, fontSize = TypeScale.caption)
            }
            if (text != null) {
                ShieldButton(
                    if (copied) "Copied" else "Copy",
                    onClick = {
                        @Suppress("DEPRECATION")
                        clipboard.setText(AnnotatedString(text))
                        copied = true
                    },
                    variant = ButtonVariant.Ghost, size = ButtonSize.Sm, leadingIcon = Icons.Outlined.ContentCopy,
                )
            }
            ShieldIconButton(Icons.Outlined.Close, "Dismiss", onDismiss)
        }
        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                CircularProgressIndicator(Modifier.size(14.dp), color = Shield.Accent, strokeWidth = 1.5.dp)
                Text("Analyzing findings…", color = Shield.TextDim, fontSize = TypeScale.label)
            }
            error != null -> Callout("Triage failed: $error", CalloutKind.Error)
            text != null -> SelectionContainer {
                Box(
                    Modifier.fillMaxWidth().heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    MarkdownText(text, color = Shield.TextDim, fontSize = TypeScale.body, lineHeight = 19.sp)
                }
            }
        }
    }
}

/** Panneau de détail : en-tête, métadonnées, onglets Code / Guide / AI. */
@Composable
private fun DetailPane(
    selected: Finding,
    lines: List<String>,
    fileError: String?,
    fileFindings: List<Finding>,
    onSelect: (Finding) -> Unit,
    onClose: () -> Unit,
    onOpenInIde: ((Finding) -> Unit)?,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    aiExplanation: String?,
    aiLoading: Boolean,
    aiError: String?,
    onExplainAi: () -> Unit,
    llmEnabled: Boolean,
    claudeFix: ClaudeFixState?,
    claudeBusy: Boolean,
    onFixWithClaude: ((Finding) -> Unit)?,
    onCancelClaudeFix: () -> Unit,
    onRescan: () -> Unit,
    canRescan: Boolean,
    modifier: Modifier = Modifier,
) {
    var tab by remember(selected) { mutableStateOf(if (claudeFix != null) 3 else 0) }
    val explanation = remember(selected) { explainFinding(selected) }
    Column(modifier.background(Shield.Surface)) {
        // En-tête
        Column(Modifier.fillMaxWidth().padding(start = Space.lg, end = Space.sm, top = Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                SeverityBadge(selected.severity)
                ToolBadge(selected.tool)
                val (catLabel, catColor) = Shield.category(selected.effectiveCategory)
                TintBadge(catLabel, catColor)
                Box(Modifier.weight(1f))
                Kbd("J/K")
                ShieldIconButton(if (expanded) Icons.Outlined.CloseFullscreen else Icons.Outlined.OpenInFull, if (expanded) "Réduire" else "Agrandir", onToggleExpand)
                ShieldIconButton(Icons.Outlined.Close, "Close", onClose)
            }
            Text(
                selected.title,
                color = Shield.Text, fontSize = TypeScale.title, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Space.sm, end = Space.sm),
            )
            Text(
                explanation.category,
                color = Shield.TextDim, fontSize = TypeScale.label,
                modifier = Modifier.padding(top = 2.dp, end = Space.sm),
            )
            // Métadonnées structurées : règle, CWE, OWASP, paquet.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = Space.sm, end = Space.sm),
            ) {
                Text(
                    selected.ruleId,
                    color = Shield.TextMute, fontSize = TypeScale.caption, fontFamily = Shield.Mono,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                for (c in selected.cwe.take(3)) TintBadge(c, Shield.Info)
                for (o in selected.owasp.take(1)) TintBadge(o.substringBefore(" -").take(14), Shield.Warning)
            }
            if (selected.packageName != null) {
                Text(
                    buildString {
                        append(selected.packageName)
                        selected.installedVersion?.let { append(" @ $it") }
                        selected.fixedVersion?.let { append("  →  corrigé en $it") }
                    },
                    color = Shield.Success, fontSize = TypeScale.label, fontFamily = Shield.Mono,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Tabs(
                if (onFixWithClaude != null) listOf("Code", "Guide", "AI", "Claude Code") else listOf("Code", "Guide", "AI"),
                tab, { tab = it }, Modifier.padding(top = Space.sm),
            )
        }
        HDivider()

        Box(Modifier.fillMaxSize()) {
            when (tab) {
                0 -> CodeViewer(
                    selected = selected, lines = lines, error = fileError, fileFindings = fileFindings,
                    onSelectFinding = onSelect, onOpenInIde = onOpenInIde,
                )
                1 -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.lg),
                    verticalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    GuideBlock("What it is", explanation.what)
                    if (!selected.message.isNullOrBlank()) {
                        Text(
                            selected.message,
                            color = Shield.TextDim, fontSize = TypeScale.label, fontFamily = Shield.Mono,
                            modifier = Modifier.fillMaxWidth().background(Shield.Code, Radius.md).padding(Space.md),
                        )
                    }
                    GuideBlock("Why it matters", explanation.why)
                    Text(severityBlurb(selected.severity), color = Shield.TextMute, fontSize = TypeScale.label)
                    GuideBlock("How to fix", explanation.fix)
                    if (explanation.references.isNotEmpty()) {
                        Text("References", color = Shield.Text, fontSize = TypeScale.label, fontWeight = FontWeight.SemiBold)
                        for (ref in explanation.references) {
                            Text(ref, color = Shield.Accent, fontSize = TypeScale.label)
                        }
                    }
                }
                3 -> ClaudeFixPane(
                    fix = claudeFix,
                    busy = claudeBusy,
                    onFix = { onFixWithClaude?.invoke(selected) },
                    onCancel = onCancelClaudeFix,
                    onRescan = onRescan,
                    canRescan = canRescan,
                    onOpenInIde = onOpenInIde?.let { open -> { open(selected) } },
                )
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.lg),
                    verticalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Contextual explanation from rule, file and severity — never source code.",
                            color = Shield.TextMute, fontSize = TypeScale.label, modifier = Modifier.weight(1f).padding(end = Space.md),
                        )
                        ShieldButton(
                            if (aiLoading) "Explaining…" else if (aiExplanation != null) "Regenerate" else "Explain",
                            onClick = onExplainAi,
                            leadingIcon = Icons.Outlined.AutoAwesome,
                            size = ButtonSize.Sm,
                            enabled = llmEnabled && !aiLoading,
                            loading = aiLoading,
                        )
                    }
                    when {
                        !llmEnabled -> Callout("AI is disabled. Configure a provider (Gemini, Claude, Ollama…) in Settings. The offline guide is available in the Guide tab.")
                        aiError != null -> Callout(aiError, CalloutKind.Error)
                        aiExplanation != null -> SelectionContainer {
                            MarkdownText(aiExplanation, color = Shield.TextDim, fontSize = TypeScale.body, lineHeight = 19.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Bandeau de la correction groupée CRITICAL/HIGH (au-dessus de la liste). */
@Composable
private fun ClaudeBatchPanel(
    fix: ClaudeFixState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onRescan: () -> Unit,
    canRescan: Boolean,
    modifier: Modifier = Modifier,
) {
    val logScroll = rememberScrollState()
    LaunchedEffect(fix.log.size) { logScroll.animateScrollTo(logScroll.maxValue) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            androidx.compose.material3.Icon(Icons.Outlined.AutoAwesome, null, tint = Shield.Accent, modifier = Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Claude Code · ${fix.findings.size} critical/high findings", color = Shield.Text, fontSize = TypeScale.body, fontWeight = FontWeight.SemiBold)
                Text("Runs locally in the project folder. Your code goes to Anthropic through Claude Code, never to the CodeShield server.", color = Shield.TextMute, fontSize = TypeScale.caption)
            }
            if (fix.running) {
                ShieldButton("Stop", onClick = onCancel, size = ButtonSize.Sm, leadingIcon = Icons.Outlined.Close)
            } else {
                if (fix.success == true) {
                    ShieldButton("Re-scan", onClick = onRescan, variant = ButtonVariant.Primary, leadingIcon = Icons.Outlined.PlayArrow, size = ButtonSize.Sm, enabled = canRescan)
                }
                ShieldIconButton(Icons.Outlined.Close, "Dismiss", onDismiss)
            }
        }
        if (fix.running) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                CircularProgressIndicator(Modifier.size(14.dp), color = Shield.Accent, strokeWidth = 1.5.dp)
                Text("Claude Code is working…", color = Shield.TextDim, fontSize = TypeScale.label)
            }
        }
        if (fix.log.isNotEmpty() && fix.summary == null) {
            SelectionContainer {
                Text(
                    fix.log.joinToString("\n"),
                    color = Shield.TextDim, fontSize = TypeScale.label, fontFamily = Shield.Mono,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).background(Shield.Code, Radius.md)
                        .verticalScroll(logScroll).padding(Space.md),
                )
            }
        }
        val summary = fix.summary
        if (summary != null) {
            if (fix.success == true) {
                Callout("Claude Code finished. Review the changes, then re-scan to confirm.", CalloutKind.Success)
                SelectionContainer {
                    Box(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                        MarkdownText(summary, color = Shield.TextDim, fontSize = TypeScale.body, lineHeight = 19.sp)
                    }
                }
            } else {
                Callout(summary, CalloutKind.Error)
            }
        }
    }
}

/** Onglet Claude Code : lance la correction locale et suit sa progression. */
@Composable
private fun ClaudeFixPane(
    fix: ClaudeFixState?,
    busy: Boolean,
    onFix: () -> Unit,
    onCancel: () -> Unit,
    onRescan: () -> Unit,
    canRescan: Boolean,
    onOpenInIde: (() -> Unit)?,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(fix?.log?.size) { scroll.animateScrollTo(scroll.maxValue) }
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Claude Code runs locally in the project folder, reads the file and edits it. " +
                    "Your code goes to Anthropic through Claude Code, never to the CodeShield server.",
                color = Shield.TextMute, fontSize = TypeScale.label, modifier = Modifier.weight(1f).padding(end = Space.md),
            )
            if (fix?.running == true) {
                ShieldButton("Stop", onClick = onCancel, size = ButtonSize.Sm, leadingIcon = Icons.Outlined.Close)
            } else {
                ShieldButton(
                    if (fix != null) "Fix again" else "Fix with Claude Code",
                    onClick = onFix,
                    variant = ButtonVariant.Primary,
                    leadingIcon = Icons.Outlined.AutoAwesome,
                    size = ButtonSize.Sm,
                    enabled = !busy,
                )
            }
        }
        if (fix == null) {
            if (busy) Callout("Claude Code is already fixing other findings.")
            return@Column
        }
        if (fix.running) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                CircularProgressIndicator(Modifier.size(14.dp), color = Shield.Accent, strokeWidth = 2.dp)
                Text("Claude Code is working…", color = Shield.TextDim, fontSize = TypeScale.label)
            }
        }
        if (fix.log.isNotEmpty()) {
            SelectionContainer {
                Text(
                    fix.log.joinToString("\n"),
                    color = Shield.TextDim, fontSize = TypeScale.label, fontFamily = Shield.Mono,
                    modifier = Modifier.fillMaxWidth().background(Shield.Code, Radius.md).padding(Space.md),
                )
            }
        }
        val summary = fix.summary
        if (summary != null) {
            if (fix.success == true) {
                Callout("Claude Code finished. Review the changes, then re-scan to confirm.", CalloutKind.Success)
                SelectionContainer { MarkdownText(summary, color = Shield.TextDim, fontSize = TypeScale.body, lineHeight = 19.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    ShieldButton("Re-scan", onClick = onRescan, variant = ButtonVariant.Primary, leadingIcon = Icons.Outlined.PlayArrow, size = ButtonSize.Sm, enabled = canRescan)
                    if (onOpenInIde != null) ShieldButton("Open in IDE", onClick = onOpenInIde, size = ButtonSize.Sm)
                }
            } else {
                Callout(summary, CalloutKind.Error)
            }
        }
    }
}

@Composable
private fun GuideBlock(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = Shield.Text, fontSize = TypeScale.label, fontWeight = FontWeight.SemiBold)
        Text(body, color = Shield.TextDim, fontSize = TypeScale.body)
    }
}

