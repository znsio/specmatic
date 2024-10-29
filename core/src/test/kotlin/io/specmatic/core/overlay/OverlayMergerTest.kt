package io.specmatic.core.overlay

import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Test

class OverlayMergerTest {

    @Test
    fun `should merge the update map`() {
        val yamlContent = """
            node1: "existing node1 content"
            node2:
                subnode: "existing additional content"
            node3:
                newField: "existing value"
        """.trimIndent()

        val overlayContent = """
            overlay: 1.0.0
            info:
              title: Targeted Overlay
              version: 1.0.0
            actions:
              - target: ${'$'}.node1
                update: new node1 content
              - target: ${'$'}.node2.subnode
                update: new additional content
              - target: ${'$'}.node3
                update:
                  newField: new value
        """.trimIndent()

        val updatedContent = OverlayMerger().merge(
            baseContent = yamlContent,
            updateMap = OverlayParser.parseAndReturnUpdateMap(overlayContent),
            removalMap = emptyMap()
        )

        assertThat(updatedContent).contains("""node1: "new node1 content"""")
        assertThat(updatedContent).contains("""subnode: "new additional content"""")
        assertThat(updatedContent).contains("""newField: "new value"""")
    }

    @Test
    fun `should merge the removal map`() {
        val yamlContent = """
            node1: "existing node1 content"
            node2:
                subnode: "existing additional content"
            node3:
                newField: "existing value"
        """.trimIndent()

        val overlayContent = """
            overlay: 1.0.0
            info:
              title: Targeted Overlay
              version: 1.0.0
            actions:
              - target: ${'$'}.node1
                update: new node1 content
              - target: ${'$'}.node2.subnode
                remove: true
              - target: ${'$'}.node3
                remove: true
        """.trimIndent()

        val updatedContent = OverlayMerger().merge(
            baseContent = yamlContent,
            updateMap = OverlayParser.parseAndReturnUpdateMap(overlayContent),
            removalMap = OverlayParser.parseAndReturnRemovalMap(overlayContent)
        )

        assertThat(updatedContent).contains("""node1: "new node1 content"""")
        assertThat(updatedContent).doesNotContain("subnode")
        assertThat(updatedContent).doesNotContain("node3")
    }
}