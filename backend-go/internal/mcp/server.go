// Package mcp expose CodeShield aux agents IA (Claude Code…) via le Model
// Context Protocol, transport Streamable HTTP sur /mcp.
//
//	claude mcp add --transport http codeshield https://<host>/mcp \
//	  --header "Authorization: Bearer csk_..."
//
// L'agent lit les issues et leur guidance, corrige le code EN LOCAL, puis
// marque l'issue FIXED_PENDING_VERIFY : le prochain scan desktop confirme
// (RESOLVED) ou infirme (REOPENED). Aucun code source ne transite ici.
package mcp

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strconv"
	"strings"

	"github.com/modelcontextprotocol/go-sdk/auth"
	sdk "github.com/modelcontextprotocol/go-sdk/mcp"

	"codeshield/backend/internal/domain"
	"codeshield/backend/internal/repository"
	"codeshield/backend/internal/service"
)

const principalKey = "principal"

type Server struct {
	Q       *repository.Queries
	Auth    service.Authenticator
	Version string
}

func (s *Server) issues() service.IssueService { return service.IssueService{Q: s.Q} }

// Handler retourne le handler HTTP authentifié (clé `csk_` ou JWT).
func (s *Server) Handler() http.Handler {
	server := s.newServer()
	h := sdk.NewStreamableHTTPHandler(func(*http.Request) *sdk.Server { return server }, nil)
	verify := func(ctx context.Context, token string, _ *http.Request) (*auth.TokenInfo, error) {
		p, err := s.Auth.Authenticate(ctx, token, true)
		if err != nil {
			return nil, fmt.Errorf("%w: %v", auth.ErrInvalidToken, err)
		}
		if !p.Has(domain.ScopeIssuesRead) {
			return nil, fmt.Errorf("%w: API key lacks scope issues:read", auth.ErrInvalidToken)
		}
		scopes := []string{}
		for _, sc := range domain.Scopes {
			if p.Has(sc) {
				scopes = append(scopes, string(sc))
			}
		}
		// UserID : empêche le détournement de session MCP par un autre compte.
		return &auth.TokenInfo{
			UserID: strconv.FormatInt(p.User.ID, 10), Scopes: scopes,
			Extra: map[string]any{principalKey: p},
		}, nil
	}
	return auth.RequireBearerToken(verify, &auth.RequireBearerTokenOptions{AllowMissingExpiration: true})(h)
}

func principal(extra *sdk.RequestExtra) (service.Principal, error) {
	if extra == nil || extra.TokenInfo == nil {
		return service.Principal{}, errors.New("unauthenticated")
	}
	p, ok := extra.TokenInfo.Extra[principalKey].(service.Principal)
	if !ok {
		return service.Principal{}, errors.New("unauthenticated")
	}
	return p, nil
}

// ---------------------------------------------------------------- tools I/O

type ListProjectsIn struct{}

type ProjectSummary struct {
	ID        int64                   `json:"id"`
	Name      string                  `json:"name"`
	RepoURL   *string                 `json:"repo_url,omitempty"`
	Score     int                     `json:"score"`
	Grade     string                  `json:"grade"`
	OpenTotal int                     `json:"open_total"`
	Open      map[domain.Severity]int `json:"open_counts"`
}

type ListProjectsOut struct {
	Projects []ProjectSummary `json:"projects"`
}

type ListIssuesIn struct {
	ProjectID  int64    `json:"project_id,omitempty" jsonschema:"restrict to one project id"`
	Severity   []string `json:"severity,omitempty" jsonschema:"filter by severity: CRITICAL, HIGH, MEDIUM, LOW"`
	Status     []string `json:"status,omitempty" jsonschema:"filter by status; defaults to active issues (OPEN, IN_PROGRESS, REOPENED)"`
	Tool       []string `json:"tool,omitempty" jsonschema:"filter by scanner: SEMGREP, GITLEAKS, DEPENDENCY_CHECK, TRIVY, BANDIT, OSV_SCANNER, CHECKOV"`
	Category   []string `json:"category,omitempty" jsonschema:"filter by category: SAST, SECRET, SCA, IAC"`
	FilePrefix string   `json:"file_prefix,omitempty" jsonschema:"only issues whose file path starts with this prefix"`
	Cursor     string   `json:"cursor,omitempty" jsonschema:"next_cursor from a previous call"`
	Limit      int      `json:"limit,omitempty" jsonschema:"page size, 1-200 (default 50)"`
}

