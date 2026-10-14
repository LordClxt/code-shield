package service

import (
	"context"
	"errors"
	"slices"

	"codeshield/backend/internal/domain"
	"codeshield/backend/internal/repository"
)

// IssueService : logique commune à l'API REST /issues et au serveur MCP.
type IssueService struct{ Q *repository.Queries }

type IssueQuery struct {
	ProjectID  *int64
	Statuses   []domain.Status
	Severities []domain.Severity
	Tools      []domain.Tool
	Categories []domain.Category
	FilePrefix string
	Cursor     string
	Limit      int
}

type IssuePage struct {
	Items      []repository.Issue `json:"items"`
	NextCursor *string            `json:"next_cursor"`
}

type IssueDetail struct {
	repository.Issue
	FixContext FixContext `json:"fix_context"`
}

var ErrBadCursor = errors.New("invalid cursor")

func (s IssueService) List(ctx context.Context, p Principal, in IssueQuery) (IssuePage, error) {
	f := repository.IssueFilter{
		OwnerID: p.User.ID, ProjectID: in.ProjectID, Statuses: in.Statuses, Severities: in.Severities,
		Tools: in.Tools, Categories: in.Categories, FilePrefix: in.FilePrefix, Limit: in.Limit,
	}
	if f.Limit <= 0 || f.Limit > 200 {
		f.Limit = 50
	}
	if r := p.ProjectRestriction(); r != nil {
		if f.ProjectID != nil && *f.ProjectID != *r {
			return IssuePage{Items: []repository.Issue{}}, nil
		}
		f.ProjectID = r
	}
	if in.Cursor != "" {
		rank, id, err := DecodeCursor(in.Cursor)
		if err != nil {
			return IssuePage{}, ErrBadCursor
		}
		f.AfterRank, f.AfterID = &rank, &id
	}
	// On demande un élément de plus pour savoir s'il existe une page suivante.
	want := f.Limit
	f.Limit++
	items, err := s.Q.ListIssues(ctx, f)
	if err != nil {
		return IssuePage{}, err
	}
	page := IssuePage{Items: items}
	if len(items) > want {
		page.Items = items[:want]
		last := page.Items[want-1]
		c := EncodeCursor(last.Severity, last.ID)
		page.NextCursor = &c
	}
	if page.Items == nil {
		page.Items = []repository.Issue{}
	}
	return page, nil
}

func (s IssueService) Get(ctx context.Context, p Principal, id int64) (IssueDetail, error) {
	is, err := s.Q.GetIssue(ctx, p.User.ID, id)
	if err != nil {
		return IssueDetail{}, err
	}
	if !p.CanAccessProject(is.ProjectID) {
		return IssueDetail{}, repository.ErrNotFound
	}
	return IssueDetail{Issue: is, FixContext: BuildFixContext(&is.Vulnerability)}, nil
}

// SetStatus applique une transition autorisée ; retourne les ids modifiés.
func (s IssueService) SetStatus(ctx context.Context, p Principal, ids []int64, to domain.Status, note *string) ([]int64, error) {
	if err := CheckTransition(to, note, p.Key == nil); err != nil {
		return nil, err
	}
	slices.Sort(ids)
	ids = slices.Compact(ids)
	return s.Q.SetIssuesStatus(ctx, p.User.ID, p.ProjectRestriction(), ids,
		repository.StatusChange{Status: to, Note: note, By: p.Actor()})
}
