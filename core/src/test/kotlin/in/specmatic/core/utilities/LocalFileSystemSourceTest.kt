package `in`.specmatic.core.utilities

import `in`.specmatic.core.DEFAULT_WORKING_DIRECTORY
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File

class LocalFileSystemSourceTest {
    @Test
    fun temp() {
        val localFileSystemSource = LocalFileSystemSource("dir", emptyList(), listOf("spec.yaml"))
        val specSourceData = localFileSystemSource.loadContracts({ source -> source.stubContracts }, DEFAULT_WORKING_DIRECTORY, "")

        assertThat(specSourceData).hasSize(1)

        assertThat(specSourceData).allSatisfy(
            {
                assertThat(it.path).isEqualTo("dir/spec.yaml")
            }
        )
    }

}