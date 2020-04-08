package run.qontract.core.pattern

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node
import org.xml.sax.InputSource
import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.utilities.xmlToString
import run.qontract.core.value.NullValue
import run.qontract.core.value.Value
import run.qontract.core.value.XMLValue
import run.qontract.core.withNumericStringPattern
import java.io.StringReader
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory


data class XMLPattern(val node: Node) : Pattern {
    override val pattern = xmlToString(node)

    constructor(bodyContent: String): this(parseXML(bodyContent).documentElement)

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData is NullValue)
            return Result.Failure("Got null, but expected xml of the form $pattern")

        return when (val result = matchesXMLData(sampleData, withNumericStringPattern(resolver))) {
            is Result.Failure -> result.reason("XML did not match")
            else -> result
        }
    }

    private fun matchesXMLData(sampleXMLValue: Any?, resolver: Resolver): Result {
        when (sampleXMLValue) {
            is Document -> {
                return matchesDocument(sampleXMLValue, resolver)
            }
            is XMLValue -> {
                return matchesNode(node, sampleXMLValue.node, resolver)
            }
            is Node -> {
                return matchesNode(node, sampleXMLValue, resolver)
            }
            else -> return try {
                matchesDocument(parseXML(sampleXMLValue as String), resolver)
            } catch (ignored: Exception) {
                Result.Success()
            }
        }
    }

    private fun matchesDocument(sampleXMLDocument: Document, resolver: Resolver): Result {
        return matchesNode(node, sampleXMLDocument.documentElement, resolver)
    }

    private fun matchesNode(patternNode: Node, sampleNode: Node, resolver: Resolver): Result {
        if (patternNode.nodeName != sampleNode.nodeName) {
            return if (isPatternToken(patternNode.nodeValue)) {
                val pattern = resolver.getPattern(withoutRestTokenForXML(patternNode.nodeValue))
                val sampleValue = XMLValue(sampleNode)

                when (val result = resolver.matchesPattern(sampleNode.nodeName, pattern, sampleValue)) {
                    is Result.Failure -> result.reason("Node ${sampleNode.nodeName} did not match. Expected: ${patternNode.nodeValue} Actual: ${sampleNode.nodeValue}")
                    else -> result
                }
            } else {
                Result.Failure("${patternNode.nodeValue} is not a pattern token")
            }
        }
        if (patternNode.hasAttributes()) {
            if (!sampleNode.hasAttributes())
                return Result.Failure("Node ${sampleNode.nodeName} does not have attributes. Expected: ${patternNode.attributes}")
            if (!matchingAttributes(patternNode.attributes, sampleNode.attributes, resolver))
                return Result.Failure("Node ${sampleNode.nodeName} does not have matching attributes. Expected: ${patternNode.attributes} Actual: ${sampleNode.attributes}")
        }
        if (patternNode.hasChildNodes()) {
            if (!sampleNode.hasChildNodes()) {
                return Result.Failure("Node ${sampleNode.nodeName} does not have child nodes. Expected: ${patternNode.childNodes}")
            }
            val patternChildNodes = patternNode.childNodes
            val sampleChildNodes = sampleNode.childNodes
            val firstPatternNode = patternChildNodes.item(0)
            if (isRepeatingPattern(firstPatternNode.nodeValue)) {
                return matchManyNodes(firstPatternNode, sampleNode, resolver)
            }
            if (patternChildNodes.length != sampleChildNodes.length)
                return Result.Failure("Node ${sampleNode.nodeName} does not have matching number of children. Expected: ${patternChildNodes.length} Actual: ${sampleChildNodes.length}")
            for (index in 0 until patternChildNodes.length) {
                when (val result = matchesNode(patternChildNodes.item(index), sampleChildNodes.item(index), resolver)) {
                    is Result.Success -> return result
                    else -> {
                    }
                }
            }
            return Result.Success()
        }
        val patternValue = patternNode.nodeValue
        val sampleValue = sampleNode.nodeValue
        val key = patternNode.parentNode.nodeName

        return when {
            isPatternToken(patternValue) -> {
                val resolvedPattern = resolver.getPattern(patternValue)
                val resolvedValue = resolvedPattern.parse(sampleValue, resolver)

                when (val result = resolver.matchesPattern(key, resolvedPattern, resolvedValue)) {
                    is Result.Failure -> result.reason("Node $key did not match. Expected: $patternValue Actual: $sampleValue")
                    else -> result
                }
            }
            else -> when (patternValue) {
                sampleValue -> Result.Success()
                else -> Result.Failure("Expected value $patternValue, but found value $sampleValue")
            }
        }
    }

    private fun matchManyNodes(pattern: Node, sample: Node, resolver: Resolver): Result {
        val sampleChildNodes = sample.childNodes
        val newPattern = pattern.cloneNode(true)
        newPattern.nodeValue = withoutRepeatingToken(newPattern.nodeValue)
        for (index in 0 until sampleChildNodes.length) {
            when (val result = matchesNode(newPattern, sampleChildNodes.item(index), resolver)) {
                is Result.Failure -> return result
                else -> {

                }
            }
        }
        return Result.Success()
    }

    private fun matchingAttributes(pattern: NamedNodeMap, sample: NamedNodeMap, resolver: Resolver): Boolean {
        for (index in 0 until pattern.length) {
            val patternItem = pattern.item(index)
            val name = patternItem.nodeName
            val sampleItem = sample.getNamedItem(name) ?: return false
            val patternValue = patternItem.nodeValue
            val sampleValue = sampleItem.nodeValue
            when {
                isPatternToken(patternValue) -> {
                    val resolvedPattern = resolver.getPattern(patternValue)
                    val resolvedValue = resolvedPattern.parse(sampleValue, resolver)

                    if(!resolver.matchesPattern(name, resolvedPattern, resolvedValue).isTrue()) return false
                }
                else -> if (patternValue != sampleValue) return false

            }
        }
        return true
    }

    override fun generate(resolver: Resolver): Value {
        val newDocument = copyOfDocument()
        updateNodeTemplate(newDocument, resolver)
        return XMLValue(newDocument)
    }

    private fun copyOfDocument(): Node = node.cloneNode(true)

    private fun updateNodeTemplate(node: Node, resolver: Resolver) {
        if (node.hasAttributes()) {
            attempt(breadCrumb = node.nodeName) { updateAttributes(node.attributes, resolver) }
        }
        if (node.hasChildNodes()) {
            updateChildNodes(node, resolver)
        } else {
            val nodeValue = node.nodeValue
            node.nodeValue = generateValue(nodeValue, resolver)
        }
    }

    private fun updateChildNodes(parentNode: Node, resolver: Resolver) {
        val childNodes = parentNode.childNodes
        if (childNodes.length > 0 && isRepeatingPattern(childNodes.item(0).nodeValue)) {
            val repeatingPattern = childNodes.item(0)
            parentNode.removeChild(repeatingPattern)
            val pattern = withoutRepeatingToken(repeatingPattern.nodeValue)
            val random = Random()
            val count = random.nextInt(9) + 1
            for (i in 0 until count) {
                attempt(breadCrumb = "${parentNode.nodeName}[$i]") {
                    val result = resolver.getPattern(pattern).generate(resolver)
                    val newNode = getXMLNodeFrom(result)
                    parentNode.ownerDocument.adoptNode(newNode)
                    val first = parentNode.firstChild
                    parentNode.insertBefore(newNode, first)
                }
            }
        }
        for (index in 0 until childNodes.length) {
            updateNodeTemplate(childNodes.item(index), resolver)
        }
    }

    @Throws(Exception::class)
    private fun getXMLNodeFrom(xml: Value): Node {
        return when(xml) {
            is XMLValue -> xml.node.cloneNode(true)
            else -> parseXML(xml.toString())
        }
    }

    private fun updateAttributes(attributes: NamedNodeMap, resolver: Resolver) {
        for (index in 0 until attributes.length) {
            attempt(breadCrumb = "[$index]") {
                val attribute = attributes.item(index)
                val nodeValue = attribute.nodeValue
                attribute.nodeValue = generateValue(nodeValue, resolver)
            }
        }
    }

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        val newDocument = copyOfDocument()
        val newRoot: Node = newDocument
        updateBasedOnRow(newRoot, row, resolver)
        return listOf(XMLPattern(newDocument))
    }

    override fun parse(value: String, resolver: Resolver): Value = XMLValue(value)

    private fun updateBasedOnRow(node: Node, row: Row, resolver: Resolver) {
        attempt(breadCrumb = "${node.nodeName}") {
            if (node.hasAttributes()) {
                updateAttributesBasedOnRow(node, row, resolver)
            }
            if (node.hasChildNodes()) {
                val childNodes = node.childNodes
                for (index in 0 until childNodes.length) {
                    updateBasedOnRow(childNodes.item(index), row, resolver)
                }
            } else {
                val parentNodeName = node.parentNode.nodeName
                if (row.containsField(parentNodeName)) {
                    if (isPatternToken(node.nodeValue)) {
                        when (val result = findPattern(node.nodeValue).matches(asValue(row.getField(parentNodeName)), resolver)) {
                            is Result.Failure -> result.reason("\"The value \" + row.getField(parentNodeName) + \" in the examples of ${node.nodeName} does not match the pattern \" + node.nodeValue")
                        }
                    }
                    node.nodeValue = row.getField(parentNodeName)
                } else {
                    val value = node.nodeValue
                    node.nodeValue = when {
                        isPatternToken(value) -> resolver.getPattern(withoutRestTokenForXML(value)).generate(resolver).toString()
                        else -> value
                    }
                }
            }
        }
    }

    private fun withoutRestTokenForXML(value: String): String =
        when {
            isPatternToken(value) -> withoutRestToken(value)
            else -> value
        }

    private fun updateAttributesBasedOnRow(node: Node, row: Row, resolver: Resolver) {
        val attributes = node.attributes
        for (i in 0 until attributes.length) {
            val item = attributes.item(i)

            attempt(breadCrumb = "[$i]") {
                if (row.containsField(item.nodeName)) {
                    item.nodeValue = row.getField(item.nodeName)
                } else {
                    val value = node.nodeValue
                    node.nodeValue = generateValue(value, resolver)
                }
            }
        }
    }
}

internal fun parseXML(xmlData: String): Document {
    val builderFactory = DocumentBuilderFactory.newInstance()
    val builder = builderFactory.newDocumentBuilder()

    return builder.parse(InputSource(StringReader(xmlData)))
}
