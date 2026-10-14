package test

import (
	"context"
	"encoding/json"
	"net/http"
	"slices"
	"strings"
	"testing"

	sdk "github.com/modelcontextprotocol/go-sdk/mcp"
)

type bearer struct {
	token string
	base  http.RoundTripper
}

func (b bearer) RoundTrip(r *http.Request) (*http.Response, error) {
	r = r.Clone(r.Context())
	r.Header.Set("Authorization", "Bearer "+b.token)
	return b.base.RoundTrip(r)
}

func mcpSession(t *testing.T, c *client, token string) (*sdk.ClientSession, error) {
	t.Helper()
	cl := sdk.NewClient(&sdk.Implementation{Name: "test", Version: "0"}, nil)
	return cl.Connect(context.Background(), &sdk.StreamableClientTransport{
		Endpoint:   c.srv.URL + "/mcp",
		HTTPClient: &http.Client{Transport: bearer{token, http.DefaultTransport}},
	}, nil)
}

func callJSON(t *testing.T, s *sdk.ClientSession, name string, args map[string]any) (map[string]any, bool) {
	t.Helper()
	res, err := s.CallTool(context.Background(), &sdk.CallToolParams{Name: name, Arguments: args})
	if err != nil {
		t.Fatalf("%s: %v", name, err)
	}
	if res.IsError {
		return map[string]any{"error": res.Content[0].(*sdk.TextContent).Text}, false
	}
	b, _ := json.Marshal(res.StructuredContent)
	var m map[string]any
	_ = json.Unmarshal(b, &m)
	return m, true
}

func TestMCPAgentWorkflow(t *testing.T) {
	c := newClient(t)
	tok := c.login("claude@example.com")
	pid := c.project(tok, "shop")
	c.sync(tok, pid,
		finding("javascript.express.security.audit.xss.direct-response-write", "src/routes/user.js", 40, "HIGH", map[string]any{"cwe": []string{"CWE-79"}}),
		finding("aws-access-token", ".env.example", 2, "CRITICAL", map[string]any{"tool": "GITLEAKS"}),
	)

	// Sans jeton : refusé.
	if _, err := mcpSession(t, c, ""); err == nil {
		t.Fatal("unauthenticated MCP session accepted")
	}
	// Clé sans issues:read : refusée.
	if _, err := mcpSession(t, c, c.key(tok, []string{"sync:write"}, nil)); err == nil {
		t.Fatal("sync-only key accepted on MCP")
	}

	readOnly, err := mcpSession(t, c, c.key(tok, []string{"issues:read"}, nil))
	if err != nil {
		t.Fatal(err)
	}
	defer readOnly.Close()

	tools, err := readOnly.ListTools(context.Background(), nil)
	if err != nil {
		t.Fatal(err)
	}
	var names []string
	for _, tl := range tools.Tools {
		names = append(names, tl.Name)
	}
	for _, want := range []string{"list_projects", "list_issues", "get_issue", "mark_issue_fixed", "ignore_issue", "set_issue_status", "get_project_score"} {
		if !slices.Contains(names, want) {
			t.Errorf("missing tool %s in %v", want, names)
		}
	}

	projects, _ := callJSON(t, readOnly, "list_projects", nil)
	if ps := projects["projects"].([]any); len(ps) != 1 || ps[0].(map[string]any)["grade"] != "B" { // 1 CRITICAL + 1 HIGH = 85
		t.Fatalf("list_projects: %v", projects)
	}

	list, _ := callJSON(t, readOnly, "list_issues", map[string]any{"project_id": pid})
	issues := list["issues"].([]any)
	first := issues[0].(map[string]any)
	if len(issues) != 2 || first["severity"] != "CRITICAL" || first["location"] != ".env.example:2" {
		t.Fatalf("list_issues: %v", list)
	}
	id := first["id"]

	detail, _ := callJSON(t, readOnly, "get_issue", map[string]any{"id": id})
	guidance := detail["fix_context"].(map[string]any)["guidance"].(map[string]any)
	if !strings.Contains(guidance["category"].(string), "AWS") {
		t.Fatalf("get_issue guidance: %v", guidance)
	}

	// Écriture refusée sans issues:write.
	if res, ok := callJSON(t, readOnly, "mark_issue_fixed", map[string]any{"id": id, "note": "moved to env"}); ok {
		t.Fatalf("read-only key could write: %v", res)
	}

	rw, err := mcpSession(t, c, c.key(tok, []string{"issues:read", "issues:write"}, nil))
	if err != nil {
		t.Fatal(err)
	}
	defer rw.Close()
	res, ok := callJSON(t, rw, "mark_issue_fixed", map[string]any{"id": id, "note": "secret moved to AWS_ACCESS_KEY_ID env var"})
	if !ok || res["status"] != "FIXED_PENDING_VERIFY" {
		t.Fatalf("mark_issue_fixed: %v", res)
	}
	if res, ok := callJSON(t, rw, "ignore_issue", map[string]any{"id": issues[1].(map[string]any)["id"], "justification": "short"}); ok {
		t.Fatalf("ignore without justification accepted: %v", res)
	}

	// Le scan suivant ne voit plus le secret -> RESOLVED.
	c.sync(tok, pid, finding("javascript.express.security.audit.xss.direct-response-write", "src/routes/user.js", 40, "HIGH", nil))
	if got := statuses(t, c, tok, pid); got["aws-access-token"] != "RESOLVED" {
		t.Fatalf("after verify scan: %v", got)
	}

	// Ressource et prompt.
	rr, err := rw.ReadResource(context.Background(), &sdk.ReadResourceParams{URI: "codeshield://issue/" + jsonNum(id)})
	if err != nil || !strings.Contains(rr.Contents[0].Text, "fix_context") {
		t.Fatalf("read resource: %v %v", err, rr)
	}
	pr, err := rw.GetPrompt(context.Background(), &sdk.GetPromptParams{Name: "fix_issues", Arguments: map[string]string{"project_id": "1", "severity": "HIGH"}})
	if err != nil || !strings.Contains(pr.Messages[0].Content.(*sdk.TextContent).Text, "severity=CRITICAL,HIGH") {
		t.Fatalf("prompt: %v %v", err, pr)
	}
}

func jsonNum(v any) string {
	b, _ := json.Marshal(v)
	return string(b)
}
