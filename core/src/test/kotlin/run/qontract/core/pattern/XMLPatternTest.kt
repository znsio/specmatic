package run.qontract.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.shouldMatch
import run.qontract.core.shouldNotMatch
import run.qontract.core.value.NullValue
import run.qontract.core.value.XMLNode
import run.qontract.core.value.XMLValue

internal class XMLPatternTest {
    @Test
    fun `should fail to match nulls gracefully`() {
        NullValue shouldNotMatch XMLPattern2("<data></data>")
    }

    @Test
    fun `should match a number within a structure`() {
        XMLNode("<outer><inner>1</inner></outer>") shouldMatch XMLPattern2("<outer><inner>(number)</inner></outer>")
    }

    @Test
    fun `should not match a value that doesn't conform to the specified type`() {
        XMLNode("<outer><inner>abc</inner></outer>") shouldNotMatch XMLPattern2("<outer><inner>(number)</inner></outer>")
    }

    @Test
    fun `list type should match multiple xml values of the same type` () {
        val numberInfoPattern = XMLPattern2("<number>(number)</number>")
        val resolver = Resolver(newPatterns = mapOf("(NumberInfo)" to numberInfoPattern))
        val answerPattern = XMLPattern2("<answer>(NumberInfo*)</answer>")
        val value = XMLNode("<answer><number>10</number><number>20</number></answer>")

        assertThat(resolver.matchesPattern(null, answerPattern, value)).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `sanity check for pattern encompassing` () {
        val numberInfoPattern = XMLPattern2("<number>(number)</number>")
        val resolver = Resolver()

        assertThat(numberInfoPattern.encompasses(numberInfoPattern, resolver, resolver)).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `sanity check for pattern encompassing with raw values` () {
        val numberInfoPattern = XMLPattern2("<number>100</number>")
        val resolver = Resolver()

        assertThat(numberInfoPattern.encompasses(numberInfoPattern, resolver, resolver)).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `sanity check for xml with number type encompassing another with raw number` () {
        val pattern1 = XMLPattern2("<number>(number)</number>")
        val pattern2 = XMLPattern2("<number>100</number>")
        val resolver = Resolver()

        assertThat(pattern1.encompasses(pattern2, resolver, resolver)).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `pattern by name should encompass another pattern of the same structure` () {
        val numberInfoPattern = XMLPattern2("<number>(number)</number>")
        val resolver = Resolver(newPatterns = mapOf("(Number)" to XMLPattern2("<number>(number)</number>")))

        assertThat(resolver.getPattern("(Number)").encompasses(numberInfoPattern, resolver, resolver)).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `sanity check for nested pattern encompassing` () {
        val answersPattern = XMLPattern2("<answer><number>(number)</number><name>(string)</name></answer>")
        val resolver = Resolver()

        assertThat(answersPattern.encompasses(answersPattern, resolver, resolver)).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `pattern should not encompass another with different order` () {
        val answersPattern1 = XMLPattern2("<answer><number>(number)</number><name>(string)</name></answer>")
        val answersPattern2 = XMLPattern2("<answer><name>(string)</name><number>(number)</number></answer>")
        val resolver = Resolver()

        assertThat(answersPattern1.encompasses(answersPattern2, resolver, resolver)).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `repeating pattern should encompass another with similar elements` () {
        val bigger = XMLPattern2("<answers>(Number*)</answers>")
        val smaller = XMLPattern2("<answers><number>(number)</number><number>(number)</number></answers>")
        val resolver = Resolver(newPatterns = mapOf("(Number)" to parsedPattern("<number>(number)</number>")))

        assertThat(bigger.encompasses(smaller, resolver, resolver)).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `repeating pattern should not encompass another with dissimilar elements` () {
        val answersPattern1 = XMLPattern2("<answers>(Number*)</answers>")
        val answersPattern2 = XMLPattern2("<answers><number>(string)</number><number>(number)</number></answers>")
        val resolver = Resolver(newPatterns = mapOf("(Number)" to parsedPattern("<number>(number)</number>")))

        assertThat(answersPattern1.encompasses(answersPattern2, resolver, resolver)).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `node with finite number of children should not encompass repeating pattern with similar type` () {
        val answersPattern1 = XMLPattern2("<answers><number>(number)</number><number>(number)</number></answers>")
        val answersPattern2 = XMLPattern2("<answer>(Number*)</answer>")
        val resolver = Resolver(newPatterns = mapOf("(Number)" to parsedPattern("<number>(number)</number>")))

        assertThat(answersPattern1.encompasses(answersPattern2, resolver, resolver)).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `sanity check for attributes`() {
        val pattern = XMLPattern2("""<number val="(number)">(number)</number>""")
        assertThat(pattern.encompasses(pattern, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `sanity check for attributes with raw values`() {
        val pattern = XMLPattern2("""<number val="10">(number)</number>""")
        assertThat(pattern.encompasses(pattern, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `different raw values in attributes should not match`() {
        val pattern1 = XMLPattern2("""<number val="10">(number)</number>""")
        val pattern2 = XMLPattern2("""<number val="20">(number)</number>""")
        assertThat(pattern1.encompasses(pattern2, Resolver(), Resolver())).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `should generate a value when the xml contains an empty node`() {
        val pattern = XMLPattern2("<data><empty/><value>10</value></data>")
        val value = pattern.generate(Resolver())

        assertThat(value.toStringValue()).isEqualTo("<data><empty/><value>10</value></data>")
    }
}
