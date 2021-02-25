package run.qontract.core.pattern

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import run.qontract.core.*
import run.qontract.core.value.NullValue
import run.qontract.core.value.StringValue
import run.qontract.core.value.XMLNode
import run.qontract.core.value.toXMLNode
import run.qontract.shouldMatch
import run.qontract.shouldNotMatch

internal class XMLPatternTest {
    @Test
    fun `generate a value with a list of types`() {
        val itemsType = parsedPattern("<items>(Item*)</items>")
        val itemType = parsedPattern("<item>(string)</item>")

        val resolver = Resolver(newPatterns = mapOf("(Item)" to itemType))
        val xmlValue = itemsType.generate(resolver) as XMLNode

        for(node in xmlValue.childNodes.map { it as XMLNode }) {
            assertThat(node.childNodes.size == 1)
            assertThat(node.childNodes[0]).isInstanceOf(StringValue::class.java)
        }
    }

    @Test
    fun `generate a value with namespace intact`() {
        val itemsType = parsedPattern("<ns1:items xmlns:ns1=\"http://example.com/items\">(string)</ns1:items>")

        val xmlValue = itemsType.generate(Resolver()) as XMLNode

        assertThat(xmlValue.name).isEqualTo("items")
        assertThat(xmlValue.realName).isEqualTo("ns1:items")

        assertThat(xmlValue.attributes.size).isOne()
        assertThat(xmlValue.attributes.get("xmlns:ns1")).isEqualTo(StringValue("http://example.com/items"))

        assertThat(xmlValue.childNodes.size).isOne()
        assertThat(xmlValue.childNodes.first()).isInstanceOf(StringValue::class.java)
    }

    @Test
    fun `should fail to match nulls gracefully`() {
        NullValue shouldNotMatch XMLPattern("<data></data>")
    }

    @Test
    fun `should match a number within a structure`() {
        toXMLNode("<outer><inner>1</inner></outer>") shouldMatch XMLPattern("<outer><inner>(number)</inner></outer>")
    }

    @Test
    fun `should match a type with whitespace`() {
        val xmlSpecWithWhitespace = """
<outer>
    <inner>
        (number)
    </inner>
</outer>
""".trimMargin()
        toXMLNode("<outer><inner>1</inner></outer>") shouldMatch XMLPattern(xmlSpecWithWhitespace)
    }

    @Test
    fun `optional node text should match non empty value`() {
        toXMLNode("<data>1</data>") shouldMatch XMLPattern("<data>(number?)</data>")
    }

    @Test
    fun `optional node text should match empty value`() {
        toXMLNode("<data></data>") shouldMatch XMLPattern("<data>(number?)</data>")
    }

