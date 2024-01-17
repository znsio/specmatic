package application

import io.mockk.every
import io.mockk.mockkStatic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.utilities.ContractPathData
import `in`.specmatic.core.utilities.contractFilePathsFrom

internal class SpecmaticConfigTest {

    @Test
    fun `should return contractStubPathData`() {
        val contractPathData = ContractPathData("baseDir", "invalidPath")
        mockkStatic("in.specmatic.core.utilities.Utilities")
        every {
            contractFilePathsFrom(any(), any(), any())
        }.returns(listOf(contractPathData))

        val paths = SpecmaticConfig().contractStubPathData()
        assertThat(paths == listOf(contractPathData)).isTrue
    }
}