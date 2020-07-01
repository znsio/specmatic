package run.qontract.core.utilities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.value.XMLValue

internal class UtilitiesTest {
    @Test
    fun `parsing multiline xml`() {
        XMLValue("""<data> </data>""").let {
            println(it.node.nodeType)
            println(it.node.firstChild.nodeType)
            println(it.node.firstChild.textContent)
        }

        val xml = """<line1>
<line2>data</line2>
</line1>
        """

        val xmlValue = XMLValue(xml)

        val node = xmlValue.node

        assertThat(node.childNodes.length).isOne()
        assertThat(xmlValue.toStringValue().trim()).isEqualTo("""<line1><line2>data</line2></line1>""")
    }
}