type IssueSummary struct {
	ID          int64           `json:"id"`
	ProjectID   int64           `json:"project_id"`
	ProjectName string          `json:"project_name"`
	Severity    domain.Severity `json:"severity"`
	Status      domain.Status   `json:"status"`
	Tool        domain.Tool     `json:"tool"`
	Category    domain.Category `json:"category"`
	RuleID      string          `json:"rule_id"`
	Title       string          `json:"title"`
	Location    string          `json:"location" jsonschema:"path:line or path:start-end"`
	CWE         []string        `json:"cwe,omitempty"`
}

type ListIssuesOut struct {
	Issues     []IssueSummary `json:"issues"`
	NextCursor string         `json:"next_cursor,omitempty"`
}

type GetIssueIn struct {
	ID int64 `json:"id" jsonschema:"issue id"`
}

type MarkFixedIn struct {
	ID   int64  `json:"id" jsonschema:"issue id"`
	Note string `json:"note" jsonschema:"what was changed to fix the issue (files, approach)"`
}

type IgnoreIn struct {
	ID            int64  `json:"id" jsonschema:"issue id"`
	Justification string `json:"justification" jsonschema:"why this is a false positive or an accepted risk (min 10 chars)"`
}

type SetStatusIn struct {
	ID     int64  `json:"id" jsonschema:"issue id"`
	Status string `json:"status" jsonschema:"OPEN or IN_PROGRESS"`
	Note   string `json:"note,omitempty"`
}

type StatusOut struct {
	ID     int64         `json:"id"`
	Status domain.Status `json:"status"`
	Next   string        `json:"next"`
}

type ProjectScoreIn struct {
	ProjectID int64 `json:"project_id"`
}

func location(v *domain.Vulnerability) string {
	if v.EndLine != nil && *v.EndLine != v.LineNumber {
		return fmt.Sprintf("%s:%d-%d", v.FilePath, v.LineNumber, *v.EndLine)
	}
	return fmt.Sprintf("%s:%d", v.FilePath, v.LineNumber)
}

func enumList[T ~string](raw []string, valid func(T) bool, field string) ([]T, error) {
	var out []T
	for _, r := range raw {
		v := T(strings.ToUpper(strings.TrimSpace(r)))
		if !valid(v) {
			return nil, fmt.Errorf("invalid %s %q", field, r)
		}
		out = append(out, v)
	}
	return out, nil
}

