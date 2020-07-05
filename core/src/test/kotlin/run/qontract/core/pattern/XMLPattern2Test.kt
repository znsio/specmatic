package run.qontract.core.pattern

import org.junit.jupiter.api.Test
import run.qontract.core.Resolver

internal class XMLPattern2Test {
    @Test
    fun `generate a value with a list of types`() {
        val itemsType = parsedPattern("<items>(Item*)</items>")
        val itemType = parsedPattern("<item>(string)</item>")

        val resolver = Resolver(newPatterns = mapOf("(Item)" to itemType))
        val xmlValue = itemsType.generate(resolver)

        println(xmlValue)
    }
}