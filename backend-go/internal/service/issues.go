package service

import (
	"encoding/base64"
	"errors"
	"fmt"
	"strconv"
	"strings"

	"codeshield/backend/internal/domain"
)

var ErrForbiddenTransition = errors.New("forbidden status transition")

// CheckTransition : statuts qu'un acteur peut poser à la main.
//   - REOPENED est réservé au moteur de synchro.
//   - RESOLVED n'est posable que par un humain (session JWT) : un agent doit
//     passer par FIXED_PENDING_VERIFY, confirmé par le scan suivant.
//   - IGNORED exige une justification.
func CheckTransition(to domain.Status, note *string, human bool) error {
	switch to {
	case domain.StatusOpen, domain.StatusInProgress, domain.StatusFixedPendingVerify:
		return nil
	case domain.StatusIgnored:
		if note == nil || len(strings.TrimSpace(*note)) < 10 {
			return fmt.Errorf("%w: IGNORED requiert une justification (note >= 10 caractères)", ErrForbiddenTransition)
		}
		return nil
	case domain.StatusResolved:
		if human {
			return nil
		}
		return fmt.Errorf("%w: un agent doit utiliser FIXED_PENDING_VERIFY ; le prochain scan confirmera RESOLVED", ErrForbiddenTransition)
	default:
		return fmt.Errorf("%w: %s est réservé au moteur de synchronisation", ErrForbiddenTransition, to)
	}
}

// Curseur opaque "rang:id" encodé en base64url.
func EncodeCursor(sev domain.Severity, id int64) string {
	return base64.RawURLEncoding.EncodeToString([]byte(fmt.Sprintf("%d:%d", sev.Rank(), id)))
}

func DecodeCursor(c string) (rank int, id int64, err error) {
	raw, err := base64.RawURLEncoding.DecodeString(c)
	if err != nil {
		return 0, 0, errors.New("invalid cursor")
	}
	parts := strings.SplitN(string(raw), ":", 2)
	if len(parts) != 2 {
		return 0, 0, errors.New("invalid cursor")
	}
	if rank, err = strconv.Atoi(parts[0]); err != nil {
		return 0, 0, errors.New("invalid cursor")
	}
	if id, err = strconv.ParseInt(parts[1], 10, 64); err != nil {
		return 0, 0, errors.New("invalid cursor")
	}
	return rank, id, nil
}