func (s *Server) newServer() *sdk.Server {
	srv := sdk.NewServer(&sdk.Implementation{Name: "codeshield", Title: "CodeShield", Version: s.Version}, &sdk.ServerOptions{
		Instructions: "CodeShield centralise les failles détectées localement (SAST, secrets, dépendances, IaC). " +
			"Workflow : list_issues -> get_issue (fix_context) -> corriger le fichier dans le dépôt local -> tests -> mark_issue_fixed. " +
			"Le serveur ne contient jamais le code source : lisez les fichiers localement. " +
			"Ne marquez jamais une issue corrigée sans avoir modifié le code ; utilisez ignore_issue avec justification pour un faux positif.",
	})

	sdk.AddTool(srv, &sdk.Tool{
		Name: "list_projects", Description: "List the user's CodeShield projects with their security score (A-F) and open issue counts.",
		Annotations: &sdk.ToolAnnotations{ReadOnlyHint: true},
	}, s.listProjects)

	sdk.AddTool(srv, &sdk.Tool{
		Name: "list_issues",
		Description: "List security issues, most severe first. Defaults to active issues (OPEN, IN_PROGRESS, REOPENED). " +
			"Paginate with next_cursor. Each item gives file:line so you can open the file in the local repository.",
		Annotations: &sdk.ToolAnnotations{ReadOnlyHint: true},
	}, s.listIssues)

	sdk.AddTool(srv, &sdk.Tool{
		Name: "get_issue",
		Description: "Get one issue with its fix_context: exact file and line range, CWE, explanation, remediation steps " +
			"and agent steps. Read the referenced file locally before editing.",
		Annotations: &sdk.ToolAnnotations{ReadOnlyHint: true},
	}, s.getIssue)

	sdk.AddTool(srv, &sdk.Tool{
		Name: "mark_issue_fixed",
		Description: "After you changed the code, mark the issue FIXED_PENDING_VERIFY with a note describing the fix. " +
			"The next CodeShield scan confirms it (RESOLVED) or reopens it (REOPENED). Requires scope issues:write.",
		Annotations: &sdk.ToolAnnotations{IdempotentHint: true},
	}, s.markFixed)

	sdk.AddTool(srv, &sdk.Tool{
		Name: "ignore_issue",
		Description: "Mark an issue IGNORED (false positive or accepted risk). A precise justification is mandatory. " +
			"Ignored issues no longer count in the score. Requires scope issues:write.",
		Annotations: &sdk.ToolAnnotations{IdempotentHint: true},
	}, s.ignoreIssue)

	sdk.AddTool(srv, &sdk.Tool{
		Name:        "set_issue_status",
		Description: "Set an issue to IN_PROGRESS (you are working on it) or back to OPEN. Requires scope issues:write.",
		Annotations: &sdk.ToolAnnotations{IdempotentHint: true},
	}, s.setStatus)

	sdk.AddTool(srv, &sdk.Tool{
		Name: "get_project_score", Description: "Get a project's current security score, grade and open counts by severity.",
		Annotations: &sdk.ToolAnnotations{ReadOnlyHint: true},
	}, s.projectScore)

	srv.AddResourceTemplate(&sdk.ResourceTemplate{
		Name: "issue", URITemplate: "codeshield://issue/{id}", MIMEType: "application/json",
		Description: "A CodeShield issue with its fix_context.",
	}, s.readIssue)

	srv.AddPrompt(&sdk.Prompt{
		Name: "fix_issues", Title: "Fix CodeShield issues",
		Description: "Guided workflow to fix the most severe open issues of a project in the local repository.",
		Arguments: []*sdk.PromptArgument{
			{Name: "project_id", Description: "CodeShield project id", Required: true},
			{Name: "severity", Description: "minimum severity to fix (CRITICAL, HIGH, MEDIUM, LOW); default HIGH"},
			{Name: "max", Description: "maximum number of issues to fix in this session; default 10"},
		},
	}, s.fixIssuesPrompt)
	return srv
}

func (s *Server) listProjects(ctx context.Context, req *sdk.CallToolRequest, _ ListProjectsIn) (*sdk.CallToolResult, ListProjectsOut, error) {
	p, err := principal(req.Extra)
	if err != nil {
		return nil, ListProjectsOut{}, err
	}
	ps, err := s.Q.ListProjects(ctx, p.User.ID)
	if err != nil {
		return nil, ListProjectsOut{}, err
	}
	out := ListProjectsOut{Projects: []ProjectSummary{}}
	for _, pr := range ps {
		if !p.CanAccessProject(pr.ID) {
			continue
		}
		counts, err := s.Q.CountActiveBySeverity(ctx, pr.ID)
		if err != nil {
			return nil, ListProjectsOut{}, err
		}
		sum := ProjectSummary{ID: pr.ID, Name: pr.Name, RepoURL: pr.RepoURL, Open: counts}
		sum.Score, sum.Grade = service.ComputeScore(counts)
		for _, n := range counts {
			sum.OpenTotal += n
		}
		out.Projects = append(out.Projects, sum)
	}
	return nil, out, nil
}

