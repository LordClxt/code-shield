package codeshield.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import codeshield.llm.AppTheme
import codeshield.llm.LlmModels
import codeshield.llm.LlmProvider
import codeshield.llm.LlmSettings
import codeshield.ui.design.ButtonSize
import codeshield.ui.design.ButtonVariant
import codeshield.ui.design.Callout
import codeshield.ui.design.CalloutKind
import codeshield.ui.design.HDivider
import codeshield.ui.design.PageHeader
import codeshield.ui.design.Panel
import codeshield.ui.design.SettingRow
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
 * Section Settings : configuration du moteur LLM (fournisseurs, clé API locale,
 * sélection de modèle, budget de tokens et test de connexion) et préférences visuelles.
 */
@Composable
fun SettingsSection(
    settings: LlmSettings,
    onChange: (LlmSettings) -> Unit,
    onTest: () -> Unit,
    testing: Boolean = false,
    testMessage: String? = null,
    testOk: Boolean? = null,
    onFetchModels: () -> Unit = {},
    modelsLoading: Boolean = false,
    fetchedModels: List<String> = emptyList(),
    modelsMessage: String? = null,
) {
    val strings = codeshield.ui.i18n.LocalStrings.current
    var providerOpen by remember { mutableStateOf(false) }
    var modelOpen by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    val models = (fetchedModels + LlmModels.defaultsFor(settings.provider)).distinct()

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = strings.settingsTitle,
            subtitle = strings.settingsSubtitle,
            actions = {
                ShieldButton(
                    if (testing) strings.settingsTesting else strings.settingsTestLlm,
                    onClick = onTest,
                    leadingIcon = Icons.Outlined.NetworkCheck,
                    size = ButtonSize.Sm,
                    enabled = !testing && settings.provider != LlmProvider.NONE,
                    loading = testing,
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
                strings.settingsZeroCodeNotice,
                CalloutKind.Info,
            )

            // ── Panneau LLM Assistant ───────────────────────────────────────
            Panel(
                title = strings.settingsAiTitle,
                subtitle = strings.settingsAiSubtitle,
                actions = {
                    StatusDot(if (settings.enabled && settings.provider != LlmProvider.NONE) Shield.Success else Shield.TextMute)
                    Text(
                        if (settings.enabled && settings.provider != LlmProvider.NONE) strings.settingsAiActive else strings.settingsAiDisabled,
                        color = Shield.TextDim,
                        fontSize = TypeScale.caption,
                    )
                },
            ) {
                SettingRow(
                    label = strings.settingsAiEnableLabel,
                    description = strings.settingsAiEnableDesc,
                ) {
                    ShieldSwitch(
                        checked = settings.enabled,
                        onCheckedChange = { onChange(settings.copy(enabled = it)) },
                    )
                }

                HDivider()

                SettingRow(
                    label = strings.settingsProviderLabel,
                    description = strings.settingsProviderDesc,
                ) {
                    Box {
                        SelectorButton(
                            text = settings.provider.label,
                            onClick = { providerOpen = true },
                        )
                        DropdownMenu(expanded = providerOpen, onDismissRequest = { providerOpen = false }) {
                            for (p in LlmProvider.entries) {
                                DropdownMenuItem(
                                    text = { Text(p.label, fontSize = TypeScale.body) },
                                    onClick = {
                                        val d = LlmModels.defaultModel(p)
                                        onChange(
                                            settings.copy(
                                                provider = p,
                                                model = if (settings.model.isBlank() || settings.provider != p) d else settings.model,
                                                baseUrl = if (settings.baseUrl.isBlank()) LlmModels.defaultBaseUrl(p) else settings.baseUrl,
                                            ),
                                        )
                                        providerOpen = false
                                    },
                                )
                            }
                        }
                    }
                }

                if (LlmModels.needsKey(settings.provider)) {
                    HDivider()
                    SettingRow(
                        label = strings.settingsApiKeyLabel,
                        description = strings.settingsApiKeyDesc(settings.provider.label),
                    ) {
                        ShieldTextField(
                            value = settings.apiKey,
                            onValueChange = { onChange(settings.copy(apiKey = it.trim())) },
                            placeholder = "sk-...",
                            mono = true,
                            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailing = {
                                Text(
                                    if (showKey) "Hide" else "Show",
                                    color = Shield.Accent,
                                    fontSize = TypeScale.caption,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clip(Radius.sm).clickable { showKey = !showKey }.padding(4.dp),
                                )
                            },
                        )
                    }
                } else if (settings.provider == LlmProvider.OLLAMA) {
                    HDivider()
                    SettingRow(
                        label = "Local Ollama Engine",
                        description = "No API key required for locally hosted models.",
                    ) {
                        TintBadge("Offline / Private", Shield.Success)
                    }
                }

                if (settings.provider == LlmProvider.OLLAMA || settings.provider == LlmProvider.CUSTOM) {
                    HDivider()
                    SettingRow(
                        label = "Base URL",
                        description = "Endpoint address for local daemon or custom gateway.",
                    ) {
                        ShieldTextField(
                            value = settings.baseUrl,
                            onValueChange = { onChange(settings.copy(baseUrl = it.trim())) },
                            placeholder = "http://localhost:11434",
                            mono = true,
                        )
                    }
                }

                HDivider()

                SettingRow(
                    label = strings.settingsModelLabel,
                    description = strings.settingsModelDesc,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            SelectorButton(
                                text = settings.model.ifBlank { "Select model…" },
                                onClick = { modelOpen = true },
                                enabled = models.isNotEmpty(),
                            )
                            DropdownMenu(expanded = modelOpen, onDismissRequest = { modelOpen = false }) {
                                for (m in models) {
                                    val isFetched = m in fetchedModels
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                                            ) {
                                                Text(m, fontSize = TypeScale.body)
                                                if (isFetched) {
                                                    Icon(Icons.Outlined.Check, "Available", tint = Shield.Success, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        },
                                        onClick = {
                                            onChange(settings.copy(model = m))
                                            modelOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        ShieldButton(
                            if (modelsLoading) strings.settingsFetchingModels else strings.settingsFetchModels,
                            onClick = onFetchModels,
                            leadingIcon = Icons.Outlined.Refresh,
                            size = ButtonSize.Sm,
                            enabled = !modelsLoading && settings.provider != LlmProvider.NONE,
                            loading = modelsLoading,
                        )
                    }
                }

                modelsMessage?.let {
                    Callout(it, CalloutKind.Error)
                }

                HDivider()

                SettingRow(
                    label = strings.settingsMaxTokensLabel,
                    description = strings.settingsMaxTokensDesc,
                ) {
                    var maxTokensText by remember(settings.maxTokens) { mutableStateOf(settings.maxTokens.toString()) }
                    ShieldTextField(
                        value = maxTokensText,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(5)
                            maxTokensText = digits
                            digits.toIntOrNull()?.let { v -> onChange(settings.copy(maxTokens = v)) }
                        },
                        placeholder = "4096",
                        mono = true,
                    )
                }

                HDivider()

                // Test de connexion
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(strings.settingsTestLlm, color = Shield.Text, fontSize = TypeScale.body, fontWeight = FontWeight.Medium)
                        Text(strings.settingsZeroCodeNotice.take(65) + "…", color = Shield.TextMute, fontSize = TypeScale.caption)
                    }
                    ShieldButton(
                        if (testing) strings.settingsTesting else strings.settingsTestLlm,
                        onClick = onTest,
                        leadingIcon = Icons.Outlined.NetworkCheck,
                        size = ButtonSize.Sm,
                        enabled = !testing && settings.provider != LlmProvider.NONE,
                        loading = testing,
                    )
                }

                testMessage?.let { msg ->
                    Callout(msg, if (testOk == true) CalloutKind.Success else CalloutKind.Error)
                }
            }

            // ── Panneau Apparence & Langue ──────────────────────────────────
            Panel(
                title = strings.settingsPreferencesTitle,
                subtitle = strings.settingsPreferencesSubtitle,
            ) {
                // Option Langue
                SettingRow(
                    label = strings.settingsLanguageLabel,
                    description = strings.settingsLanguageDesc,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        for (lang in codeshield.ui.i18n.AppLanguage.entries) {
                            val active = settings.language == lang
                            ShieldButton(
                                text = "${lang.flag} ${lang.nativeLabel}",
                                onClick = { onChange(settings.copy(language = lang)) },
                                variant = if (active) ButtonVariant.Primary else ButtonVariant.Secondary,
                                size = ButtonSize.Sm,
                            )
                        }
                    }
                }

                HDivider()

                // Option Thème
                SettingRow(
                    label = strings.settingsThemeLabel,
                    description = strings.settingsThemeDesc,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        for (t in AppTheme.entries) {
                            val active = settings.theme == t
                            val label = when (t) {
                                AppTheme.DARK -> strings.settingsThemeDark
                                AppTheme.LIGHT -> strings.settingsThemeLight
                            }
                            ShieldButton(
                                text = label,
                                onClick = { onChange(settings.copy(theme = t)) },
                                variant = if (active) ButtonVariant.Primary else ButtonVariant.Secondary,
                                size = ButtonSize.Sm,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectorButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.md)
            .background(Shield.Surface)
            .border(1.dp, Shield.Border, Radius.md)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.md, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = if (enabled) Shield.Text else Shield.TextMute,
            fontSize = TypeScale.body,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Outlined.ExpandMore, null, tint = Shield.TextMute, modifier = Modifier.size(16.dp))
    }
}
