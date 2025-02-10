package application

import io.mockk.every
import io.mockk.mockkStatic
import io.specmatic.core.config.SpecmaticConfigVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.specmatic.core.utilities.ContractPathData
import io.specmatic.core.utilities.contractFilePathsFrom

internal class SpecmaticConfigTest {

    @Test
    fun `should return contractStubPathData`() {
        val contractPathData = ContractPathData("baseDir", "invalidPath")
        mockkStatic("io.specmatic.core.utilities.Utilities")
        every {
            contractFilePathsFrom(any(), any(), any())
        }.returns(listOf(contractPathData))

        val paths = SpecmaticConfig().contractStubPathData()
        assertThat(paths == listOf(contractPathData)).isTrue
    }

    @Test
    fun `should print a warning for older versions of the config`() {
        val (output, _) = captureStandardOutput {
            io.specmatic.core.SpecmaticConfig(version = SpecmaticConfigVersion.VERSION_1).printWarningForOlderVersions(
                configFilename
            )
        }

        assertThat(output).contains("older version")
    }
}