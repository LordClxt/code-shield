package codeshield.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import codeshield.ui.design.ButtonSize
import codeshield.ui.design.ButtonVariant
import codeshield.ui.design.ShieldButton

/**
 * Wrappers hérités, conservés pour compatibilité : ils délèguent au
 * [ShieldButton] du design system. Préférer ShieldButton dans le nouveau code.
 */
@Composable
fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    modifier: Modifier = Modifier,
) = ShieldButton(text, onClick, modifier, ButtonVariant.Primary, ButtonSize.Md, enabled, loading)

@Composable
fun GhostPillButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    modifier: Modifier = Modifier,
) = ShieldButton(text, onClick, modifier, ButtonVariant.Secondary, ButtonSize.Md, enabled, loading)
