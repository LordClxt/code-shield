package codeshield.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import codeshield.cache.PersistedScanMeta
import codeshield.cache.formatTs
import codeshield.model.Severity
import codeshield.ui.ScannerUiState
import codeshield.ui.design.ButtonSize
import codeshield.ui.design.ButtonVariant
import codeshield.ui.design.Callout
import codeshield.ui.design.CalloutKind
import codeshield.ui.design.EmptyState
import codeshield.ui.design.HDivider
import codeshield.ui.design.HeaderCell
import codeshield.ui.design.PageHeader
import codeshield.ui.design.Panel
import codeshield.ui.design.ShieldButton
import codeshield.ui.design.ShieldIconButton
import codeshield.ui.design.ShieldTextField
import codeshield.ui.design.StatusDot
import codeshield.ui.design.TableHeader
import codeshield.ui.design.TableRow
import codeshield.ui.design.TintBadge
import codeshield.ui.design.VDivider
import codeshield.ui.theme.Radius
import codeshield.ui.theme.Shield
import codeshield.ui.theme.Space
import codeshield.ui.theme.TypeScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Section Pipeline : console temps réel des scanners CLI, contrôles de défilement,
 * et historique persisté des scans précédents.
 */
@Composable
fun PipelineSection(
    state: ScannerUiState,
    onRun: () -> Unit,
    onRestore: (PersistedScanMeta) -> Unit = {},
    onClearHistory: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var follow by remember { mutableStateOf(true) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(state.terminal.size, state.isScanning) {
        if (state.terminal.isNotEmpty() && (follow || !state.isScanning)) {
            listState.scrollToItem(state.terminal.size - 1)
        }
    }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    fun scrollTop() {
        if (state.terminal.isEmpty()) return
        scope.launch { listState.scrollToItem(0) }
    }
    fun scrollBottom() {
        if (state.terminal.isEmpty()) return
        scope.launch { listState.scrollToItem(state.terminal.size - 1) }
    }
    fun copyLogs() {
        if (state.terminal.isEmpty()) return
        @Suppress("DEPRECATION")
        clipboard.setText(AnnotatedString(state.terminal.joinToString("\n")))
        copied = true
    }

    val strings = codeshield.ui.i18n.LocalStrings.current

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = strings.pipelineTitle,
            subtitle = if (state.isScanning) strings.overviewScanning else "${state.terminal.size} log lines",
            actions = {
                ShieldButton(
                    if (copied) "Copied" else "Copy logs",
                    onClick = ::copyLogs,
                    leadingIcon = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                    size = ButtonSize.Sm,
                    enabled = state.terminal.isNotEmpty(),
                )
                ShieldButton(
                    if (state.isScanning) strings.overviewScanning else strings.overviewRunScan,
                    onClick = onRun,
                    variant = ButtonVariant.Primary,
                    leadingIcon = Icons.Outlined.PlayArrow,
                    size = ButtonSize.Sm,
                    enabled = !state.isScanning && state.projectDir.isNotBlank(),
                    loading = state.isScanning,
                )
            },
        )
        HDivider()

        if (state.restoreMessage != null) {
            Callout(state.restoreMessage, CalloutKind.Info, modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.sm))
            HDivider()
        }

        // Barre d'outils terminal
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            ShieldButton(
                "Scroll top",
                onClick = ::scrollTop,
                leadingIcon = Icons.Outlined.KeyboardArrowUp,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Sm,
                enabled = state.terminal.isNotEmpty(),
            )
            ShieldButton(
                "Scroll bottom",
                onClick = ::scrollBottom,
                leadingIcon = Icons.Outlined.KeyboardArrowDown,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Sm,
                enabled = state.terminal.isNotEmpty(),
            )
            ShieldButton(
                if (follow) "Auto-scroll: On" else "Auto-scroll: Off",
                onClick = { follow = !follow },
                variant = if (follow) ButtonVariant.Secondary else ButtonVariant.Ghost,
                size = ButtonSize.Sm,
            )
            Box(Modifier.weight(1f))
            if (state.isScanning) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusDot(Shield.Accent)
                    Text("Streaming CLI output…", color = Shield.Accent, fontSize = TypeScale.label)
                }
            } else {
                Text("${state.terminal.size} lines", color = Shield.TextMute, fontSize = TypeScale.caption)
            }
        }
        HDivider()

        // Zone terminal + historique en bas
        Column(modifier = Modifier.fillMaxSize().padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            // Fenêtre de console
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(Radius.lg)
                    .background(Shield.Code)
                    .border(1.dp, Shield.Border, Radius.lg),
            ) {
                if (state.terminal.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Outlined.Terminal,
                            title = strings.pipelineNoOutputTitle,
                            hint = strings.pipelineNoOutputHint,
                        )
                    }
                } else {
                    SelectionContainer {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(Space.md),
                        ) {
                            items(state.terminal) { line ->
                                val (color, bg) = when {
                                    line.startsWith("[error]") -> Shield.severity(Severity.CRITICAL) to Shield.Error.copy(alpha = 0.08f)
                                    line.startsWith("[stderr]") -> Shield.severity(Severity.HIGH) to Shield.Warning.copy(alpha = 0.06f)
                                    line.startsWith("[warn]") -> Shield.severity(Severity.MEDIUM) to Color.Transparent
                                    line.startsWith("[semgrep]") || line.startsWith("[gitleaks]") || line.startsWith("[depcheck]") -> Shield.Accent to Color.Transparent
                                    else -> Shield.TextDim to Color.Transparent
                                }
                                Text(
                                    line,
                                    color = color,
                                    fontSize = TypeScale.label,
                                    fontFamily = Shield.Mono,
                                    modifier = Modifier.fillMaxWidth().background(bg).padding(vertical = 1.dp, horizontal = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Historique des scans
            if (state.history.isNotEmpty()) {
                Panel(
                    title = "Scan history",
                    subtitle = "Persisted locally across app sessions (${state.history.size} runs)",
                    actions = {
                        ShieldButton(
                            "Clear project history",
                            onClick = onClearHistory,
                            leadingIcon = Icons.Outlined.DeleteOutline,
                            variant = ButtonVariant.Ghost,
                            size = ButtonSize.Sm,
                        )
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    TableHeader {
                        HeaderCell("Target project", Modifier.weight(1.5f))
                        HeaderCell("Timestamp", Modifier.weight(1.2f))
                        HeaderCell("Findings", Modifier.width(100.dp))
                        HeaderCell("Action", Modifier.width(90.dp))
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                        items(state.history.take(20), key = { it.projectDir + it.finishedAtEpochMs }) { meta ->
                            TableRow(selected = false, onClick = { onRestore(meta) }) {
                                Text(
                                    meta.projectDir.substringAfterLast('/'),
                                    color = Shield.Text,
                                    fontSize = TypeScale.body,
                                    fontFamily = Shield.Mono,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1.5f),
                                )
                                Text(
                                    formatTs(meta.finishedAtEpochMs),
                                    color = Shield.TextMute,
                                    fontSize = TypeScale.label,
                                    fontFamily = Shield.Mono,
                                    modifier = Modifier.weight(1.2f),
                                )
                                Box(Modifier.width(100.dp)) {
                                    TintBadge("${meta.findingsCount} findings", if (meta.findingsCount > 0) Shield.Accent else Shield.TextMute)
                                }
                                Box(Modifier.width(90.dp), contentAlignment = Alignment.CenterEnd) {
                                    ShieldButton("Restore", onClick = { onRestore(meta) }, size = ButtonSize.Sm, variant = ButtonVariant.Ghost)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Section Cloud Sync : configuration serveur + envoi des métadonnées vers l'API CodeShield.
 */
@Composable
fun SyncSection(
    state: ScannerUiState,
    onServerChange: (String) -> Unit,
    onProjectIdChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onSync: () -> Unit,
) {
    var showToken by remember { mutableStateOf(false) }

    val strings = codeshield.ui.i18n.LocalStrings.current

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = strings.syncTitle,
            subtitle = strings.syncSubtitle,
            actions = {
                ShieldButton(
                    if (state.findings.isEmpty()) "No findings to sync" else "Sync ${state.findings.size} findings",
                    onClick = onSync,
                    variant = ButtonVariant.Primary,
                    leadingIcon = Icons.Outlined.CloudUpload,
                    size = ButtonSize.Sm,
                    enabled = state.serverUrl.isNotBlank() && state.projectId.isNotBlank() && state.findings.isNotEmpty(),
                )
            },
        )
        HDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            Callout(
                "Intellectual property guarantee: Raw source code is never transmitted. Only metadata (tool, rule, file path, line number) is synchronized with the CodeShield API.",
                CalloutKind.Info,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.lg),
            ) {
                // Colonne 1 : Paramètres du serveur
                Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                    Panel(
                        title = "Platform credentials",
                        subtitle = "Configure your CodeShield API connection",
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                            Text(strings.syncServerUrl, color = Shield.TextDim, fontSize = TypeScale.label, fontWeight = FontWeight.Medium)
                            ShieldTextField(
                                value = state.serverUrl,
                                onValueChange = onServerChange,
                                placeholder = "http://localhost:8000",
                                mono = true,
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                            Text(strings.syncProjectId, color = Shield.TextDim, fontSize = TypeScale.label, fontWeight = FontWeight.Medium)
                            ShieldTextField(
                                value = state.projectId,
                                onValueChange = onProjectIdChange,
                                placeholder = "e.g. 1 or project-uuid",
                                mono = true,
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                            Text(strings.syncApiToken, color = Shield.TextDim, fontSize = TypeScale.label, fontWeight = FontWeight.Medium)
                            ShieldTextField(
                                value = state.apiToken,
                                onValueChange = onTokenChange,
                                placeholder = "csk_live_...",
                                mono = true,
                                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                                trailing = {
                                    Text(
                                        if (showToken) "Hide" else "Show",
                                        color = Shield.Accent,
                                        fontSize = TypeScale.caption,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.clip(Radius.sm).clickable { showToken = !showToken }.padding(4.dp),
                                    )
                                },
                            )
                        }

                        HDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("Pending payload", color = Shield.Text, fontSize = TypeScale.body, fontWeight = FontWeight.Medium)
                                Text("${state.findings.size} finding entries ready for differential resolution", color = Shield.TextMute, fontSize = TypeScale.caption)
                            }
                            ShieldButton(
                                strings.syncButton,
                                onClick = onSync,
                                variant = ButtonVariant.Primary,
                                leadingIcon = Icons.Outlined.CloudSync,
                                enabled = state.serverUrl.isNotBlank() && state.projectId.isNotBlank() && state.findings.isNotEmpty(),
                            )
                        }
                    }
                }

                // Colonne 2 : Résultat de synchronisation
                Column(Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                    Panel(
                        title = strings.syncLastSync,
                        subtitle = "Differential resolution status",
                    ) {
                        if (state.syncMessage != null) {
                            Callout(state.syncMessage, CalloutKind.Success)
                        } else {
                            EmptyState(
                                icon = Icons.Outlined.CloudSync,
                                title = strings.syncNoSyncTitle,
                                hint = strings.syncNoSyncHint,
                            )
                        }
                    }
                }
            }
        }
    }
}
