package codeshield.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JvmBinaryLocatorTest {

    @Test
    fun missingBinaryResolvesToNull() {
        assertNull(JvmBinaryLocator("codeshield-definitely-missing-xyz"))
    }

    @Test
    fun javaIsAlwaysResolvableOnJvm() {
        // `java` existe forcément : les tests tournent dessus.
        assertNotNull(JvmBinaryLocator("java"))
    }

    @Test
    fun windowsLooksForExeAndCmd() {
        assertEquals(listOf("claude.exe", "claude.cmd", "claude"), binaryFileNames("claude", windows = true))
        assertEquals(listOf("claude"), binaryFileNames("claude", windows = false))
    }

    @Test
    fun userBinDirsIncludeLocalBinAndNpmGlobal() {
        val dirs = userBinDirs(home = "/h", appData = "C:/Users/me/AppData/Roaming")
        assertTrue("/h/.local/bin" in dirs && "C:/Users/me/AppData/Roaming/npm" in dirs, dirs.toString())
    }
}
