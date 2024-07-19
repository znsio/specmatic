package io.specmatic.core.wsdl.parser.operation

import io.specmatic.core.pattern.XMLPattern
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class SOAPTypesTest {
    @Test
    fun `generates gherkin statements with one type`() {
        val types = SOAPTypes(mapOf("Name" to XMLPattern("<name>(string)</name>")))
        val statements = types.statements()
        assertThat(statements).hasSize(1)
        assertThat(statements.first()).startsWith("Given ")
    }

    @Test
    fun `generates gherkin statements with one multiple types`() {
        val types = SOAPTypes(mapOf("Name" to XMLPattern("<name>(string)</name>"), "Address" to XMLPattern("<address>(string)</address>")))
        val statements = types.statements()
        assertThat(statements).hasSize(2)
        assertThat(statements.first()).startsWith("Given ")
    }
}