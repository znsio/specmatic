package run.qontract.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import run.qontract.core.CONTRACT_EXTENSION
import run.qontract.core.QontractFilePath
import run.qontract.core.References

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
        val references = References("user", QontractFilePath("user.$CONTRACT_EXTENSION"), valuesCache = mapOf("name" to "Jane"))
        val row = Row(listOf("name"), listOf("(${DEREFERENCE_PREFIX}user.name)"), references = mapOf("user" to references))
        assertThat(row.getField("name")).isEqualTo("Jane")
    }

    @Test
    fun `returns a plain value if present`() {
        val row = Row(listOf("name"), listOf("Jane"))
        assertThat(row.getField("name")).isEqualTo("Jane")
    }
}