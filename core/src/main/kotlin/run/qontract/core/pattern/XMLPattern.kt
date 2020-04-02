package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.value.Value
import run.qontract.core.Result
import run.qontract.core.utilities.xmlToString
import run.qontract.core.value.XMLValue
import org.w3c.dom.Document
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node
import org.xml.sax.InputSource
import run.qontract.core.value.NullValue
import java.io.StringReader
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

class XMLPattern : Pattern {
    private var document: Document = parseXML("<empty/>")

    override val pattern = xmlToString(document)

    constructor(bodyContent: String) {
        document = parseXML(bodyContent)
    }

    constructor(document: Document) {
        this.document = document
    }

    @Throws(Exception::class)
    private fun parseXML(xmlData: String): Document {
        val builderFactory = DocumentBuilderFactory.newInstance()
        val builder = builderFactory.newDocumentBuilder()
        return builder.parse(InputSource(StringReader(xmlData)))
    }

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData is NullValue)
            return Result.Failure("Got null, but expected xml of the form $pattern")


        resolver.addCustomPattern("(number)", NumericStringPattern())
        return when (val result = matchesXMLData(sampleData, resolver)) {
            is Result.Failure -> result.add("XML did not match")
            else -> result
        }
    }

    private fun matchesXMLData(sampleXMLValue: Any?, resolver: Resolver): Result {
        when (sampleXMLValue) {
            is Document -> {
                return matchesDocument(sampleXMLValue, resolver)
            }
            is XMLValue -> {
                return matchesDocument(sampleXMLValue.value as Document, resolver)
            }
            is Node -> {
                return matchesNode(document.documentElement, sampleXMLValue, resolver)
            }
            else -> return try {
                matchesDocument(parseXML(sampleXMLValue as String), resolver)
            } catch (ignored: Exception) {
                Result.Success()
            }
        }
    }

    private fun matchesDocument(sampleXMLDocument: Document, resolver: Resolver): Result {
        return matchesNode(document.documentElement, sampleXMLDocument.documentElement, resolver)
    }

    private fun matchesNode(pattern: Node, sample: Node, resolver: Resolver): Result {
        if (pattern.nodeName != sample.nodeName) {
            return if (isPatternToken(pattern.nodeValue)) {
                when (val result = resolver.matchesPatternValue(sample.nodeName, withoutRestTokenForXML(pattern.nodeValue), sample)) {
                    is Result.Failure -> result.add("Node ${sample.nodeName} did not match. Expected: ${pattern.nodeValue} Actual: ${sample.nodeValue}")
                    else -> result
                }
            } else {
                Result.Failure("${pattern.nodeValue} is not a pattern token")
            }
        }
        if (pattern.hasAttributes()) {
            if (!sample.hasAttributes())
                return Result.Failure("Node ${sample.nodeName} does not have attributes. Expected: ${pattern.attributes}")
            if (!matchingAttributes(pattern.attributes, sample.attributes, resolver))
                return Result.Failure("Node ${sample.nodeName} does not have matching attributes. Expected: ${pattern.attributes} Actual: ${sample.attributes}")
        }
        if (pattern.hasChildNodes()) {
            if (!sample.hasChildNodes()) {
                return Result.Failure("Node ${sample.nodeName} does not have child nodes. Expected: ${pattern.childNodes}")
            }
            val patternChildNodes = pattern.childNodes
            val sampleChildNodes = sample.childNodes
            val firstPatternNode = patternChildNodes.item(0)
            if (isRepeatingPattern(firstPatternNode.nodeValue)) {
                return matchManyNodes(firstPatternNode, sample, resolver)
            }
            if (patternChildNodes.length != sampleChildNodes.length)
                return Result.Failure("Node ${sample.nodeName} does not have matching number of children. Expected: ${patternChildNodes.length} Actual: ${sampleChildNodes.length}")
            for (index in 0 until patternChildNodes.length) {
                when (val result = matchesNode(patternChildNodes.item(index), sampleChildNodes.item(index), resolver)) {
                    is Result.Success -> return result
                    else -> {
                    }
                }
            }
            return Result.Success()
        }
        val patternValue = pattern.nodeValue
        val sampleValue = sample.nodeValue
        val key = pattern.parentNode.nodeName
        //TODO: looks repetitive
        return when (val result = resolver.matchesPatternValue(key, patternValue, sampleValue)) {
            is Result.Failure -> result.add("Node $key did not match. Expected: $patternValue Actual: $sampleValue")
            else -> result
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
            //TODO: remove toBoolean and return result
            if (!resolver.matchesPatternValue(name, patternValue, sampleValue).toBoolean()) return false
        }
        return true
    }

    @Throws(Exception::class)
    override fun generate(resolver: Resolver): Value {
        val newDocument = copyOfDocument()
        return XMLValue(generate(newDocument, resolver))
    }

    @Throws(ParserConfigurationException::class)
    private fun copyOfDocument(): Document {
        val builderFactory = DocumentBuilderFactory.newInstance()
        val builder = builderFactory.newDocumentBuilder()
        val newDocument = builder.newDocument()
        val originalRoot = document.documentElement
        val copiedRoot = newDocument.importNode(originalRoot, true)
        newDocument.appendChild(copiedRoot)
        return newDocument
    }

    @Throws(Exception::class)
    private fun generate(newDocument: Document, resolver: Resolver): Document {
        updateNodeTemplate(newDocument.documentElement, resolver)
        return newDocument
    }

    @Throws(Exception::class)
    private fun updateNodeTemplate(node: Node, resolver: Resolver) {
        if (node.hasAttributes()) {
            updateAttributes(node.attributes, resolver)
        }
        if (node.hasChildNodes()) {
            updateChildNodes(node, resolver)
        } else {
            val nodeValue = node.nodeValue
            node.nodeValue = generateValue(nodeValue, resolver).toString()
        }
    }

    @Throws(Exception::class)
    private fun updateChildNodes(parentNode: Node, resolver: Resolver) {
        val childNodes = parentNode.childNodes
        if (childNodes.length > 0 && isRepeatingPattern(childNodes.item(0).nodeValue)) {
            val repeatingPattern = childNodes.item(0)
            parentNode.removeChild(repeatingPattern)
            val pattern = withoutRepeatingToken(repeatingPattern.nodeValue)
            val random = Random()
            val count = random.nextInt(9) + 1
            for (i in 0 until count) {
                val result = resolver.generateFromAny(pattern)
                val newNode = getXMLNodeFrom(result)
                parentNode.ownerDocument.adoptNode(newNode)
                val first = parentNode.firstChild
                parentNode.insertBefore(newNode, first)
            }
        }
        for (index in 0 until childNodes.length) {
            updateNodeTemplate(childNodes.item(index), resolver)
        }
    }

    @Throws(Exception::class)
    private fun getXMLNodeFrom(result: Value): Node {
        var newNode: Node
        if (result is XMLValue) {
            val resultDocument = result.value as Document
            newNode = resultDocument.documentElement
            newNode = newNode.cloneNode(true)
        } else newNode = parseXML(result.toString()).documentElement
        return newNode
    }

    @Throws(Exception::class)
    private fun updateAttributes(attributes: NamedNodeMap, resolver: Resolver) {
        for (index in 0 until attributes.length) {
            val attribute = attributes.item(index)
            val nodeValue = attribute.nodeValue
            attribute.nodeValue = generateValue(nodeValue, resolver).toString()
        }
    }

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        val newDocument = copyOfDocument()
        val newRoot: Node = newDocument.documentElement
        updateBasedOnRow(newRoot, row, resolver)
        return listOf(XMLPattern(newDocument))
    }

    override fun parse(value: String, resolver: Resolver): Value = XMLValue(parseXML(value))

    @Throws(Throwable::class)
    private fun updateBasedOnRow(node: Node, row: Row, resolver: Resolver) {
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
                        is Result.Failure -> result.add("\"The value \" + row.getField(parentNodeName) + \" does not match the pattern \" + node.nodeValue")
                        else -> {
                        }
                    }
                }
                node.nodeValue = row.getField(parentNodeName).toString()
            } else {
                val value = node.nodeValue
                node.nodeValue = when {
                    isPatternToken(value) -> resolver.generateFromAny(withoutRestTokenForXML(value), resolver).toString()
                    else -> value
                }
            }
        }
    }

    private fun withoutRestTokenForXML(value: String): String =
        when {
            isPatternToken(value) -> withoutRestToken(value)
            else -> value
        }

    @Throws(Exception::class)
    private fun updateAttributesBasedOnRow(node: Node, row: Row, resolver: Resolver) {
        val attributes = node.attributes
        for (i in 0 until attributes.length) {
            val item = attributes.item(i)
            if (row.containsField(item.nodeName)) {
                item.nodeValue = row.getField(item.nodeName).toString()
            } else {
                val value = node.nodeValue
                node.nodeValue = generateValue(value, resolver).toString()
            }
        }
    }
}