package codeshield.ui.code

/**
 * Coloration syntaxique légère (lexer ligne à ligne, sans dépendance).
 * Objectif : lisibilité du visualiseur, pas un parseur exact. Gère mots-clés,
 * chaînes, commentaires (ligne et bloc multi-lignes), nombres et annotations.
 */
enum class TokenKind { PLAIN, KEYWORD, STRING, COMMENT, NUMBER, ANNOTATION, TYPE }

data class Token(val start: Int, val end: Int, val kind: TokenKind)

data class Lang(
    val id: String,
    val keywords: Set<String>,
    val lineComments: List<String>,
    val blockComment: Pair<String, String>? = null,
    val stringDelims: List<String> = listOf("\"", "'"),
    /** Délimiteurs multi-lignes (`"""`, backtick JS). */
    val multilineStrings: List<String> = emptyList(),
    val annotationPrefix: Char? = null,
)

object Languages {
    private fun kw(s: String) = s.split(' ').filter { it.isNotBlank() }.toSet()

    private val cLike = Lang(
        "c", kw("if else for while do switch case break continue return new class interface extends implements import package public private protected static final void true false null this super throw throws try catch finally const let var function async await export default from typeof instanceof yield enum struct"),
        listOf("//"), "/*" to "*/",
    )
    val KOTLIN = cLike.copy(
        id = "kotlin",
        keywords = cLike.keywords + kw("fun val var when object data sealed companion internal override open abstract suspend inline is as in out by lateinit init typealias"),
        multilineStrings = listOf("\"\"\""), annotationPrefix = '@',
    )
    val JAVA = cLike.copy(id = "java", annotationPrefix = '@', keywords = cLike.keywords + kw("int long boolean double float char byte short synchronized volatile transient native assert"))
    val JS = cLike.copy(
        id = "js",
        keywords = cLike.keywords + kw("of delete in debugger with undefined type keyof readonly declare namespace as satisfies"),
        stringDelims = listOf("\"", "'"), multilineStrings = listOf("`"), annotationPrefix = '@',
    )
    val GO = cLike.copy(
        id = "go",
        keywords = kw("break case chan const continue default defer else fallthrough for func go goto if import interface map package range return select struct switch type var nil true false iota error string int bool byte rune"),
        multilineStrings = listOf("`"),
    )
    val RUST = cLike.copy(id = "rust", keywords = kw("as break const continue crate else enum extern false fn for if impl in let loop match mod move mut pub ref return self Self static struct super trait true type unsafe use where while async await dyn"))
    val PHP = cLike.copy(id = "php", lineComments = listOf("//", "#"), keywords = cLike.keywords + kw("echo foreach as elseif endif endforeach require include require_once include_once namespace use fn match"))
    val PYTHON = Lang(
        "python",
        kw("and as assert async await break class continue def del elif else except False finally for from global if import in is lambda None nonlocal not or pass raise return True try while with yield self match case"),
        listOf("#"), null, multilineStrings = listOf("\"\"\"", "'''"), annotationPrefix = '@',
    )
    val RUBY = Lang("ruby", kw("def end class module if elsif else unless while until for in do return yield begin rescue ensure raise nil true false self require attr_accessor"), listOf("#"))
    val SHELL = Lang("shell", kw("if then else elif fi for in do done while case esac function return export local readonly echo exit"), listOf("#"))
    val YAML = Lang("yaml", kw("true false null yes no on off"), listOf("#"))
    val HCL = Lang("hcl", kw("resource variable output module provider data locals terraform true false null for in if"), listOf("#", "//"), "/*" to "*/")
    val SQL = Lang("sql", kw("select from where insert into values update set delete create table alter drop index join left right inner outer on and or not null as group by order limit offset having union"), listOf("--"), "/*" to "*/")
    val DOCKER = Lang("docker", kw("FROM RUN CMD COPY ADD ENV ARG WORKDIR EXPOSE ENTRYPOINT USER VOLUME LABEL HEALTHCHECK AS"), listOf("#"))
    val JSON = Lang("json", kw("true false null"), emptyList(), stringDelims = listOf("\""))
    val PLAIN = Lang("plain", emptySet(), emptyList(), stringDelims = emptyList())

    fun forPath(path: String): Lang {
        val name = path.substringAfterLast('/').lowercase()
        if (name == "dockerfile" || name.endsWith(".dockerfile")) return DOCKER
        return when (name.substringAfterLast('.', "")) {
            "kt", "kts", "scala", "groovy", "gradle" -> KOTLIN
            "java", "cs", "c", "h", "cpp", "hpp", "cc", "swift", "dart" -> JAVA
            "js", "jsx", "ts", "tsx", "mjs", "cjs", "vue", "svelte" -> JS
            "go" -> GO
            "rs" -> RUST
            "php" -> PHP
            "py", "pyi" -> PYTHON
            "rb" -> RUBY
            "sh", "bash", "zsh", "env" -> SHELL
            "yml", "yaml", "toml", "ini", "cfg", "conf", "properties" -> YAML
            "tf", "hcl", "tfvars" -> HCL
            "sql" -> SQL
            "json", "lock" -> JSON
            else -> PLAIN
        }
    }
}

