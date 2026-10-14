package codeshield.ui.code

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyntaxHighlighterTest {
    private fun kinds(line: String, lang: Lang) =
        SyntaxHighlighter.tokenizeLine(line, lang).tokens.map { line.substring(it.start, it.end) to it.kind }

    @Test
    fun languageIsDetectedFromPath() {
        assertEquals("python", Languages.forPath("app/db.py").id)
        assertEquals("js", Languages.forPath("src/App.tsx").id)
        assertEquals("docker", Languages.forPath("deploy/Dockerfile").id)
        assertEquals("hcl", Languages.forPath("infra/main.tf").id)
        assertEquals("plain", Languages.forPath("README").id)
    }

    @Test
    fun pythonKeywordsStringsCommentsAndNumbers() {
        val k = kinds("def run(x): return eval(\"1+1\") + 42  # unsafe", Languages.PYTHON)
        assertTrue("def" to TokenKind.KEYWORD in k, k.toString())
        assertTrue("return" to TokenKind.KEYWORD in k)
        assertTrue("\"1+1\"" to TokenKind.STRING in k)
        assertTrue("42" to TokenKind.NUMBER in k)
        assertTrue("# unsafe" to TokenKind.COMMENT in k)
    }

    @Test
    fun commentMarkersInsideStringsAreIgnored() {
        val k = kinds("val url = \"http://x\" // real", Languages.KOTLIN)
        assertTrue("\"http://x\"" to TokenKind.STRING in k, k.toString())
        assertTrue("// real" to TokenKind.COMMENT in k)
    }

    @Test
    fun blockCommentsAndMultilineStringsSpanLines() {
        val lines = listOf("a = 1 /* start", "still comment", "end */ b = 2", "s = `multi", "line`")
        val toks = SyntaxHighlighter.tokenizeFile(lines, Languages.JS)
        assertEquals(TokenKind.COMMENT, toks[1].single().kind)
        assertEquals(TokenKind.COMMENT, toks[2].first().kind)
        assertTrue(toks[2].any { it.kind == TokenKind.NUMBER })
        assertEquals(TokenKind.STRING, toks[4].single().kind)
    }

    @Test
    fun escapedQuotesStayInsideStrings() {
        val k = kinds("x = 'it\\'s' + y", Languages.PYTHON)
        assertEquals("'it\\'s'", k.first { it.second == TokenKind.STRING }.first)
    }
}
