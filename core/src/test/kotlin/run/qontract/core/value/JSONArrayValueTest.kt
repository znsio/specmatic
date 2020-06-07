package run.qontract.core.value

import org.junit.jupiter.api.Test
import run.qontract.core.pattern.parsedValue
import run.qontract.core.printToConsole
import run.qontract.core.toClause

internal class JSONArrayValueTest {
    @Test
    fun `when there are multiple values, take only the examples of the first one`() {
        val array = parsedValue("""["one", "two", "three"]""")
        val (typeDeclaration, exampleDeclaration) = array.typeDeclarationWithKey("array", ExampleDeclaration())

        printToConsole(typeDeclaration.types.entries.map { toClause(it.key, it.value) }, exampleDeclaration)
    }
}