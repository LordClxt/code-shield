package codeshield.ui.theme

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import codeshield.llm.AppTheme
import desktop.shared.generated.resources.Res
import desktop.shared.generated.resources.inter_bold
import desktop.shared.generated.resources.inter_medium
import desktop.shared.generated.resources.inter_regular
import desktop.shared.generated.resources.inter_semibold
import desktop.shared.generated.resources.jetbrains_mono_medium
import desktop.shared.generated.resources.jetbrains_mono_regular
import org.jetbrains.compose.resources.Font

/**
 * Design system CodeShield Desktop — sombre, sobre, façon Linear / Vercel :
 * neutres zinc, un seul accent indigo, bordures fines, aucune ombre ni dégradé.
 *
 * Les anciens noms (Card, Panel, Pill, …) restent disponibles comme alias
 * pour ne pas casser les écrans pendant la migration.
 */
object Shield {
    // ── Surfaces ────────────────────────────────────────────────────────────
    val Bg = Color(0xFF09090B)
    val Sidebar = Color(0xFF0C0C0E)
    val Surface = Color(0xFF0F0F12)
    val Raised = Color(0xFF16161A)
    val Hover = Color(0xFF1C1C21)
    val Selected = Color(0xFF212127)
    val Code = Color(0xFF0B0B0D)

    // ── Lignes ──────────────────────────────────────────────────────────────
    val Border = Color(0x14FFFFFF)
    val BorderStrong = Color(0x24FFFFFF)

    // ── Texte ───────────────────────────────────────────────────────────────
    val Text = Color(0xFFEDEDEF)
    val TextDim = Color(0xFFA0A0A8)
    val TextMute = Color(0xFF6B6B73)

    // ── Accent & sémantique ─────────────────────────────────────────────────
    val Accent = Color(0xFF6E79D6)
    val AccentHover = Color(0xFF7F89E0)
    val Success = Color(0xFF3FB97F)
    val Warning = Color(0xFFE5A83B)
    val Error = Color(0xFFE5484D)
    val Info = Color(0xFF4C9AE8)

    // ── Alias hérités (migration) ───────────────────────────────────────────
    val Window = Bg
    val Rail = Sidebar
    val Panel = Surface
    val Card = Raised
    val CardHi = Selected
    val CardHover = Hover
    val Pill = Color(0xFF26262C)
    val BorderHi = BorderStrong
    val Terminal = Code
    val AccentViolet = Accent
    val Folder = Color(0xFF3E3E46)
    val FolderTab = Color(0xFF4A4A54)
    val Doc = Color(0xFFD7D7DC)

    /** Conservé pour compat : plus de dégradé, un aplat accent. */
    val AccentGradient: Brush = Brush.horizontalGradient(listOf(Accent, Accent))

    fun severity(severity: codeshield.model.Severity): Color = when (severity) {
        codeshield.model.Severity.CRITICAL -> Color(0xFFE5484D)
        codeshield.model.Severity.HIGH -> Color(0xFFEC7F3C)
        codeshield.model.Severity.MEDIUM -> Color(0xFFE5A83B)
        codeshield.model.Severity.LOW -> Color(0xFF4C9AE8)
    }

    fun toolBadge(tool: codeshield.model.ToolName): Pair<String, Color> = when (tool) {
        codeshield.model.ToolName.SEMGREP -> "S" to Color(0xFF4C9AE8)
        codeshield.model.ToolName.GITLEAKS -> "G" to Color(0xFFEC7F3C)
        codeshield.model.ToolName.DEPENDENCY_CHECK -> "D" to Color(0xFF9B8AFB)
        codeshield.model.ToolName.TRIVY -> "T" to Color(0xFF2CB5A8)
        codeshield.model.ToolName.BANDIT -> "B" to Color(0xFFD9B43A)
        codeshield.model.ToolName.OSV_SCANNER -> "O" to Color(0xFF3FB97F)
        codeshield.model.ToolName.CHECKOV -> "C" to Color(0xFFD86FA4)
    }

    fun toolLabel(tool: codeshield.model.ToolName): String =
        codeshield.scanner.ScannerEngines.of(tool).label

