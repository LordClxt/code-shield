package codeshield.logic

import codeshield.model.Severity
import kotlin.test.Test
import kotlin.test.assertEquals

class ScoreCalculatorTest {

    @Test
    fun healthyProjectScores100GradeA() {
        assertEquals(
            ScoreResult(100, "A"),
            ScoreCalculator.computeScore(emptyMap()),
        )
    }

    @Test
    fun singleCriticalCostsTenPoints() {
        assertEquals(
            ScoreResult(90, "A"),
            ScoreCalculator.computeScore(mapOf(Severity.CRITICAL to 1)),
        )
    }

    @Test
    fun gradeThresholds() {
        // 2 HIGH + 1 MEDIUM = 12 -> 88 (B)
        assertEquals("B", ScoreCalculator.computeScore(
            mapOf(Severity.HIGH to 2, Severity.MEDIUM to 1)).grade)
        // 8 HIGH = 40 -> 60 (C)
        assertEquals(ScoreResult(60, "C"), ScoreCalculator.computeScore(mapOf(Severity.HIGH to 8)))
        // 12 HIGH = 60 -> 40 (D)
        assertEquals(ScoreResult(40, "D"), ScoreCalculator.computeScore(mapOf(Severity.HIGH to 12)))
        // 20 CRITICAL -> plancher 0 (F), jamais négatif
        assertEquals(ScoreResult(0, "F"), ScoreCalculator.computeScore(mapOf(Severity.CRITICAL to 20)))
    }

    @Test
    fun singleHighMatchesBackendE2ECase() {
        // Cohérent avec backend test_api_sync : 1 HIGH -> 95/A
        assertEquals(ScoreResult(95, "A"), ScoreCalculator.computeScore(mapOf(Severity.HIGH to 1)))
    }

    @Test
    fun weightsAreAlignedWithBackendAndFrontend() {
        assertEquals(
            mapOf(
                Severity.CRITICAL to 10.0,
                Severity.HIGH to 5.0,
                Severity.MEDIUM to 2.0,
                Severity.LOW to 0.5,
            ),
            ScoreCalculator.weights,
        )
    }
}
