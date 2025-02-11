package io.specmatic.core.utilities

import io.specmatic.core.DEFAULT_WORKING_DIRECTORY
import io.specmatic.osAgnosticPath
import io.specmatic.toContractSourceEntries
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LocalFileSystemSourceTest {
    @Test
    fun temp() {
        val localFileSystemSource = LocalFileSystemSource("dir", emptyList(), listOf("spec.yaml").toContractSourceEntries())
        val specSourceData = localFileSystemSource.loadContracts({ source -> source.stubContracts }, DEFAULT_WORKING_DIRECTORY, "")

        assertThat(specSourceData).hasSize(1)

        assertThat(specSourceData).allSatisfy {
            assertThat(it.path).isEqualTo(osAgnosticPath("dir/spec.yaml"))
        }
    }

}