package run.qontract.core.value

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ExampleDeclarationTest {
    @Test
    fun `should not add pattern values as examples`() {
        val exampleDeclaration = ExampleDeclaration().plus(Pair("key", "(string)"))
        assertThat(exampleDeclaration.examples).isEmpty()
        assertThat(exampleDeclaration.plus(ExampleDeclaration(mapOf("key" to "(string)"))).examples).isEmpty()
    }

    @Test
    fun `should not create new object with pattern values`() {
        assertThat(toExampleDeclaration(mapOf("key" to "(string)")).examples).isEmpty()
    }
}