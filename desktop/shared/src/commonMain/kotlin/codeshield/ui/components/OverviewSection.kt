package codeshield.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import codeshield.logic.ScoreCalculator
import codeshield.model.Severity
import codeshield.model.ToolName
import codeshield.tools.ToolState
import codeshield.tools.ToolStatus
import codeshield.scanner.ScannerEngine
import codeshield.scanner.ScannerEngines
import codeshield.ui.EngineRun
import codeshield.ui.EngineState
import codeshield.ui.ScannerUiState
import codeshield.ui.countBySeverity
import codeshield.ui.design.ButtonSize
import codeshield.ui.design.ButtonVariant
import codeshield.ui.design.Callout
import codeshield.ui.design.CalloutKind
import codeshield.ui.design.HDivider
import codeshield.ui.design.Motion
import codeshield.ui.design.ShieldVScrollbar
import codeshield.ui.design.animateOnAppear
import codeshield.ui.design.PageHeader
import codeshield.ui.design.Panel
import codeshield.ui.design.ShieldButton
import codeshield.ui.design.ShieldSwitch
import codeshield.ui.design.ShieldTextField
import codeshield.ui.design.StatusDot
import codeshield.ui.design.TintBadge
import codeshield.ui.theme.Radius
import codeshield.ui.theme.Shield
import codeshield.ui.theme.Space
import codeshield.ui.theme.TypeScale

/**
 * Section Overview : vue d'ensemble du projet, configuration des moteurs,
 * bandeau KPI de sécurité, statut du backend et distribution des vulnérabilités.
 */