func (s *Server) listIssues(ctx context.Context, req *sdk.CallToolRequest, in ListIssuesIn) (*sdk.CallToolResult, ListIssuesOut, error) {
	p, err := principal(req.Extra)
	if err != nil {
		return nil, ListIssuesOut{}, err
	}
	q := service.IssueQuery{FilePrefix: in.FilePrefix, Cursor: in.Cursor, Limit: in.Limit}
	if in.ProjectID > 0 {
		q.ProjectID = &in.ProjectID
	}
	if q.Severities, err = enumList(in.Severity, domain.Severity.Valid, "severity"); err != nil {
		return nil, ListIssuesOut{}, err
	}
	if q.Statuses, err = enumList(in.Status, domain.Status.Valid, "status"); err != nil {
		return nil, ListIssuesOut{}, err
	}
	if len(q.Statuses) == 0 {
		q.Statuses = []domain.Status{domain.StatusOpen, domain.StatusInProgress, domain.StatusReopened}
	}
	if q.Tools, err = enumList(in.Tool, domain.Tool.Valid, "tool"); err != nil {
		return nil, ListIssuesOut{}, err
	}
	if q.Categories, err = enumList(in.Category, domain.Category.Valid, "category"); err != nil {
		return nil, ListIssuesOut{}, err
	}
	page, err := s.issues().List(ctx, p, q)
	if err != nil {
		return nil, ListIssuesOut{}, err
	}
	out := ListIssuesOut{Issues: []IssueSummary{}}
	for _, is := range page.Items {
		out.Issues = append(out.Issues, IssueSummary{
			ID: is.ID, ProjectID: is.ProjectID, ProjectName: is.ProjectName, Severity: is.Severity,
			Status: is.Status, Tool: is.Tool, Category: is.Category, RuleID: is.RuleID, Title: is.Title,
			Location: location(&is.Vulnerability), CWE: is.CWE,
		})
	}
	if page.NextCursor != nil {
		out.NextCursor = *page.NextCursor
	}
	return nil, out, nil
}

func (s *Server) getIssue(ctx context.Context, req *sdk.CallToolRequest, in GetIssueIn) (*sdk.CallToolResult, service.IssueDetail, error) {
	p, err := principal(req.Extra)
	if err != nil {
		return nil, service.IssueDetail{}, err
	}
	d, err := s.issues().Get(ctx, p, in.ID)
	if errors.Is(err, repository.ErrNotFound) {
		return nil, service.IssueDetail{}, fmt.Errorf("issue %d not found", in.ID)
	}
	return nil, d, err
}

func (s *Server) setIssue(ctx context.Context, extra *sdk.RequestExtra, id int64, to domain.Status, note *string) (StatusOut, error) {
	p, err := principal(extra)
	if err != nil {
		return StatusOut{}, err
	}
	if !p.Has(domain.ScopeIssuesWrite) {
		return StatusOut{}, errors.New("this API key lacks scope issues:write; create a key with issues:write in CodeShield settings")
	}
	updated, err := s.issues().SetStatus(ctx, p, []int64{id}, to, note)
	if err != nil {
		return StatusOut{}, err
	}
	if len(updated) == 0 {
		return StatusOut{}, fmt.Errorf("issue %d not found", id)
	}
	out := StatusOut{ID: id, Status: to}
	switch to {
	case domain.StatusFixedPendingVerify:
		out.Next = "Run a CodeShield scan (desktop app) and sync: the issue becomes RESOLVED if the finding is gone, REOPENED otherwise."
	case domain.StatusIgnored:
		out.Next = "The issue is excluded from the score and will not be reopened by future scans."
	default:
		out.Next = "Status updated."
	}
	return out, nil
}

func (s *Server) markFixed(ctx context.Context, req *sdk.CallToolRequest, in MarkFixedIn) (*sdk.CallToolResult, StatusOut, error) {
	if strings.TrimSpace(in.Note) == "" {
		return nil, StatusOut{}, errors.New("note is required: describe the change you made")
	}
	out, err := s.setIssue(ctx, req.Extra, in.ID, domain.StatusFixedPendingVerify, &in.Note)
	return nil, out, err
}

func (s *Server) ignoreIssue(ctx context.Context, req *sdk.CallToolRequest, in IgnoreIn) (*sdk.CallToolResult, StatusOut, error) {
	out, err := s.setIssue(ctx, req.Extra, in.ID, domain.StatusIgnored, &in.Justification)
	return nil, out, err
}

func (s *Server) setStatus(ctx context.Context, req *sdk.CallToolRequest, in SetStatusIn) (*sdk.CallToolResult, StatusOut, error) {
	to := domain.Status(strings.ToUpper(in.Status))
	if to != domain.StatusOpen && to != domain.StatusInProgress {
		return nil, StatusOut{}, errors.New("status must be OPEN or IN_PROGRESS (use mark_issue_fixed / ignore_issue otherwise)")
	}
	var note *string
	if in.Note != "" {
		note = &in.Note
	}
	out, err := s.setIssue(ctx, req.Extra, in.ID, to, note)
	return nil, out, err
}

