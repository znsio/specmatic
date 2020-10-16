package run.qontract.core.value

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class XMLNodeTest {
    @Test
    fun `parse XML`() {
        val xmlData = "<data>data</data>"
        val node = XMLNode(xmlData)

        assertThat(node).isEqualTo(XMLNode("data", "data", emptyMap(), listOf(StringValue("data"))))
        assertThat(node.toStringValue()).isEqualTo("<data>data</data>")
    }
}