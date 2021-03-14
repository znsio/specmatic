package run.qontract.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class VariableValueTest {
    @Test
    fun `should return the value of the specified variable`() {
        val rowValue = VariableValue(ValueReference("name"), mapOf("name" to "Jane Doe"))
        assertThat(rowValue.fetch()).isEqualTo("Jane Doe")
    }

    @Test
    fun `should throw an exception if the value of the specified variable is not found`() {
        val rowValue = VariableValue(ValueReference("surname"), mapOf("name" to "Jane Doe"))
        assertThatThrownBy { rowValue.fetch() }.isInstanceOf(ContractException::class.java)
    }
}