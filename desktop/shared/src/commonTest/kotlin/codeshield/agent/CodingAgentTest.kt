package codeshield.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CodingAgentTest {

    @Test
    fun codexCommandReadsPromptFromStdinAndOpensNetworkOnlyWhenAutoApproved() {
        val safe = CodexAgent.command("/bin/codex", autoApprove = false)
        assertEquals(listOf("/bin/codex", "exec", "--json"), safe.take(3))
        assertEquals("-", safe.last())
        assertEquals("workspace-write", safe[safe.indexOf("--sandbox") + 1])
        assertFalse(safe.any { "network_access" in it })
        assertTrue(CodexAgent.command("/bin/codex", autoApprove = true).contains("sandbox_workspace_write.network_access=true"))
        assertFalse(CodexAgent.keepsInputOpen)
    }

    @Test
    fun codexEventsBecomeLogAndBlockedCommands() {
        assertEquals(
            listOf(AgentEvent.ToolUse("Bash", "bun install")),
            CodexAgent.parse("""{"type":"item.started","item":{"id":"1","type":"command_execution","command":"bun install","status":"in_progress"}}""", false),
        )
        val failed = CodexAgent.parse(
            """{"type":"item.completed","item":{"id":"1","type":"command_execution","command":"bun install","aggregated_output":"network error","exit_code":1,"status":"failed"}}""",
            autoApprove = false,
        )
        assertEquals(AgentEvent.ToolResult("network error", true), failed[0])
        assertTrue(failed[1] is AgentEvent.CommandBlocked)
        // Avec réseau autorisé, un échec reste un échec : rien à proposer.
        assertEquals(1, CodexAgent.parse("""{"type":"item.completed","item":{"type":"command_execution","command":"x","exit_code":2}}""", true).size)
        assertEquals(
            listOf(AgentEvent.ToolUse("Edit", "package.json")),
            CodexAgent.parse("""{"type":"item.completed","item":{"type":"file_change","changes":[{"path":"package.json","kind":"update"}]}}""", false),
        )
        assertEquals(listOf(AgentEvent.Text("Corrigé.")), CodexAgent.parse("""{"type":"item.completed","item":{"type":"agent_message","text":"Corrigé."}}""", false))
        assertEquals(listOf(AgentEvent.Result("", false)), CodexAgent.parse("""{"type":"turn.completed","usage":{}}""", false))
        assertEquals(listOf(AgentEvent.Fatal("quota")), CodexAgent.parse("""{"type":"turn.failed","error":{"message":"quota"}}""", false))
    }

    @Test
    fun openCodeAutoFlagAndRejectedCommands() {
        assertEquals(listOf("/bin/opencode", "run", "--format", "json"), OpenCodeAgent.command("/bin/opencode", false))
        assertTrue("--auto" in OpenCodeAgent.command("/bin/opencode", true))
        val rejected = OpenCodeAgent.parse(
            """{"type":"tool_use","timestamp":1,"sessionID":"s","part":{"type":"tool","tool":"bash","state":{"status":"error","input":{"command":"bun install"},"error":"The user rejected permission to use this specific tool call."}}}""",
            autoApprove = false,
        )
        assertEquals(AgentEvent.ToolUse("Bash", "bun install"), rejected[0])
        assertEquals("bun install", (rejected[1] as AgentEvent.CommandBlocked).command)
        assertEquals(
            listOf(AgentEvent.ToolUse("Edit", "src/a.ts")),
            OpenCodeAgent.parse("""{"type":"tool_use","part":{"tool":"edit","state":{"status":"completed","input":{"filePath":"src/a.ts"},"output":"ok"}}}""", false),
        )
        assertEquals(listOf(AgentEvent.Text("Fini.")), OpenCodeAgent.parse("""{"type":"text","part":{"type":"text","text":"Fini."}}""", false))
        // Erreur réelle observée (compte sans crédit) : bloquante.
        val fatal = OpenCodeAgent.parse(
            """{"type":"error","sessionID":"s","error":{"name":"APIError","data":{"message":"Upstream request failed: Insufficient account funds","statusCode":402}}}""",
            autoApprove = false,
        )
        assertEquals(listOf(AgentEvent.Fatal("Upstream request failed: Insufficient account funds")), fatal)
    }

    @Test
    fun onlyClaudeCodeAsksPermissionLive() {
        assertTrue(CodingAgentKind.CLAUDE_CODE.asksPermission)
        assertFalse(CodingAgentKind.CODEX.asksPermission)
        assertFalse(CodingAgentKind.OPENCODE.asksPermission)
        assertEquals(CodexAgent, CodingAgent.of(CodingAgentKind.CODEX))
    }
}
