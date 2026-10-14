package codeshield.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Mouvement du design system : courts, sans rebond, une seule courbe (la même
 * que la landing web). Un outil qu'on utilise des heures : les transitions
 * guident l'œil, elles ne se font pas remarquer.
 */
object Motion {
    /** Sortie douce : démarre vite, se pose lentement. */
    val Emphasized = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    const val FAST = 140
    const val BASE = 220
    const val SLOW = 480

    fun <T> fast(): FiniteAnimationSpec<T> = tween(FAST, easing = Emphasized)
    fun <T> base(delayMs: Int = 0): FiniteAnimationSpec<T> = tween(BASE, delayMs, Emphasized)
    fun <T> slow(delayMs: Int = 0): FiniteAnimationSpec<T> = tween(SLOW, delayMs, Emphasized)
}

/**
 * Valeur qui part de [from] à la première composition et rejoint [target]
 * (barres, anneaux, compteurs), puis suit ses changements.
 */
@Composable
fun animateOnAppear(target: Float, from: Float = 0f, durationMs: Int = 900, delayMs: Int = 0): State<Float> {
    val anim = remember { Animatable(from) }
    LaunchedEffect(target) { anim.animateTo(target, tween(durationMs, delayMs, Motion.Emphasized)) }
    return anim.asState()
}

/** Barre de défilement verticale discrète, collée au bord droit d'une [BoxScope]. */
@Composable
fun BoxScope.ShieldVScrollbar(state: LazyListState) {
    VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(2.dp))
}

@Composable
fun BoxScope.ShieldVScrollbar(state: ScrollState) {
    VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(2.dp))
}

@Composable
fun BoxScope.ShieldHScrollbar(state: ScrollState) {
    HorizontalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(2.dp))
}