@Composable
fun OverviewSection(
    state: ScannerUiState,
    dirValid: Boolean,
    onDirChange: (String) -> Unit,
    onBrowse: () -> Unit,
    onToggleEngine: (ToolName, Boolean) -> Unit,
    onToggleAutoDetect: (Boolean) -> Unit,
    onInstall: (ToolName) -> Unit,
    onRun: () -> Unit,
    onCancel: () -> Unit = {},
    onStartBackend: () -> Unit = {},
    onRunPentest: () -> Unit = {},
    onOpenFindings: () -> Unit = {},
) {
    val counts = countBySeverity(state.findings)
    val (score, grade) = ScoreCalculator.computeScore(counts)
    val criticalCount = counts[Severity.CRITICAL] ?: 0
    val highCount = counts[Severity.HIGH] ?: 0
    val strings = codeshield.ui.i18n.LocalStrings.current

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = strings.overviewTitle,
            subtitle = if (state.projectDir.isNotBlank()) state.projectDir.substringAfterLast('/') else strings.pentestNoTargetFolder,
            actions = {
                if (state.findings.isNotEmpty()) {
                    ShieldButton(
                        "${strings.sidebarFindings} (${state.findings.size})",
                        onClick = onOpenFindings,
                        leadingIcon = Icons.AutoMirrored.Outlined.ListAlt,
                        size = ButtonSize.Sm,
                    )
                }
                ShieldButton(
                    if (state.isScanning) strings.overviewScanning else strings.overviewRunScan,
                    onClick = onRun,
                    variant = ButtonVariant.Primary,
                    leadingIcon = Icons.Outlined.PlayArrow,
                    size = ButtonSize.Sm,
                    enabled = !state.isScanning && dirValid,
                    loading = state.isScanning,
                )
            },
        )
        HDivider()

        val scroll = rememberScrollState()
        Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            // ── Bandeau KPI ─────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                ScoreCard(
                    label = strings.overviewKpiScore,
                    grade = grade,
                    score = score,
                    accent = when (grade) {
                        "A" -> Shield.Success
                        "B" -> Shield.Text
                        "C" -> Shield.Warning
                        else -> Shield.Error
                    },
                    modifier = Modifier.weight(1f),
                    onClick = onOpenFindings,
                )
                KpiCard(
                    label = strings.overviewKpiCritical,
                    value = criticalCount,
                    secondary = if (criticalCount > 0) "Immediate action" else "Clean",
                    accent = Shield.severity(Severity.CRITICAL),
                    modifier = Modifier.weight(1f),
                    onClick = onOpenFindings,
                )
                KpiCard(
                    label = strings.overviewKpiHigh,
                    value = highCount,
                    secondary = "High priority",
                    accent = Shield.severity(Severity.HIGH),
                    modifier = Modifier.weight(1f),
                    onClick = onOpenFindings,
                )
                KpiCard(
                    label = strings.overviewKpiTotal,
                    value = state.findings.size,
                    secondary = "${counts[Severity.MEDIUM] ?: 0} med · ${counts[Severity.LOW] ?: 0} low",
                    accent = Shield.TextDim,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenFindings,
                )
            }

            // ── Grille 2 colonnes ───────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.lg),
            ) {
                // Colonne 1 : Configuration du scan
                Column(
                    modifier = Modifier.weight(1.1f),
                    verticalArrangement = Arrangement.spacedBy(Space.lg),
                ) {
                    Panel(
                        title = strings.overviewConfigTitle,
                        subtitle = strings.overviewConfigSubtitle,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                            Text(strings.overviewProjectDirLabel, color = Shield.TextDim, fontSize = TypeScale.label, fontWeight = FontWeight.Medium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ShieldTextField(
                                    value = state.projectDir,
                                    onValueChange = onDirChange,
                                    placeholder = "/path/to/source/code",
                                    mono = true,
                                    leadingIcon = Icons.Outlined.FolderOpen,
                                    modifier = Modifier.weight(1f),
                                )
                                ShieldButton(strings.overviewBrowse, onBrowse, size = ButtonSize.Md)
                            }
                            if (!dirValid && state.projectDir.isNotBlank()) {
                                Callout(strings.overviewDirNotFound, CalloutKind.Error)
                            }
                        }

                        HDivider()

                        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                            Text(strings.overviewEnginesTitle, color = Shield.TextDim, fontSize = TypeScale.label, fontWeight = FontWeight.Medium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            ) {
                                ShieldSwitch(checked = state.autoDetectEngines, onCheckedChange = onToggleAutoDetect)
                                Text(
                                    "Auto-détection : ignorer les moteurs sans cible (ex. Bandit sans Python)",
                                    color = Shield.TextMute, fontSize = TypeScale.caption,
                                )
                            }
                            for (engine in ScannerEngines.all) {
                                EngineSwitchRow(
                                    engine = engine,
                                    checked = engine.tool in state.enabledEngines,
                                    onCheckedChange = { onToggleEngine(engine.tool, it) },
                                    status = state.toolStatuses[engine.tool],
                                    run = state.engineRuns[engine.tool],
                                    onInstall = onInstall,
                                )
                            }
                        }

                        HDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                ShieldButton(
                                    if (state.isScanning) strings.overviewScanning else strings.overviewRunScan,
                                    onClick = onRun,
                                    variant = ButtonVariant.Primary,
                                    leadingIcon = Icons.Outlined.PlayArrow,
                                    enabled = !state.isScanning && dirValid,
                                    loading = state.isScanning,
                                )
                                if (state.isScanning) {
                                    ShieldButton("Stop", onClick = onCancel, variant = ButtonVariant.Ghost, leadingIcon = Icons.Outlined.Stop)
                                }
                            }
                            if (state.isScanning) {
                                val done = state.engineRuns.values.count { it.state == codeshield.ui.EngineState.DONE }
                                val total = state.engineRuns.values.count { it.state != codeshield.ui.EngineState.SKIPPED && it.state != codeshield.ui.EngineState.MISSING }
                                Text("$done / $total moteurs terminés", color = Shield.Accent, fontSize = TypeScale.label)
                            } else if (state.terminal.isNotEmpty()) {
                                state.terminal.lastOrNull()?.let { last ->
                                    Text(
                                        last.take(60),
                                        color = Shield.TextMute,
                                        fontSize = TypeScale.caption,
                                        fontFamily = Shield.Mono,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                // Colonne 2 : Distribution & Backend Pentest
                Column(
                    modifier = Modifier.weight(0.9f),
                    verticalArrangement = Arrangement.spacedBy(Space.lg),
                ) {
                    // Carte Score et Sévérités
                    Panel(
                        title = "Findings distribution",
                        subtitle = "${state.findings.size} issues identified",
                    ) {
                        for (sev in Severity.entries) {
                            val count = counts[sev] ?: 0
                            val max = (counts.values.maxOrNull() ?: 1).coerceAtLeast(1)
                            // Les barres se remplissent l'une après l'autre à l'arrivée sur l'écran.
                            val fraction by animateOnAppear(count.toFloat() / max.toFloat(), delayMs = 120 + sev.ordinal * 70)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            ) {
                                StatusDot(Shield.severity(sev), 8.dp)
                                Text(
                                    sev.name.lowercase().replaceFirstChar { it.uppercase() },
                                    color = Shield.TextDim,
                                    fontSize = TypeScale.body,
                                    modifier = Modifier.width(72.dp),
                                )
                                Box(
                                    modifier = Modifier.weight(1f).height(6.dp).clip(Radius.full).background(Shield.Pill),
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f))
                                            .height(6.dp).clip(Radius.full).background(Shield.severity(sev)),
                                    )
                                }
                                Text(
                                    count.toString(),
                                    color = if (count > 0) Shield.Text else Shield.TextMute,
                                    fontSize = TypeScale.label,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.width(36.dp),
                                )
                            }
                        }
                        if (state.findings.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenFindings).padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text("Browse all in findings view", color = Shield.Accent, fontSize = TypeScale.label, fontWeight = FontWeight.Medium)
                                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = Shield.Accent, modifier = Modifier.size(13.dp))
                            }
                        }
                    }

                    // Carte Backend & Robustesse
                    Panel(
                        title = "Backend & Pentest",
                        subtitle = state.serverUrl,
                        actions = {
                            StatusDot(if (state.backendState.running) Shield.Success else Shield.Error)
                            Text(
                                if (state.backendState.running) "Online" else "Stopped",
                                color = Shield.TextDim,
                                fontSize = TypeScale.caption,
                            )
                        },
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                            Text(
                                if (state.backendState.running) "Backend service is responding normally to audit probes."
                                else "Backend is offline. Start the service or run dynamic penetration tests.",
                                color = Shield.TextMute, fontSize = TypeScale.label,
                            )
                            state.pentestReport?.let { rep ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(Radius.md).background(Shield.Code).padding(Space.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                                ) {
                                    Icon(Icons.Outlined.Security, null, tint = Shield.Accent, modifier = Modifier.size(16.dp))
                                    Text("Robustness: ${rep.totalScore}% (${rep.grade})", color = Shield.Text, fontSize = TypeScale.label, fontWeight = FontWeight.Medium)
                                    Text("· ${rep.passedCount}/${rep.checks.size} checks", color = Shield.TextMute, fontSize = TypeScale.caption)
                                }
                            }
                            if (state.backendState.error != null) {
                                Callout(state.backendState.error, CalloutKind.Error)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            ) {
                                if (!state.backendState.running) {
                                    ShieldButton(
                                        if (state.backendState.starting) "Starting…" else "Start backend",
                                        onClick = onStartBackend,
                                        size = ButtonSize.Sm,
                                        enabled = !state.backendState.starting,
                                        loading = state.backendState.starting,
                                    )
                                }
                                ShieldButton(
                                    if (state.isPentesting) "Auditing…" else "⚡ Run Pentest",
                                    onClick = onRunPentest,
                                    size = ButtonSize.Sm,
                                    enabled = !state.isPentesting,
                                    loading = state.isPentesting,
                                )
                            }
                        }
                    }

                    // Dernier message de sync si dispo
                    state.syncMessage?.let {
                        Callout(it, CalloutKind.Info)
                    }
                }
            }
        }
        ShieldVScrollbar(scroll)
        }
    }
}