    @Test
    fun `optional node text type should encompass text type`() {
        val resolver = Resolver()

        val bigger = XMLPattern("<data>(number?)</data>")
        val smaller = XMLPattern("<data>(number)</data>")

        assertThat(bigger.encompasses(smaller, resolver, resolver)).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `optional node text type should encompass empty text`() {
        val resolver = Resolver()

        val bigger = XMLPattern("<data>(number?)</data>")
        val smaller = XMLPattern("<data></data>")

        assertThat(bigger.encompasses(smaller, resolver, resolver)).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `optional node text type should encompass empty text node without closing tag`() {
        val resolver = Resolver()

        val bigger = XMLPattern("<data>(number?)</data>")
        val smaller = XMLPattern("<data/>")

        assertThat(bigger.encompasses(smaller, resolver, resolver)).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should not match a value that doesn't conform to the specified type`() {
        toXMLNode("<outer><inner>abc</inner></outer>") shouldNotMatch XMLPattern("<outer><inner>(number)</inner></outer>")
    }

    @Test
    fun `list type should match multiple xml values of the same type` () {
        val numberInfoPattern = XMLPattern("<number>(number)</number>")
        val resolver = Resolver(newPatterns = mapOf("(NumberInfo)" to numberInfoPattern))
        val answerPattern = XMLPattern("<answer>(NumberInfo*)</answer>")
        val value = toXMLNode("<answer><number>10</number><number>20</number></answer>")

        assertThat(resolver.matchesPattern(null, answerPattern, value)).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `sanity check for pattern encompassing` () {
        val numberInfoPattern = XMLPattern("<number>(number)</number>")
        val resolver = Resolver()

        assertThat(numberInfoPattern.encompasses(numberInfoPattern, resolver, resolver)).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `sanity check for pattern encompassing with raw values` () {
        val numberInfoPattern = XMLPattern("<number>100</number>")
        val resolver = Resolver()

        assertThat(numberInfoPattern.encompasses(numberInfoPattern, resolver, resolver)).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `sanity check for xml with number type encompassing another with raw number` () {
        val pattern1 = XMLPattern("<number>(number)</number>")
        val pattern2 = XMLPattern("<number>100</number>")
        val resolver = Resolver()

        assertThat(pattern1.encompasses(pattern2, resolver, resolver)).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `pattern by name should encompass another pattern of the same structure` () {
        val numberInfoPattern = XMLPattern("<number>(number)</number>")
        val resolver = Resolver(newPatterns = mapOf("(Number)" to XMLPattern("<number>(number)</number>")))

        assertThat(resolver.getPattern("(Number)").encompasses(numberInfoPattern, resolver, resolver)).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `sanity check for nested pattern encompassing` () {
        val answersPattern = XMLPattern("<answer><number>(number)</number><name>(string)</name></answer>")
        val resolver = Resolver()

        assertThat(answersPattern.encompasses(answersPattern, resolver, resolver)).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `pattern should not encompass another with different order` () {
        val answersPattern1 = XMLPattern("<answer><number>(number)</number><name>(string)</name></answer>")
        val answersPattern2 = XMLPattern("<answer><name>(string)</name><number>(number)</number></answer>")
        val resolver = Resolver()

        assertThat(answersPattern1.encompasses(answersPattern2, resolver, resolver)).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `repeating pattern should encompass another with similar elements` () {
        val bigger = XMLPattern("<answers>(Number*)</answers>")
        val smaller = XMLPattern("<answers><number>(number)</number><number>(number)</number></answers>")
        val resolver = Resolver(newPatterns = mapOf("(Number)" to parsedPattern("<number>(number)</number>")))

        assertThat(bigger.encompasses(smaller, resolver, resolver)).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `repeating pattern should encompass another with similar elements or empty` () {
        val bigger = XMLPattern("<answers>(Number*?)</answers>")
        val smallerList = XMLPattern("<answers><number>(number)</number><number>(number)</number></answers>")
        val smallerEmpty = XMLPattern("<answers></answers>")
        val resolver = Resolver(newPatterns = mapOf("(Number)" to parsedPattern("<number>(number)</number>")))

        assertThat(bigger.encompasses(smallerList, resolver, resolver)).isInstanceOf(Result.Success::class.java)
        assertThat(bigger.encompasses(smallerEmpty, resolver, resolver)).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `repeating pattern should not encompass another with dissimilar elements` () {
        val answersPattern1 = XMLPattern("<answers>(Number*)</answers>")
        val answersPattern2 = XMLPattern("<answers><number>(string)</number><number>(number)</number></answers>")
        val resolver = Resolver(newPatterns = mapOf("(Number)" to parsedPattern("<number>(number)</number>")))

        assertThat(answersPattern1.encompasses(answersPattern2, resolver, resolver)).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `node with finite number of children should not encompass repeating pattern with similar type` () {
        val answersPattern1 = XMLPattern("<answers><number>(number)</number><number>(number)</number></answers>")
        val answersPattern2 = XMLPattern("<answer>(Number*)</answer>")
        val resolver = Resolver(newPatterns = mapOf("(Number)" to parsedPattern("<number>(number)</number>")))

        assertThat(answersPattern1.encompasses(answersPattern2, resolver, resolver)).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `sanity check for attributes`() {
        val pattern = XMLPattern("""<number val="(number)">(number)</number>""")
        assertThat(pattern.encompasses(pattern, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `sanity check for attributes with raw values`() {
        val pattern = XMLPattern("""<number val="10">(number)</number>""")
        assertThat(pattern.encompasses(pattern, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `different raw values in attributes should not match`() {
        val pattern1 = XMLPattern("""<number val="10">(number)</number>""")
        val pattern2 = XMLPattern("""<number val="20">(number)</number>""")
        assertThat(pattern1.encompasses(pattern2, Resolver(), Resolver())).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `should generate a value when the xml contains an empty node`() {
        val pattern = XMLPattern("<data><empty/><value>10</value></data>")
        val value = pattern.generate(Resolver())

        assertThat(value.toStringValue()).isEqualTo("<data><empty/><value>10</value></data>")
    }

    @Test
    fun `should pick up node names from examples`() {
        val xmlType = XMLPattern("<data><name>(string)</name><age>(number)</age></data>")
        val example = Row(listOf("name", "age"), listOf("John Doe", "10"))

        val newTypes = xmlType.newBasedOn(example, Resolver())

        val xmlNode = newTypes[0].generate(Resolver())
        assertThat(xmlNode.toStringValue()).isEqualTo("<data><name>John Doe</name><age>10</age></data>")
    }

    @Test
    fun `should pick up node names with optional values from examples`() {
        val xmlType = XMLPattern("<data><name>(string?)</name><age>(number?)</age></data>")
        val example = Row(listOf("name", "age"), listOf("John Doe", "10"))

        val newTypes = xmlType.newBasedOn(example, Resolver())

        val xmlNode = newTypes[0].generate(Resolver())
        assertThat(xmlNode.toStringValue()).isEqualTo("<data><name>John Doe</name><age>10</age></data>")
    }

    @Test
    fun `will not pick up node names with values from invalid examples`() {
        val xmlType = XMLPattern("<data><name>(string?)</name><age>(number?)</age></data>")
        val example = Row(listOf("name", "age"), listOf("John Doe", "ABC"))

        assertThatThrownBy { xmlType.newBasedOn(example, Resolver()) }.isInstanceOf(ContractException::class.java)
    }

    @Test
    fun `should pick up attribute names from examples`() {
        val xmlType = XMLPattern("""<data name="(string)" age="(number)"></data>""")
        val example = Row(listOf("name", "age"), listOf("John Doe", "10"))

        val newTypes = xmlType.newBasedOn(example, Resolver())

        val xmlNode = newTypes[0].generate(Resolver())
        assertThat(xmlNode.toStringValue()).isEqualTo("""<data age="10" name="John Doe"/>""")
    }

    @Test
    fun `should pick up attribute names with optional values from examples`() {
        val xmlType = XMLPattern("""<data name="(string?)" age="(number?)"></data>""")
        val example = Row(listOf("name", "age"), listOf("John Doe", "10"))

        val newTypes = xmlType.newBasedOn(example, Resolver())

        val xmlNode = newTypes[0].generate(Resolver())
        assertThat(xmlNode.toStringValue()).isEqualTo("""<data age="10" name="John Doe"/>""")
    }

    @Test
    fun `should pick up attribute names with optional values from empty examples`() {
        val xmlType = XMLPattern("""<data name="(string?)" age="(number?)"></data>""")
        val example = Row(listOf("name", "age"), listOf("", ""))

        val newTypes = xmlType.newBasedOn(example, Resolver())
        assertThat(newTypes.size).isOne()

        val xmlNode = newTypes[0].generate(Resolver())
        assertThat(xmlNode.toStringValue()).isEqualTo("""<data age="" name=""/>""")
    }

    @Test
    fun `optional attribute encompasses non optional`() {
        val bigger = XMLPattern("""<number val$XML_ATTR_OPTIONAL_SUFFIX="(number)">(number)</number>""")
        val smaller = XMLPattern("""<number val="(number)">(number)</number>""")
        assertThat(bigger.encompasses(smaller, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `optional attribute should pick up example value`() {
        val type = XMLPattern("""<number val$XML_ATTR_OPTIONAL_SUFFIX="(number)"></number>""")
        val example = Row(listOf("val"), listOf("10"))

        val newTypes = type.newBasedOn(example, Resolver())
        assertThat(newTypes.size).isOne()

        val xmlNode = newTypes[0].generate(Resolver())
        assertThat(xmlNode.toStringValue()).isEqualTo("""<number val="10"/>""")
    }

    @Test
    fun `optional attribute without examples should generate two tests`() {
        val type = XMLPattern("""<number val$XML_ATTR_OPTIONAL_SUFFIX="(number)"></number>""")

        val newTypes = type.newBasedOn(Row(), Resolver())
        assertThat(newTypes.size).isEqualTo(2)

        val flags = mutableListOf<String>()

        for(newType in newTypes) {
            when {
                newType.pattern.attributes.containsKey("val") -> flags.add("with")
                else -> flags.add("without")
            }
        }

        assertThat(flags.size).isEqualTo(2)
        assertThat(flags).contains("with")
        assertThat(flags).contains("without")
    }

    @Test
    fun `sanity test that double optional gets handled right`() {
        val type = XMLPattern("""<number val$XML_ATTR_OPTIONAL_SUFFIX$XML_ATTR_OPTIONAL_SUFFIX="(number)"></number>""")

        val newTypes = type.newBasedOn(Row(), Resolver())
        assertThat(newTypes.size).isEqualTo(2)

        val flags = mutableListOf<String>()

        for(newType in newTypes) {
            when {
                newType.pattern.attributes.containsKey("val$XML_ATTR_OPTIONAL_SUFFIX") -> flags.add("with")
                else -> flags.add("without")
            }
        }

        assertThat(flags.size).isEqualTo(2)
        assertThat(flags).contains("with")
        assertThat(flags).contains("without")
    }

    @Test
    fun `unbound namespace should be parsed`() {
        val xml = """<ns1:name>(string)</ns1:name>"""
        val type = parsedPattern(xml)

        assertThat(type.matches(parsedValue(xml), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `node with string should match empty node`() {
        val type = parsedPattern("""<name>(string)</name>""")
        val value = parsedValue("""<name/>""")

        val result = type.matches(value, Resolver())
        println(resultReport(result))
        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `given a node with the qontract namespace, look it up and match the type`() {
        val nameType = parsedPattern("<name>(string)</name>")
        val personType = parsedPattern("<person><qontract_Name/></person>")

        val resolver = Resolver(newPatterns = mapOf("(Name)" to nameType))

        val xmlNode = parsedValue("<person><name>Jill</name></person>")
        assertThat(resolver.matchesPattern(null, personType, xmlNode).isTrue()).isTrue
    }

    @Test
    fun `matching works for an xml node with more than one child node`() {
        val type = XMLPattern("<account><name>John Doe</name><address>(string)</address></account>")
        val value = toXMLNode("<account><name>John Doe</name><address>Baker street</address></account>")

        assertThat(type.matches(value, Resolver())).isInstanceOf(Result.Success::class.java)
    }

    private val optionalAttribute: String = "$OCCURS_ATTRIBUTE_NAME=\"$OPTIONAL_ATTRIBUTE_VALUE\""

    @Test
    fun `last node can be optional`() {
        val type = XMLPattern("<account><name>(string)</name><address $optionalAttribute>(string)</address></account>")
        val value = toXMLNode("<account><name>John Doe</name></account>")

        assertThat(type.matches(value, Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `second to last node can be optional`() {
        val type = XMLPattern("<account><name>(string)</name><address $optionalAttribute>(string)</address><phone>(number)</phone></account>")
        val value = toXMLNode("<account><name>John Doe</name><phone>10</phone></account>")

        assertThat(type.matches(value, Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `multiple nodes can be optional`() {
        val type = XMLPattern("<account><name $optionalAttribute>(string)</name><address $optionalAttribute>(string)</address><phone>(number)</phone></account>")
        val value = toXMLNode("<account><phone>10</phone></account>")

        assertThat(type.matches(value, Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `multiple optional nodes can be corresponding nodes that are actually present`() {
        val type = XMLPattern("<account><name $optionalAttribute>(string)</name><address $optionalAttribute>(string)</address><phone>(number)</phone></account>")
        val value = toXMLNode("<account><name>Jane Doe</name><address>Baker Street</address><phone>10</phone></account>")

        assertThat(type.matches(value, Resolver())).isInstanceOf(Result.Success::class.java)
    }

    private val multipleAttribute: String = "$OCCURS_ATTRIBUTE_NAME=\"$MULTIPLE_ATTRIBUTE_VALUE\""

    @Test
    fun `multiple nodes at the end can be matched`() {
        val type = XMLPattern("<account><name>(string)</name><address $multipleAttribute>(string)</address></account>")
        val value = toXMLNode("<account><name>John Doe</name><address>Baker Street</address><address>Downing Street</address></account>")

        assertThat(type.matches(value, Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `multiple nodes in the middle can be matched`() {
        val type = XMLPattern("<account><name>(string)</name><address $multipleAttribute>(string)</address><phone>(number)</phone></account>")
        val value = toXMLNode("<account><name>John Doe</name><address>Baker Street</address><address>Downing Street</address><phone>10</phone></account>")

        assertThat(type.matches(value, Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `multiple nodes at the start can be matched`() {
        val type = XMLPattern("<account><address $multipleAttribute>(string)</address><phone>(number)</phone><name>(string)</name></account>")
        val value = toXMLNode("<account><address>Baker Street</address><address>Downing Street</address><phone>10</phone><name>John Doe</name></account>")

        assertThat(type.matches(value, Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `multi-node declaration can match 0 occurence of those nodes`() {
        val type = XMLPattern("<account><name>(string)</name><address $multipleAttribute>(string)</address><phone>(number)</phone></account>")
        val value = toXMLNode("<account><name>John Doe</name><phone>10</phone></account>")

        assertThat(type.matches(value, Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `multi-node declaration can be followed by optional declaration in which none of the nodes declared are found in the matched value`() {
        val type = XMLPattern("<account><name>(string)</name><address $multipleAttribute>(string)</address><phone $optionalAttribute>(number)</phone></account>")
        val value = toXMLNode("<account><name>John Doe</name></account>")

        assertThat(type.matches(value, Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `optional type returns an error when matching a value of a different type`() {
        val type = XMLPattern("<account><name $optionalAttribute>(string)</name></account>")
        val value = toXMLNode("<account>test</account>")

        assertThat(type.matches(value, Resolver())).isInstanceOf(Result.Failure::class.java)
    }
}