/**
 * Lexer ligne par ligne avec état inter-lignes (commentaire bloc ou chaîne
 * multi-lignes ouverts). [tokenizeFile] calcule toutes les lignes d'un coup.
 */
object SyntaxHighlighter {
    /** Délimiteur encore ouvert en fin de ligne (fin de bloc à chercher), ou null. */
    data class LineResult(val tokens: List<Token>, val openUntil: String?, val openKind: TokenKind?)

    fun tokenizeFile(lines: List<String>, lang: Lang): List<List<Token>> {
        var open: String? = null
        var openKind: TokenKind? = null
        return lines.map { line ->
            val r = tokenizeLine(line, lang, open, openKind)
            open = r.openUntil
            openKind = r.openKind
            r.tokens
        }
    }

    fun tokenizeLine(line: String, lang: Lang, openUntil: String? = null, openKind: TokenKind? = null): LineResult {
        val tokens = mutableListOf<Token>()
        var i = 0
        val n = line.length
        // Reprise d'un bloc ouvert sur une ligne précédente.
        if (openUntil != null && openKind != null) {
            val end = line.indexOf(openUntil)
            if (end < 0) return LineResult(listOf(Token(0, n, openKind)), openUntil, openKind)
            tokens += Token(0, end + openUntil.length, openKind)
            i = end + openUntil.length
        }
        while (i < n) {
            val c = line[i]
            // Commentaire ligne
            val lc = lang.lineComments.firstOrNull { line.startsWith(it, i) }
            if (lc != null) {
                tokens += Token(i, n, TokenKind.COMMENT)
                return LineResult(tokens, null, null)
            }
            // Commentaire bloc
            val bc = lang.blockComment
            if (bc != null && line.startsWith(bc.first, i)) {
                val end = line.indexOf(bc.second, i + bc.first.length)
                if (end < 0) {
                    tokens += Token(i, n, TokenKind.COMMENT)
                    return LineResult(tokens, bc.second, TokenKind.COMMENT)
                }
                tokens += Token(i, end + bc.second.length, TokenKind.COMMENT)
                i = end + bc.second.length
                continue
            }
            // Chaînes multi-lignes (""" ''' `)
            val ms = lang.multilineStrings.firstOrNull { line.startsWith(it, i) }
            if (ms != null) {
                val end = line.indexOf(ms, i + ms.length)
                if (end < 0) {
                    tokens += Token(i, n, TokenKind.STRING)
                    return LineResult(tokens, ms, TokenKind.STRING)
                }
                tokens += Token(i, end + ms.length, TokenKind.STRING)
                i = end + ms.length
                continue
            }
            // Chaînes simples (avec échappement)
            val sd = lang.stringDelims.firstOrNull { line.startsWith(it, i) }
            if (sd != null) {
                var j = i + sd.length
                while (j < n && !line.startsWith(sd, j)) j += if (line[j] == '\\') 2 else 1
                val end = minOf(n, j + sd.length)
                tokens += Token(i, end, TokenKind.STRING)
                i = end
                continue
            }
            // Annotations / décorateurs
            if (lang.annotationPrefix == c && i + 1 < n && line[i + 1].isLetter()) {
                var j = i + 1
                while (j < n && (line[j].isLetterOrDigit() || line[j] == '_' || line[j] == '.')) j++
                tokens += Token(i, j, TokenKind.ANNOTATION)
                i = j
                continue
            }
            // Nombres
            if (c.isDigit() && (i == 0 || !line[i - 1].isLetterOrDigit() && line[i - 1] != '_')) {
                var j = i + 1
                while (j < n && (line[j].isLetterOrDigit() || line[j] == '.' || line[j] == '_')) j++
                tokens += Token(i, j, TokenKind.NUMBER)
                i = j
                continue
            }
            // Identifiants : mot-clé, type (Majuscule) ou texte
            if (c.isLetter() || c == '_' || c == '$') {
                var j = i + 1
                while (j < n && (line[j].isLetterOrDigit() || line[j] == '_' || line[j] == '$')) j++
                val word = line.substring(i, j)
                val kind = when {
                    word in lang.keywords || (lang.id == "sql" && word.lowercase() in lang.keywords) -> TokenKind.KEYWORD
                    word[0].isUpperCase() && lang.id != "plain" && lang.id != "docker" -> TokenKind.TYPE
                    else -> TokenKind.PLAIN
                }
                if (kind != TokenKind.PLAIN) tokens += Token(i, j, kind)
                i = j
                continue
            }
            i++
        }
        return LineResult(tokens, null, null)
    }
}