    fun category(c: codeshield.model.Category): Pair<String, Color> = when (c) {
        codeshield.model.Category.SAST -> "SAST" to Color(0xFF6E79D6)
        codeshield.model.Category.SECRET -> "Secret" to Color(0xFFEC7F3C)
        codeshield.model.Category.SCA -> "Deps" to Color(0xFF9B8AFB)
        codeshield.model.Category.IAC -> "IaC" to Color(0xFF2CB5A8)
    }

    /** Police à chasse fixe (JetBrains Mono) — code, chemins, logs. */
    val Mono: FontFamily
        @Composable @ReadOnlyComposable get() = LocalMonoFont.current
}

/** Échelle d'espacement (dp). */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Échelle de rayons. */
object Radius {
    val sm = RoundedCornerShape(6.dp)
    val md = RoundedCornerShape(8.dp)
    val lg = RoundedCornerShape(12.dp)
    val full = RoundedCornerShape(50)
}

/** Échelle typographique (sp). */
object TypeScale {
    val display = 22.sp
    val title = 15.sp
    val body = 13.sp
    val label = 12.sp
    val caption = 11.sp
}

private val LocalMonoFont = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }

@Composable
fun ShieldTheme(
    @Suppress("UNUSED_PARAMETER") theme: AppTheme = AppTheme.DARK,
    content: @Composable () -> Unit,
) {
    // Dark-first (cf. AGENTS.md) : le thème clair n'est plus appliqué.
    val inter = FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal),
        Font(Res.font.inter_medium, FontWeight.Medium),
        Font(Res.font.inter_semibold, FontWeight.SemiBold),
        Font(Res.font.inter_bold, FontWeight.Bold),
    )
    val mono = FontFamily(
        Font(Res.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(Res.font.jetbrains_mono_medium, FontWeight.Medium),
    )
    val base = TextStyle(fontFamily = inter, fontSize = TypeScale.body, color = Shield.Text)
    val d = Typography()
    val typography = Typography(
        displayLarge = d.displayLarge.copy(fontFamily = inter),
        displayMedium = d.displayMedium.copy(fontFamily = inter),
        displaySmall = d.displaySmall.copy(fontFamily = inter),
        headlineLarge = d.headlineLarge.copy(fontFamily = inter),
        headlineMedium = d.headlineMedium.copy(fontFamily = inter),
        headlineSmall = d.headlineSmall.copy(fontFamily = inter),
        titleLarge = d.titleLarge.copy(fontFamily = inter),
        titleMedium = d.titleMedium.copy(fontFamily = inter),
        titleSmall = d.titleSmall.copy(fontFamily = inter),
        bodyLarge = base,
        bodyMedium = base,
        bodySmall = base.copy(fontSize = TypeScale.label),
        labelLarge = base.copy(fontSize = TypeScale.label, fontWeight = FontWeight.Medium),
        labelMedium = base.copy(fontSize = TypeScale.caption, fontWeight = FontWeight.Medium),
        labelSmall = base.copy(fontSize = TypeScale.caption),
    )
    // Barres de défilement fines, presque invisibles au repos, plus nettes au survol.
    val scrollbar = ScrollbarStyle(
        minimalHeight = 24.dp,
        thickness = 6.dp,
        shape = RoundedCornerShape(3.dp),
        hoverDurationMillis = 200,
        unhoverColor = Color.White.copy(alpha = 0.10f),
        hoverColor = Color.White.copy(alpha = 0.24f),
    )
    CompositionLocalProvider(LocalMonoFont provides mono, LocalScrollbarStyle provides scrollbar) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                background = Shield.Bg,
                surface = Shield.Raised,
                surfaceVariant = Shield.Raised,
                surfaceContainer = Shield.Raised,
                onBackground = Shield.Text,
                onSurface = Shield.Text,
                onSurfaceVariant = Shield.TextDim,
                primary = Shield.Accent,
                onPrimary = Color.White,
                outline = Shield.BorderStrong,
                outlineVariant = Shield.Border,
                error = Shield.Error,
            ),
            typography = typography,
            content = content,
        )
    }
}
