package `in`.specmatic.core.value

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class JSONArrayValueTest {
    @Test
    fun `when there are multiple values, take only the type of the first one`() {
        val array= JSONArrayValue(listOf(StringValue("one"), StringValue("two"), StringValue("three")))
        val (typeDeclaration, exampleDeclaration) = array.typeDeclarationWithKey("array", emptyMap(), UseExampleDeclarations())

        assertThat(typeDeclaration.typeValue).isEqualTo("(string*)")
        assertThat(typeDeclaration.types).isEmpty()
        assertThat(exampleDeclaration.examples.isEmpty())
    }

    @Test
    fun `empty array test`() {
        val array = JSONArrayValue(emptyList())
        val (typeDeclaration, exampleDeclaration) = array.typeDeclarationWithKey("array", emptyMap(), UseExampleDeclarations())

        assertThat(typeDeclaration.typeValue).isEqualTo("[]")
        assertThat(typeDeclaration.types).isEmpty()
        assertThat(exampleDeclaration.examples.isEmpty())
    }
}
