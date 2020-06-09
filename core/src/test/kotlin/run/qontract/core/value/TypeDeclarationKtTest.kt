package run.qontract.core.value

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.DeferredPattern
import run.qontract.core.pattern.TabularPattern
import run.qontract.core.pattern.parsedValue

internal class TypeDeclarationKtTest {
    @Test
    fun `improved support for converging a list with an empty list`() {
        val emptyList = parsedValue("""{"list": []}""")
        val oneElementList = parsedValue("""{"list": [1]}""")

        val (emptyDeclaration, _) = emptyList.typeDeclarationWithKey("List", emptyMap(), ExampleDeclaration())
        val (oneElementDeclaration, _) = oneElementList.typeDeclarationWithKey("List", emptyMap(), ExampleDeclaration())

        val convergedOneWay = convergeTypeDeclarations(emptyDeclaration, oneElementDeclaration)
        val convergedTheOtherWay = convergeTypeDeclarations(oneElementDeclaration, emptyDeclaration)

        assertThat((convergedOneWay.types.getValue("List") as TabularPattern).pattern.getValue("list")).isEqualTo(DeferredPattern("(number*)"))
        assertThat((convergedTheOtherWay.types.getValue("List") as TabularPattern).pattern.getValue("list")).isEqualTo(DeferredPattern("(number*)"))
    }

}