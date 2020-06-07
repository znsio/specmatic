package run.qontract.core.value

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.parsedValue

internal class JSONArrayValueTest {
    @Test
    fun `when there are multiple values, take only the examples of the first one`() {
        val array = parsedValue("""["one", "two", "three"]""")
        val (typeDeclaration, exampleDeclaration) = array.typeDeclarationWithKey("array", ExampleDeclaration())

        assertThat(typeDeclaration.types).isEmpty()
        assertThat(exampleDeclaration.examples.isEmpty())
    }

    @Test
    fun `empty array test`() {
        val array = parsedValue("""[]""")
        val (typeDeclaration, exampleDeclaration) = array.typeDeclarationWithKey("array", ExampleDeclaration())

        assertThat(typeDeclaration.types).isEmpty()
        assertThat(exampleDeclaration.examples.isEmpty())
    }
}
