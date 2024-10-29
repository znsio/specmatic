package io.specmatic.core.overlay

import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Test

class OverlayParserTest {

    @Test
    fun `should parse overlay content`() {
        val overlayContent = """
            actions:
              - target: ${'$'}.node1
                update: new content for node1
              - target: ${'$'}.node2.subnode
                update: new additional content for subnode
              - target: ${'$'}.node3
                update:
                  newField: updated value
        """.trimIndent()

        val map = OverlayParser.parseAndReturnUpdateMap(overlayContent)

        assertThat(map["${'$'}.node1"]).isEqualTo("new content for node1")
        assertThat(map["${'$'}.node2.subnode"]).isEqualTo("new additional content for subnode")
        assertThat(map["${'$'}.node3"]).isEqualTo(
            mapOf(
                "newField" to "updated value"
            )
        )
    }
}