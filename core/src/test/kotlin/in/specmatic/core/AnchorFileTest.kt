package `in`.specmatic.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class AnchorFileTest {
    @Test
    fun `should resolve a canonical path relative to the provided anchor file path`() {
        val anchorFile = AnchorFile("/path/to/hello/../something-else/../hello/world.txt")
        assertThat(anchorFile.resolve("contract.spec").path).isEqualTo("/path/to/hello/contract.spec")
    }
}