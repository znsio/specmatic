package io.specmatic.core.value

import io.specmatic.core.pattern.isPatternToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class UseExampleDeclarationsTest {
    @Test
    fun `should not add pattern values as examples`() {
        val exampleDeclaration = UseExampleDeclarations().plus(Pair("key", "(string)"))
        assertThat(exampleDeclaration.examples).isEmpty()
        assertThat(exampleDeclaration.plus(UseExampleDeclarations(mapOf("key" to "(string)"))).examples).isEmpty()
    }

    @Test
    fun `should not create new object with pattern values`() {
        assertThat(toExampleDeclaration(mapOf("key" to "(string)")).examples).isEmpty()
    }
}

private fun toExampleDeclaration(examples: Map<String, String>): UseExampleDeclarations {
    return UseExampleDeclarations(examples.filterNot { isPatternToken(it.value) })
}