/** Carte KPI : survol qui éclaire la bordure, léger soulèvement. */
@Composable
private fun KpiShell(modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val border by animateColorAsState(if (hovered) Shield.BorderStrong else Shield.Border, Motion.fast(), label = "kpiBorder")
    val bg by animateColorAsState(if (hovered) Shield.Raised else Shield.Surface, Motion.fast(), label = "kpiBg")
    val lift by animateDpAsState(if (hovered) (-2).dp else 0.dp, Motion.base(), label = "kpiLift")
    Box(
        modifier = modifier
            .offset(y = lift)
            .clip(Radius.lg)
            .background(bg)
            .border(1.dp, border, Radius.lg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = Space.lg, vertical = Space.md),
    ) { content() }
}

/** Score : note en grand, anneau qui se trace jusqu'au score, compteur /100. */
@Composable
private fun ScoreCard(label: String, grade: String, score: Int, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val progress by animateOnAppear(score / 100f, durationMs = 1100, delayMs = 150)
    KpiShell(modifier, onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, color = Shield.TextMute, fontSize = TypeScale.caption, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(grade, color = accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("${(progress * 100).roundToInt()} / 100", color = Shield.TextDim, fontSize = TypeScale.caption, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Canvas(Modifier.size(40.dp)) {
                val stroke = 4.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(Shield.Pill, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(accent, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
private fun KpiCard(
    label: String,
    value: Int,
    secondary: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    // Le chiffre monte jusqu'à sa valeur à l'ouverture de l'écran.
    val shown by animateOnAppear(value.toFloat(), durationMs = 700, delayMs = 100)
    KpiShell(modifier, onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = Shield.TextMute, fontSize = TypeScale.caption, fontWeight = FontWeight.Medium)
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(shown.roundToInt().toString(), color = accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(secondary, color = Shield.TextDim, fontSize = TypeScale.caption, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

@Composable
private fun EngineSwitchRow(
    engine: ScannerEngine,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    status: ToolStatus?,
    run: EngineRun?,
    onInstall: (ToolName) -> Unit,
) {
    val tool = engine.tool
    Column(
        modifier = Modifier.fillMaxWidth().clip(Radius.md).background(Shield.Raised).padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            ShieldSwitch(checked = checked, onCheckedChange = onCheckedChange)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(engine.label, color = Shield.Text, fontSize = TypeScale.body, fontWeight = FontWeight.Medium)
                    for (c in engine.categories) {
                        val (label, color) = Shield.category(c)
                        TintBadge(label, color)
                    }
                }
                Text(engine.description, color = Shield.TextMute, fontSize = TypeScale.caption)
            }
            run?.let { EngineRunBadge(it) }
            when (status?.state) {
                ToolState.READY -> TintBadge("Ready", Shield.Success)
                ToolState.INSTALLING -> TintBadge("Installing…", Shield.Accent)
                ToolState.MISSING, null -> {
                    if (status?.autoInstallable ?: (tool != ToolName.DEPENDENCY_CHECK)) {
                        ShieldButton("Download", { onInstall(tool) }, size = ButtonSize.Sm, variant = ButtonVariant.Ghost)
                    } else {
                        Text("manual install", color = Shield.TextMute, fontSize = TypeScale.caption)
                    }
                }
            }
        }
        if (run?.state == EngineState.RUNNING) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clip(Radius.full),
                color = Shield.toolBadge(tool).second,
                trackColor = Shield.Pill,
            )
        }
        if (status?.state == ToolState.INSTALLING) {
            val progress = status.progress
            if (progress != null && progress >= 0f) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clip(Radius.full),
                    color = Shield.Accent,
                    trackColor = Shield.Pill,
                )
            }
        }
        val error = status?.error ?: run?.message?.takeIf { run.state == EngineState.FAILED || run.state == EngineState.MISSING }
        if (error != null) {
            Text(error, color = Shield.Error, fontSize = TypeScale.caption, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun EngineRunBadge(run: EngineRun) {
    val secs = run.durationMs?.let { " · ${it / 1000}s" }.orEmpty()
    when (run.state) {
        EngineState.QUEUED -> TintBadge("En attente", Shield.TextMute)
        EngineState.RUNNING -> TintBadge("En cours…", Shield.Accent)
        EngineState.DONE -> TintBadge("${run.findings} findings$secs", if (run.findings > 0) Shield.Warning else Shield.Success)
        EngineState.FAILED -> TintBadge("Échec", Shield.Error)
        EngineState.TIMEOUT -> TintBadge("Timeout", Shield.Error)
        EngineState.MISSING -> TintBadge("Absent", Shield.Error)
        EngineState.SKIPPED -> TintBadge("Ignoré", Shield.TextMute)
        EngineState.CANCELLED -> TintBadge("Annulé", Shield.TextMute)
    }
}
