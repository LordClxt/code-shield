package service

import (
	"context"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	"codeshield/backend/internal/domain"
	"codeshield/backend/internal/repository"
)

// SyncResult : contrat de réponse de /api/v1/sync/submit.
type SyncResult struct {
	ScanID     int64                   `json:"scan_id"`
	Created    int                     `json:"created"`
	Touched    int                     `json:"touched"`
	Resolved   int                     `json:"resolved"`
	Reopened   int                     `json:"reopened"`
	OpenTotal  int                     `json:"open_total"`
	OpenCounts map[domain.Severity]int `json:"open_counts"`
	Score      int                     `json:"score"`
	Grade      string                  `json:"grade"`
}

type SyncService struct{ Pool *pgxpool.Pool }

// Submit applique le moteur différentiel dans une transaction unique :
//
//   - identité = empreinte si fournie, sinon (outil, règle, fichier, ligne) ;
//   - match sur faille active      -> touched (position/métadonnées rafraîchies) ;
//   - match sur RESOLVED/FIXED_PENDING_VERIFY -> REOPENED ;
//   - match sur IGNORED            -> reste IGNORED (touched) ;
//   - faille active absente du scan -> RESOLVED ;
//   - finding inconnu              -> OPEN.
func (s SyncService) Submit(ctx context.Context, req domain.SyncSubmitRequest) (SyncResult, error) {
	var res SyncResult
	err := repository.InTx(ctx, s.Pool, func(q *repository.Queries) error {
		r, err := Diff(ctx, q, req, time.Now().UTC())
		res = r
		return err
	})
	return res, err
}

type vulnIndex struct {
	byFP     map[string]*domain.Vulnerability
	byLegacy map[domain.LegacyKey]*domain.Vulnerability
}

func indexVulns(rows []domain.Vulnerability) vulnIndex {
	idx := vulnIndex{byFP: map[string]*domain.Vulnerability{}, byLegacy: map[domain.LegacyKey]*domain.Vulnerability{}}
	for i := range rows {
		v := &rows[i]
		if v.Fingerprint != nil {
			idx.byFP[*v.Fingerprint] = v
			continue
		}
		idx.byLegacy[v.LegacyKey()] = v
	}
	return idx
}

// match : empreinte d'abord ; sinon clé v1 (permet aussi la montée de version
// d'une ligne historique sans empreinte vers une identité par empreinte).
func (idx vulnIndex) match(f *domain.Finding) *domain.Vulnerability {
	if f.Fingerprint != nil {
		if v, ok := idx.byFP[*f.Fingerprint]; ok {
			return v
		}
	}
	return idx.byLegacy[f.LegacyKey()]
}

func applyFinding(v *domain.Vulnerability, f *domain.Finding, scanID int64, now time.Time) {
	v.LastScanID = &scanID
	v.RuleID, v.FilePath, v.LineNumber = f.RuleID, f.FilePath, f.LineNumber
	v.EndLine, v.Column, v.EndColumn = f.EndLine, f.Column, f.EndColumn
	v.Severity, v.Title, v.Message, v.Category = f.Severity, f.Title, f.Message, f.Category
	v.CWE, v.OWASP = nonNil(f.CWE), nonNil(f.OWASP)
	v.RemediationKey, v.Package, v.InstalledVersion, v.FixedVersion = f.RemediationKey, f.Package, f.InstalledVersion, f.FixedVersion
	if f.Fingerprint != nil {
		v.Fingerprint = f.Fingerprint
	}
	v.LastSeen = now
}

func nonNil(xs []string) []string {
	if xs == nil {
		return []string{}
	}
	return xs
}

// Diff est exporté pour les tests (exécuté dans la transaction fournie).
func Diff(ctx context.Context, q *repository.Queries, req domain.SyncSubmitRequest, now time.Time) (SyncResult, error) {
	var res SyncResult
	if err := q.LockProject(ctx, req.ProjectID); err != nil {
		return res, err
	}
	scan, err := q.InsertScan(ctx, req.ProjectID, len(req.Findings), req.CommitSHA, req.Branch)
	if err != nil {
		return res, err
	}
	res.ScanID = scan.ID

	existing, err := q.AllProjectVulns(ctx, req.ProjectID)
	if err != nil {
		return res, err
	}
	idx := indexVulns(existing)
	seen := map[int64]bool{}

	for i := range req.Findings {
		f := &req.Findings[i]
		f.Normalize()
		row := idx.match(f)
		if row != nil && seen[row.ID] {
			continue // doublon dans le même payload
		}
		if row == nil {
			v := &domain.Vulnerability{ProjectID: req.ProjectID, Tool: f.Tool, Status: domain.StatusOpen, FirstSeen: now}
			applyFinding(v, f, scan.ID, now)
			if err := q.InsertVuln(ctx, v); err != nil {
				return res, err
			}
			seen[v.ID] = true
			// Indexe la nouvelle ligne pour dédoublonner le reste du payload.
			if v.Fingerprint != nil {
				idx.byFP[*v.Fingerprint] = v
			} else {
				idx.byLegacy[v.LegacyKey()] = v
			}
			res.Created++
			continue
		}

		seen[row.ID] = true
		// Si l'identité legacy change (code déplacé), on retire l'ancienne entrée.
		delete(idx.byLegacy, row.LegacyKey())
		applyFinding(row, f, scan.ID, now)
		switch row.Status {
		case domain.StatusResolved, domain.StatusFixedPendingVerify:
			row.Status = domain.StatusReopened
			row.ResolvedAt = nil
			res.Reopened++
		default:
			res.Touched++
		}
		if err := q.UpdateVulnFromScan(ctx, row); err != nil {
			return res, err
		}
	}

	var toResolve []int64
	for i := range existing {
		v := &existing[i]
		if !seen[v.ID] && v.Status.Active() {
			toResolve = append(toResolve, v.ID)
		}
	}
	if err := q.ResolveByScan(ctx, toResolve, now); err != nil {
		return res, err
	}
	res.Resolved = len(toResolve)

	counts, err := q.CountActiveBySeverity(ctx, req.ProjectID)
	if err != nil {
		return res, err
	}
	res.OpenCounts = counts
	for _, n := range counts {
		res.OpenTotal += n
	}
	res.Score, res.Grade = ComputeScore(counts)
	return res, nil
}