func (s *Server) projectScore(ctx context.Context, req *sdk.CallToolRequest, in ProjectScoreIn) (*sdk.CallToolResult, ProjectSummary, error) {
	p, err := principal(req.Extra)
	if err != nil {
		return nil, ProjectSummary{}, err
	}
	pr, err := s.Q.GetOwnedProject(ctx, in.ProjectID, p.User.ID)
	if err != nil || !p.CanAccessProject(pr.ID) {
		return nil, ProjectSummary{}, fmt.Errorf("project %d not found", in.ProjectID)
	}
	counts, err := s.Q.CountActiveBySeverity(ctx, pr.ID)
	if err != nil {
		return nil, ProjectSummary{}, err
	}
	sum := ProjectSummary{ID: pr.ID, Name: pr.Name, RepoURL: pr.RepoURL, Open: counts}
	sum.Score, sum.Grade = service.ComputeScore(counts)
	for _, n := range counts {
		sum.OpenTotal += n
	}
	return nil, sum, nil
}

func (s *Server) readIssue(ctx context.Context, req *sdk.ReadResourceRequest) (*sdk.ReadResourceResult, error) {
	p, err := principal(req.Extra)
	if err != nil {
		return nil, err
	}
	raw := strings.TrimPrefix(req.Params.URI, "codeshield://issue/")
	id, err := strconv.ParseInt(raw, 10, 64)
	if err != nil {
		return nil, sdk.ResourceNotFoundError(req.Params.URI)
	}
	d, err := s.issues().Get(ctx, p, id)
	if errors.Is(err, repository.ErrNotFound) {
		return nil, sdk.ResourceNotFoundError(req.Params.URI)
	}
	if err != nil {
		return nil, err
	}
	b, err := json.MarshalIndent(d, "", "  ")
	if err != nil {
		return nil, err
	}
	return &sdk.ReadResourceResult{Contents: []*sdk.ResourceContents{{URI: req.Params.URI, MIMEType: "application/json", Text: string(b)}}}, nil
}

func (s *Server) fixIssuesPrompt(_ context.Context, req *sdk.GetPromptRequest) (*sdk.GetPromptResult, error) {
	args := req.Params.Arguments
	sev := strings.ToUpper(args["severity"])
	if sev == "" {
		sev = "HIGH"
	}
	max := args["max"]
	if max == "" {
		max = "10"
	}
	sevs := []string{}
	for _, s := range domain.Severities {
		sevs = append(sevs, string(s))
		if string(s) == sev {
			break
		}
	}
	text := fmt.Sprintf(`Tu corriges les failles CodeShield du projet %s dans le dépôt local courant.

1. Appelle list_issues avec project_id=%s et severity=%s (au plus %s issues, les plus graves d'abord).
2. Pour chaque issue :
   a. get_issue pour obtenir fix_context (fichier, plage de lignes, CWE, guidance).
   b. set_issue_status IN_PROGRESS.
   c. Ouvre le fichier localement, comprends le flux de données, applique le correctif MINIMAL conforme à la guidance.
   d. Lance les tests pertinents. S'ils échouent à cause de ta modification, corrige ou annule.
   e. mark_issue_fixed avec une note précise (fichiers modifiés, approche).
   f. Si c'est un faux positif avéré : ignore_issue avec une justification, sans toucher au code.
3. Pour un secret exposé : remplace la valeur par une variable d'environnement ET rappelle à l'utilisateur de révoquer le secret chez le fournisseur.
4. Termine par un résumé : issues corrigées, ignorées, restantes, et rappelle de relancer un scan CodeShield pour confirmer (RESOLVED).

Règles : ne marque jamais une issue corrigée sans changement de code ; ne désactive pas un test ou une règle pour faire passer un scan.`,
		args["project_id"], args["project_id"], strings.Join(sevs, ","), max)
	return &sdk.GetPromptResult{
		Description: "Fix CodeShield issues",
		Messages:    []*sdk.PromptMessage{{Role: "user", Content: &sdk.TextContent{Text: text}}},
	}, nil
}
