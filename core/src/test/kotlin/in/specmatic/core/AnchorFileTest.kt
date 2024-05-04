package `in`.specmatic.core

import `in`.specmatic.osAgnosticPath
import `in`.specmatic.runningOnWindows
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class AnchorFileTest {
    @Test
    fun `should resolve a canonical path relative to the provided anchor file path`() {
        val anchorFile = AnchorFile("/path/to/hello/../something-else/../hello/world.txt")

        if(runningOnWindows())
            assertThat(osAgnosticPath(anchorFile.resolve("contract.spec").path)).isEqualTo(osAgnosticPath(":/path/to/hello/contract.spec"))
        else
            assertThat(anchorFile.resolve("contract.spec").path).isEqualTo("/path/to/hello/contract.spec")
    }

}