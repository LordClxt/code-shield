// Package service contient la logique métier (score, synchro, remédiation, auth).
package service

import (
	"math"

	"codeshield/backend/internal/domain"
)

// Pondérations alignées avec frontend (score.ts) et desktop (ScoreCalculator).
var severityWeights = map[domain.Severity]float64{
	domain.SeverityCritical: 10,
	domain.SeverityHigh:     5,
	domain.SeverityMedium:   2,
	domain.SeverityLow:      0.5,
}

// ComputeScore retourne (score 0..100, grade A..F).
// Seuils : A>=90, B>=75, C>=60, D>=40, sinon F.
func ComputeScore(open map[domain.Severity]int) (int, string) {
	penalty := 0.0
	for sev, w := range severityWeights {
		penalty += w * float64(open[sev])
	}
	// round-half-even, comme round() en Python (parité v1).
	score := max(0, 100-int(math.RoundToEven(penalty)))
	switch {
	case score >= 90:
		return score, "A"
	case score >= 75:
		return score, "B"
	case score >= 60:
		return score, "C"
	case score >= 40:
		return score, "D"
	default:
		return score, "F"
	}
}
