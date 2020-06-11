package run.qontract.core.value

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.DeferredPattern
import run.qontract.core.pattern.TabularPattern
import run.qontract.core.pattern.parsedValue

internal class TypeDeclarationKtTest {
    @Test
    fun `ability to converge a list with an empty list`() {
        val emptyList = parsedValue("""{"list": []}""")
        val oneElementList = parsedValue("""{"list": [1]}""")

        val (emptyDeclaration, _) = emptyList.typeDeclarationWithKey("List", emptyMap(), ExampleDeclaration())
        val (oneElementDeclaration, _) = oneElementList.typeDeclarationWithKey("List", emptyMap(), ExampleDeclaration())

        val convergedOneWay = convergeTypeDeclarations(emptyDeclaration, oneElementDeclaration)
        val convergedTheOtherWay = convergeTypeDeclarations(oneElementDeclaration, emptyDeclaration)

        assertThat((convergedOneWay.types.getValue("List") as TabularPattern).pattern.getValue("list")).isEqualTo(DeferredPattern("(number*)"))
        assertThat((convergedTheOtherWay.types.getValue("List") as TabularPattern).pattern.getValue("list")).isEqualTo(DeferredPattern("(number*)"))
    }

    @Test
    fun `primitive with key should create a new variable if it already exists for an example`() {
        val declaration = primitiveTypeDeclarationWithKey("name", emptyMap(), ExampleDeclaration(mapOf("name" to "John Doe")), "string", "Jane Doe")
        assertThat(declaration).isEqualTo(Pair(TypeDeclaration("(name_: string)"), ExampleDeclaration(mapOf("name" to "John Doe", "name_" to "Jane Doe"))))
    }

    @Test
    fun `primitive with key should not create a new variable if an example does not exist with that name`() {
        val declaration = primitiveTypeDeclarationWithKey("name", emptyMap(), ExampleDeclaration(emptyMap()), "string", "Jane Doe")
        assertThat(declaration).isEqualTo(Pair(TypeDeclaration("(string)"), ExampleDeclaration(mapOf("name" to "Jane Doe"))))
    }

    @Test
    fun `primitive without key should create a new variable even if an example does not exist with that name`() {
        val declaration = primitiveTypeDeclarationWithoutKey("name", emptyMap(), ExampleDeclaration(emptyMap()), "string", "Jane Doe")
        assertThat(declaration).isEqualTo(Pair(TypeDeclaration("(name: string)"), ExampleDeclaration(mapOf("name" to "Jane Doe"))))
    }
}