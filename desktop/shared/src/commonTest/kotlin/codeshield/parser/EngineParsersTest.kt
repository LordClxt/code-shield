package codeshield.parser

import codeshield.model.Category
import codeshield.model.Severity
import codeshield.model.ToolName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Parsers v2 : champs structurés (plages, CWE, paquets) et chemins relatifs. */
class EngineParsersTest {

    @Test
    fun pathsAreRelativeToTheScannedRoot() {
        assertEquals("src/a.py", normalizeFindingPath("./src/a.py"))
        assertEquals("src/a.py", normalizeFindingPath("/home/me/proj/src/a.py", "/home/me/proj"))
        assertEquals("src/a.py", normalizeFindingPath("C:\\proj\\src\\a.py", "C:\\proj"))
        assertEquals("/elsewhere/a.py", normalizeFindingPath("/elsewhere/a.py", "/home/me/proj"))
    }

    @Test
    fun semgrepExtractsRangeColumnsCweAndOwasp() {
        val raw = """
        {"results": [{
          "check_id": "python.lang.security.audit.formatted-sql-query.formatted-sql-query",
          "path": "./app/db.py",
          "start": {"line": 42, "col": 9}, "end": {"line": 44, "col": 31},
          "extra": {"severity": "WARNING", "message": "SQL built with format",
            "lines": "cursor.execute(f\"SELECT * FROM t WHERE id={uid}\")",
            "metadata": {"cwe": ["CWE-89: Improper Neutralization of Special Elements"],
                         "owasp": ["A03:2021 - Injection"], "confidence": "HIGH"}}
        }]}
        """.trimIndent()
        val f = parseSemgrepResults(raw).single()
        assertEquals("app/db.py", f.filePath)
        assertEquals(42, f.lineNumber)
        assertEquals(44, f.endLine)
        assertEquals(9, f.column)
        assertEquals(listOf("CWE-89"), f.cwe)
        assertEquals(listOf("A03:2021 - Injection"), f.owasp)
        assertEquals(Category.SAST, f.category)
        assertFalse(f.message.orEmpty().contains("SELECT"), "extra.lines (code) ne doit jamais être lu")
    }

    @Test
    fun gitleaksNeverReadsTheSecret() {
        val raw = """[{"RuleID":"aws-access-token","File":"config/.env","StartLine":3,"EndLine":3,
            "StartColumn":12,"EndColumn":51,"Secret":"AKIAABCDEFGHIJKLMNOP","Match":"KEY=AKIAABCDEFGHIJKLMNOP"}]"""
        val f = parseGitleaksResults(raw).single()
        assertEquals(Category.SECRET, f.category)
        assertEquals(12, f.column)
        assertNull(f.endLine)
        assertFalse(f.toString().contains("AKIA"), "le secret ne doit apparaître dans aucun champ")
    }

    @Test
    fun trivyCoversVulnsSecretsAndMisconfigs() {
        val raw = """
        {"Results": [
          {"Target": "requirements.txt", "Class": "lang-pkgs", "Vulnerabilities": [
            {"VulnerabilityID": "CVE-2023-30861", "PkgName": "flask", "InstalledVersion": "2.2.0",
             "FixedVersion": "2.3.2, 2.2.5", "Severity": "HIGH", "Title": "flask: cookie disclosure", "CweIDs": ["CWE-539"]}]},
          {"Target": "Dockerfile", "Class": "config", "Misconfigurations": [
            {"ID": "DS002", "AVDID": "AVD-DS-0002", "Title": "Image user should not be 'root'", "Severity": "HIGH",
             "Status": "FAIL", "Resolution": "Add USER", "CauseMetadata": {"StartLine": 1, "EndLine": 12}},
            {"ID": "DS001", "Title": "ok", "Severity": "LOW", "Status": "PASS"}]},
          {"Target": "app/settings.py", "Class": "secret", "Secrets": [
            {"RuleID": "github-pat", "Category": "GitHub", "Severity": "CRITICAL", "Title": "GitHub PAT",
             "StartLine": 7, "EndLine": 7, "Match": "token = ghp_****"}]}
        ]}
        """.trimIndent()
        val fs = parseTrivyResults(raw)
        assertEquals(3, fs.size, fs.toString())
        val sca = fs.first { it.category == Category.SCA }
        assertEquals("flask", sca.packageName)
        assertEquals("2.2.0", sca.installedVersion)
        assertEquals("2.3.2", sca.fixedVersion)
        assertEquals(listOf("CWE-539"), sca.cwe)
        val iac = fs.first { it.category == Category.IAC }
        assertEquals("AVD-DS-0002", iac.ruleId)
        assertEquals(12, iac.endLine)
        val secret = fs.first { it.category == Category.SECRET }
        assertEquals(Severity.CRITICAL, secret.severity)
        assertFalse(secret.toString().contains("ghp_"))
    }

