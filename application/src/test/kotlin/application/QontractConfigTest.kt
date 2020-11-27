package application

import io.mockk.every
import io.mockk.mockkStatic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.utilities.ContractPathData
import run.qontract.core.utilities.contractFilePathsFrom

internal class QontractConfigTest {

    @Test
    fun `should return contractStubPathData`() {
        val contractPathData = ContractPathData("aaa", "bbbb")
        mockkStatic("run.qontract.core.utilities.Utilities")
        every {
            contractFilePathsFrom(any(), any(), any())
        }.returns(listOf(contractPathData))

        val paths = QontractConfig().contractStubPathData()
        assertThat(paths.equals(listOf(contractPathData))).isTrue
    }
}