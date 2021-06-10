package `in`.specmatic.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import `in`.specmatic.core.CONTRACT_EXTENSION
import `in`.specmatic.core.ContractCache
import `in`.specmatic.core.ContractFileWithExports
import `in`.specmatic.core.References
import com.github.tomakehurst.wiremock.client.WireMock.any
import io.mockk.every
import io.mockk.mockk

internal class RowTest {
    @Test
    fun `a row returns the value of the specified variable`() {
        val row = Row(listOf("name"), listOf("(${DEREFERENCE_PREFIX}name)"), variables = mapOf("name" to "Jane"))
        assertThat(row.getField("name")).isEqualTo("Jane")
    }

    @Test
    fun `if the specified variable is missing the row throws an exception`() {
        val row = Row(listOf("name"), listOf("(${DEREFERENCE_PREFIX}name)"))
        assertThatThrownBy { row.getField("name") }.isInstanceOf(ContractException::class.java)
    }

    @Test
    fun `returns the value returned from another contract`() {
        val data = mapOf("name" to "Jane")

        val contractFile = mockk<ContractFileWithExports>()
        every {
            contractFile.runContractAndExtractExports(any(), any(), any())
        }.returns(data)
        every {
            contractFile.absolutePath
        }.returns("path.spec")

        val contractCache = ContractCache(mutableMapOf("apth.spec" to data))

        val references = References("user", contractFile, valuesCache = mapOf("name" to "Jane"), contractCache = contractCache)
        val row = Row(listOf("name"), listOf("(${DEREFERENCE_PREFIX}user.name)"), references = mapOf("user" to references))

        assertThat(row.getField("name")).isEqualTo("Jane")
    }

    @Test
    fun `returns a plain value if present`() {
        val row = Row(listOf("name"), listOf("Jane"))
        assertThat(row.getField("name")).isEqualTo("Jane")
    }
}