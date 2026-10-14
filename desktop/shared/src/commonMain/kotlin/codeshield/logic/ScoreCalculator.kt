package codeshield.logic

import codeshield.model.Severity

/** Résultat du score : 0..100 + grade A..F. */
data class ScoreResult(val score: Int, val grade: String)

/**
 * Calculateur de score, miroir exact de backend-go/internal/service/score.go et frontend/score.ts.
 * Poids : CRITICAL=10, HIGH=5, MEDIUM=2, LOW=0.5. Seuils : A>=90, B>=75, C>=60, D>=40, sinon F.
 */
object ScoreCalculator {
    val weights: Map<Severity, Double> = mapOf(
        Severity.CRITICAL to 10.0,
        Severity.HIGH to 5.0,
        Severity.MEDIUM to 2.0,
        Severity.LOW to 0.5,
    )

    fun computeScore(openCounts: Map<Severity, Int>): ScoreResult {
        var penalty = 0.0
        for ((severity, weight) in weights) {
            penalty += weight * (openCounts[severity] ?: 0)
        }
        // Kotlin round() = half-up (contrairement au banker's rounding Python) :
        // écart max de 1 point sur les cas x.5 (ex. 1 LOW -> 99 ici vs 100 côté backend).
        // Les seuils de grade restent identiques ; les tests documentent ce cas.
        val score = (100 - kotlin.math.round(penalty).toInt()).coerceAtLeast(0)
        val grade = when {
            score >= 90 -> "A"
            score >= 75 -> "B"
            score >= 60 -> "C"
            score >= 40 -> "D"
            else -> "F"
        }
        return ScoreResult(score, grade)
    }
}