    @Test
    fun banditMapsTestIdsCweAndColumns() {
        val raw = """
        {"results": [{"filename": "./svc/run.py", "line_number": 10, "line_range": [10, 12],
          "col_offset": 4, "end_col_offset": 40, "issue_severity": "HIGH", "issue_confidence": "HIGH",
          "issue_text": "subprocess call with shell=True", "test_id": "B602",
          "test_name": "subprocess_popen_with_shell_equals_true", "issue_cwe": {"id": 78},
          "code": "subprocess.Popen(cmd, shell=True)"}]}
        """.trimIndent()
        val f = parseBanditResults(raw).single()
        assertEquals(ToolName.BANDIT, f.tool)
        assertEquals("svc/run.py", f.filePath)
        assertEquals(12, f.endLine)
        assertEquals(5, f.column)
        assertEquals(listOf("CWE-78"), f.cwe)
        assertEquals("B602", f.remediationKey)
        assertFalse(f.toString().contains("Popen(cmd"), "le champ `code` ne doit jamais être lu")
    }

    @Test
    fun osvGroupsAliasesAndPrefersCve() {
        val raw = """
        {"results": [{"source": {"path": "/repo/package-lock.json", "type": "lockfile"},
          "packages": [{"package": {"name": "lodash", "version": "4.17.20", "ecosystem": "npm"},
            "vulnerabilities": [{"id": "GHSA-35jh-r3h4-6jhm", "summary": "Command Injection in lodash",
               "aliases": ["CVE-2021-23337"],
               "affected": [{"package": {"name": "lodash"}, "ranges": [{"events": [{"introduced": "0"}, {"fixed": "4.17.21"}]}]}]}],
            "groups": [{"ids": ["GHSA-35jh-r3h4-6jhm", "CVE-2021-23337"], "max_severity": "7.2"}]}]}]}
        """.trimIndent()
        val f = parseOsvResults(raw, "/repo").single()
        assertEquals("CVE-2021-23337", f.ruleId)
        assertEquals("package-lock.json", f.filePath)
        assertEquals(Severity.HIGH, f.severity)
        assertEquals("4.17.21", f.fixedVersion)
        assertEquals("lodash", f.packageName)
    }

    @Test
    fun checkovHandlesSingleAndMultiFrameworkReports() {
        val one = """{"check_type": "terraform", "results": {"failed_checks": [
            {"check_id": "CKV_AWS_20", "check_name": "S3 Bucket has an ACL defined which allows public READ access.",
             "file_path": "/infra/s3.tf", "file_line_range": [1, 9], "resource": "aws_s3_bucket.data",
             "guideline": "https://docs.prismacloud.io/..."}]}}"""
        val f = parseCheckovResults(one).single()
        assertEquals("infra/s3.tf", f.filePath)
        assertEquals(9, f.endLine)
        assertEquals(Severity.MEDIUM, f.severity)
        assertEquals(Category.IAC, f.category)
        val many = "[$one, {\"check_type\": \"dockerfile\", \"results\": {\"failed_checks\": []}}]"
        assertEquals(1, parseCheckovResults(many).size)
    }

    @Test
    fun dependencyCheckUsesCvssForSeverity() {
        val raw = """
        {"dependencies": [{"fileName": "log4j-core-2.14.1.jar", "filePath": "/p/lib/log4j-core-2.14.1.jar",
          "packages": [{"id": "pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1"}],
          "vulnerabilities": [{"name": "CVE-2021-44228", "severity": "CRITICAL",
            "cvssv3": {"baseScore": 10.0}, "cwes": ["CWE-502", "CWE-400"], "description": "Log4Shell"}]}]}
        """.trimIndent()
        val f = parseDependencyCheckResults(raw, "/p").single()
        assertEquals("lib/log4j-core-2.14.1.jar", f.filePath)
        assertEquals(Severity.CRITICAL, f.severity)
        assertEquals("log4j-core", f.packageName)
        assertEquals("2.14.1", f.installedVersion)
        assertEquals(listOf("CWE-502", "CWE-400"), f.cwe)
    }

    @Test
    fun malformedReportsNeverThrow() {
        for (parse in listOf(::parseTrivyResults, ::parseBanditResults, ::parseOsvResults, ::parseCheckovResults, ::parseDependencyCheckResults)) {
            assertTrue(parse("not json", "").isEmpty())
            assertTrue(parse("{}", "").isEmpty())
            assertTrue(parse("[]", "").isEmpty())
        }
    }
}
