package `in`.specmatic.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ContractFileWithExportsTest {
    @Test
    fun `uses the sibling property to generate a canonical path for the provided path`() {
        val contractFileWithExports =
            ContractFileWithExports("contract.spec", AnchorFile("/path/to/hello/../hello/something/../world"))

        assertThat(contractFileWithExports.absolutePath).isEqualTo("/path/to/hello/contract.spec")
    }
}