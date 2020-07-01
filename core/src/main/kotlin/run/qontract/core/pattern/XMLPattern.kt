package run.qontract.core.pattern

import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node
import org.w3c.dom.Node.ELEMENT_NODE
import org.w3c.dom.Node.TEXT_NODE
import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.mismatchResult
import run.qontract.core.resultReport
import run.qontract.core.utilities.parseXML
import run.qontract.core.utilities.xmlToString
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value
import run.qontract.core.value.XMLValue
import java.util.*

data class XMLPattern(val node: Node) : Pattern {
    override val pattern = xmlToString(node)

    constructor(bodyContent: String): this(parseXML(bodyContent).documentElement)

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
            if(sampleData !is XMLValue)
            return mismatchResult(this, sampleData)

        return when (val result = matchesNode(node, sampleData.node, resolver)) {
            is Result.Failure -> result.reason("XML did not match")
            else -> result
        }
    }

    private fun matchesNode(patternNode: Node, sampleNode: Node, resolver: Resolver): Result {
        if (patternNode.nodeName != sampleNode.nodeName) {
            return if (isPatternToken(patternNode.nodeValue)) {
                val pattern = resolver.getPattern(patternNode.nodeValue)
                val sampleValue = XMLValue(sampleNode)

                when (val result = resolver.matchesPattern(sampleNode.nodeName, pattern, sampleValue)) {
                    is Result.Failure -> result.reason("Node ${sampleNode.nodeName} did not match. Expected: ${patternNode.nodeValue} Actual: ${sampleNode.nodeValue}")
                    else -> result
                }
            } else {
                Result.Failure("${patternNode.nodeValue} is not a type specifier")
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
                return matchRepeatingNodes(firstPatternNode, sampleNode, resolver)
            }
            if (patternChildNodes.length != sampleChildNodes.length)
                return Result.Failure("Node ${sampleNode.nodeName} does not have matching number of children. Expected: ${patternChildNodes.length} Actual: ${sampleChildNodes.length}")
            for (index in 0 until patternChildNodes.length) {
                val result = matchesNode(patternChildNodes.item(index), sampleChildNodes.item(index), resolver)
                if (result is Result.Failure) return result
            }
            return Result.Success()
        }
        val patternValue = patternNode.nodeValue
        val sampleValue = sampleNode.nodeValue
        val key = patternNode.parentNode.nodeName

        return when {
            isPatternToken(patternValue) -> try {
                val resolvedPattern = resolver.getPattern(patternValue)
                val resolvedValue = resolvedPattern.parse(sampleValue, resolver)

                when (val result = resolver.matchesPattern(key, resolvedPattern, resolvedValue)) {
                    is Result.Failure -> result.reason("Node $key did not match. Expected: $patternValue Actual: $sampleValue")
                    else -> result
                }
            } catch(e: ContractException) {
                e.failure()
            }
            else -> when (patternValue) {
                sampleValue -> Result.Success()
                else -> Result.Failure("Expected value $patternValue, but found value $sampleValue")
            }
        }
    }

    private fun matchRepeatingNodes(pattern: Node, sample: Node, resolver: Resolver): Result {
        val sampleChildNodes = sample.childNodes
        val newPattern = pattern.cloneNode(true)
        newPattern.nodeValue = withoutListToken(newPattern.nodeValue)

        return 0.until(sampleChildNodes.length).asSequence().map { index ->
            matchesNode(newPattern, sampleChildNodes.item(index), resolver)
        }.find { it is Result.Failure } ?: Result.Success()
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
                    val resolvedValue = try { resolvedPattern.parse(sampleValue, resolver) } catch(e: Throwable) { return false }

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
        } else if(node.nodeType == TEXT_NODE) {
            val nodeValue = node.nodeValue
            node.nodeValue = generateValue(node.nodeName, nodeValue, resolver)
        }
    }

    private fun updateChildNodes(parentNode: Node, resolver: Resolver) {
        val childNodes = parentNode.childNodes
        if (childNodes.length > 0 && isRepeatingPattern(childNodes.item(0).nodeValue)) {
            val repeatingPattern = childNodes.item(0)
            parentNode.removeChild(repeatingPattern)
            val pattern = withoutListToken(repeatingPattern.nodeValue)
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
                attribute.nodeValue = generateValue(attribute.nodeName, nodeValue, resolver)
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
    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver): Result {
        when {
            otherPattern is ExactValuePattern -> return otherPattern.fitsWithin(listOf(this), otherResolver, thisResolver)
            otherPattern !is XMLPattern -> return Result.Failure("Expected XMLPattern")
            node.nodeName != otherPattern.node.nodeName -> return Result.Failure("Expected a node named ${node.nodeName}, but got ${otherPattern.node.nodeName} instead.")
            else -> {
                for (i in (0.until(node.attributes?.length ?: 0))) {
                    val thisAttribute = node.attributes.item(i)
                    val otherAttribute = otherPattern.node.attributes.getNamedItem(thisAttribute.nodeName)
                            ?: return Result.Failure("Encompassing type has attribute ${thisAttribute.nodeName} but smaller didn't.")

                    val result = attributeEncompasses(thisAttribute, otherAttribute, thisResolver, otherResolver)
                    if (result is Result.Failure) {
                        return result.breadCrumb(thisAttribute.nodeName)
                    }
                }

                val (theseEncompassables, otherEncompassables) = if (containsRepeatingPattern(node)) {
                    val others = otherPattern.getEncompassables(otherResolver).map { resolvedHop(it, otherResolver) }
                    val these = getEncompassables(others.size, thisResolver).map { resolvedHop(it, thisResolver) }
                    Pair(these, others)
                } else {
                    val others = otherPattern.getEncompassables(node.childNodes.length, otherResolver).map { resolvedHop(it, otherResolver) }
                    if (others.size != node.childNodes.length)
                        return Result.Failure("The lengths of the two XML types are unequal.")

                    val these = getEncompassables(thisResolver).map { resolvedHop(it, thisResolver) }
                    Pair(these, others)
                }

                return theseEncompassables.zip(otherEncompassables).map { (thisOne, otherOne) ->
                    when {
                        otherOne is ExactValuePattern && otherOne.pattern is StringValue -> {
                            ExactValuePattern(thisOne.parse(otherOne.pattern.toStringValue(), thisResolver))
                        }
                        else -> otherOne
                    }.let{ otherOneAdjustedForExactValue -> thisOne.encompasses(otherOneAdjustedForExactValue, thisResolver, otherResolver) }
                }.find { it is Result.Failure } ?: Result.Success()
            }
        }
    }

    private fun getEncompassables(size: Int, resolver: Resolver): List<Pattern> = when {
        containsRepeatingPattern(node) -> {
            val xmlPattern = resolver.getPattern(withoutListToken(node.firstChild.nodeValue))
            0.until(size).map { xmlPattern }
        }
        containsPattern(node) -> {
            listOf(resolver.getPattern(node.firstChild.nodeValue))
        }
        containsTextNode(node) -> {
            listOf(parsedPattern(node.firstChild.nodeValue))
        }
        else -> {
            0.until(node.childNodes?.length ?: 0).map {
                XMLPattern(node.childNodes.item(it))
            }
        }
    }

    private fun containsTextNode(node: Node): Boolean = node.hasChildNodes() && node.firstChild.nodeName == "#text"

    private fun containsPattern(node: Node): Boolean =
            node.hasChildNodes() && node.childNodes.length == 1 && node.firstChild.nodeName == "#text" && isPatternToken(node.firstChild.nodeValue)

    private fun getEncompassables(resolver: Resolver): List<Pattern> = when {
        node.hasChildNodes() && node.childNodes.length == 1 && node.firstChild.nodeName == "#text" && isPatternToken(node.firstChild.nodeValue) -> listOf(resolver.getPattern(withoutListToken(node.firstChild.nodeValue)))
        containsTextNode(node) -> {
            listOf(parsedPattern(node.firstChild.nodeValue))
        }
        else -> {
            0.until(node.childNodes?.length ?: 0).map {
                XMLPattern(node.childNodes.item(it))
            }
        }
    }

    private fun containsRepeatingPattern(node: Node): Boolean =
            node.hasChildNodes() && node.childNodes.length == 1 && node.firstChild.nodeName == "#text" && isRepeatingPattern(node.firstChild.nodeValue)

    private fun attributeEncompasses(thisAttribute: Node, otherAttribute: Node, thisResolver: Resolver, otherResolver: Resolver): Result {
        val bigger = if(isPatternToken(thisAttribute.nodeValue)) thisResolver.getPattern(thisAttribute.nodeValue) else parsedPattern(thisAttribute.nodeValue)
        val smaller = if(isPatternToken(thisAttribute.nodeValue)) otherResolver.getPattern(otherAttribute.nodeValue) else parsedPattern(otherAttribute.nodeValue)

        return bigger.encompasses(smaller, thisResolver, otherResolver)
    }

    override val typeName: String = "xml"

    private fun updateBasedOnRow(node: Node, row: Row, resolver: Resolver) {
        attempt(breadCrumb = node.nodeName) {
            if (node.hasAttributes()) {
                updateAttributesBasedOnRow(node, row, resolver)
            }
            if (node.hasChildNodes() && (node.childNodes.length > 1 || node.firstChild.nodeName != "#text")) {
                val childNodes = node.childNodes
                for (index in 0 until childNodes.length) {
                    updateBasedOnRow(childNodes.item(index), row, resolver)
                }
            } else if(node.hasChildNodes() && node.firstChild.nodeName == "#text") {
                val nodeName = node.nodeName
                val nodeValue = node.firstChild.nodeValue

                if (row.containsField(nodeName)) {
                    if (isPatternToken(nodeValue)) {
                        val nodePattern = resolver.getPattern(nodeValue)

                        val rowValue = row.getField(nodeName)

                        when {
                            isPatternToken(rowValue) -> {
                                val rowPattern = resolver.getPattern(rowValue)
                                when(val result = nodePattern.encompasses(rowPattern, resolver, resolver)) {
                                    is Result.Success -> putValueIntoNode(resolver.generate(nodeName, rowPattern), node)
                                    else -> throw ContractException(resultReport(result))
                                }
                            }
                            else -> {
                                putValueIntoNode(nodePattern.parse(rowValue, resolver), node)
                            }
                        }
                    }
                    else
                        node.firstChild.nodeValue = row.getField(nodeName)
                } else {
                    when {
                        isRepeatingPattern(node.firstChild.nodeValue) ->
                            putValuesIntoNode(nodeName, resolvedHop(resolver.getPattern(withoutListToken(node.firstChild.nodeValue)), resolver), node, resolver)
                        isPatternToken(node.firstChild.nodeValue) ->
                            putValueIntoNode(resolver.generate(nodeName, resolver.getPattern(node.firstChild.nodeValue)), node)
                    }
                }
            }
        }
    }

    private fun putValuesIntoNode(key: String, pattern: Pattern, node: Node, resolver: Resolver) {
        if(pattern !is XMLPattern) throw ContractException("Only XML types can be used within an XML type. ${pattern.typeName} is not an XML type.")
        node.removeChild(node.firstChild)

        repeat(randomNumber(10)) {
            val value = resolver.generate(key, pattern)
            if(value !is XMLValue) throw ContractException("Only XML types can be used within an XML type.  ${pattern.typeName} is not an XML type.")

            val nodeCopy = node.ownerDocument.importNode(value.node, true)
            node.appendChild(nodeCopy)
        }
    }

    private fun putValueIntoNode(newNodeValue: Value, node: Node) {
        if(newNodeValue is XMLValue) {
            node.removeChild(node.firstChild)
            val nodeCopy = node.ownerDocument.importNode(newNodeValue.node, true)
            node.appendChild(nodeCopy)
        } else {
            node.firstChild.nodeValue = newNodeValue.toStringValue()
        }
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
                    node.nodeValue = generateValue(node.nodeName, value, resolver)
                }
            }
        }
    }
}

internal fun generateValue(key: String, value: String, resolver: Resolver): String {
    return if (isPatternToken(value)) {
        resolver.generate(key, parsedPattern(value)).toStringValue()
    } else value
}
