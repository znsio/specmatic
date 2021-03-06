package run.qontract.core.pattern

import run.qontract.core.*
import run.qontract.core.utilities.mapZip
import run.qontract.core.utilities.parseXML
import run.qontract.core.value.*
import run.qontract.core.wsdl.MULTIPLE_ATTRIBUTE_VALUE
import run.qontract.core.wsdl.OCCURS_ATTRIBUTE_NAME
import run.qontract.core.wsdl.OPTIONAL_ATTRIBUTE_VALUE

const val QONTRACT_XML_ATTRIBUTE_PREFIX = "qontract_"
const val TYPE_ATTRIBUTE = "qontract_type"

fun toTypeData(node: XMLNode): XMLTypeData = XMLTypeData(node.name, node.realName, attributeTypeMap(node), nodeTypes(node))

private fun nodeTypes(node: XMLNode): List<Pattern> {
    return node.childNodes.map {
        it.exactMatchElseType()
    }
}

private fun attributeTypeMap(node: XMLNode): Map<String, Pattern> {
    return node.attributes.mapValues { (key, value) ->
        when {
            value.isPatternToken() -> DeferredPattern(value.trimmed().toStringValue(), key)
            else -> ExactValuePattern(value)
        }
    }
}

data class XMLPattern(override val pattern: XMLTypeData = XMLTypeData(realName = ""), override val typeAlias: String? = null) : Pattern, SequenceType {
    constructor(node: XMLNode, typeAlias: String? = null): this(toTypeData(node), typeAlias)
    constructor(xmlString: String, typeAlias: String? = null): this(toXMLNode(parseXML(xmlString)), typeAlias)

    override fun matches(sampleData: List<Value>, resolver: Resolver): ConsumeResult<Value> {
        val xmlValues = sampleData.filterIsInstance<XMLValue>()
        if(xmlValues.size != sampleData.size)
            return ConsumeResult(Result.Failure("XMLPattern can only match XML values"))

        return when {
            pattern.isOptionalNode() -> matchesOptionalNode(xmlValues, resolver)
            pattern.isMultipleNode() -> matchesMultipleNodes(xmlValues, resolver)
            else -> matchesRequiredNode(xmlValues, sampleData, resolver)
        }
    }

    private fun matchesRequiredNode(
        xmlValues: List<XMLValue>,
        sampleData: List<Value>,
        resolver: Resolver
    ): ConsumeResult<Value> = if (xmlValues.isEmpty())
        ConsumeResult(Result.Failure("Didn't get enough values", breadCrumb = this.pattern.name), sampleData)
    else
        ConsumeResult(matches(xmlValues.first(), resolver), xmlValues.drop(1))

    private fun matchesMultipleNodes(
        xmlValues: List<XMLValue>,
        resolver: Resolver
    ): ConsumeResult<Value> {
        val remainder = xmlValues.dropWhile {
            matches(it, resolver) is Result.Success
        }

        return if (remainder.isNotEmpty() && remainder.first().let { it is XMLNode && it.name == this.pattern.name }) {
            ConsumeResult(matches(remainder.first(), resolver), remainder)
        }
        else if (remainder.isNotEmpty()) {
            val provisionalError = ProvisionalError(
                matches(remainder.first(), resolver) as Result.Failure,
                this,
                remainder.first())
            ConsumeResult(Result.Success(), remainder, provisionalError)
        } else {
            ConsumeResult(Result.Success(), remainder)
        }
    }

    private fun matchesOptionalNode(
        xmlValues: List<XMLValue>,
        resolver: Resolver
    ): ConsumeResult<Value> = if (xmlValues.isEmpty())
        ConsumeResult(Result.Success())
    else {
        val xmlValue = xmlValues.first()
        val result = matches(xmlValue, resolver)

        when (result) {
            is Result.Success -> ConsumeResult(Result.Success(), xmlValues.drop(1))
            is Result.Failure -> when {
                xmlValue is XMLNode && xmlValue.name == this.pattern.name -> ConsumeResult(result, xmlValues)
                else -> ConsumeResult(Result.Success(), xmlValues, ProvisionalError(result, this, xmlValue))
            }
        }
    }

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData !is XMLNode)
            return Result.Failure("Expected xml, got ${sampleData?.displayableType()}").breadCrumb(pattern.name)

        val nameResult = matchName(sampleData)

        val matchingType = if(this.pattern.attributes.containsKey(TYPE_ATTRIBUTE)) {
            val typeName =
                (this.pattern.attributes.getValue(TYPE_ATTRIBUTE) as ExactValuePattern).pattern.toStringValue()
            val xmlType = (resolver.getPattern("($typeName)") as XMLPattern)
            xmlType.copy(pattern = xmlType.pattern.copy(name = this.pattern.name, realName = this.pattern.realName))
        } else {
            this
        }

        return nameResult.ifSuccess {
            matchingType.matchNamespaces(sampleData)
        }.ifSuccess {
            matchingType.matchAttributes(sampleData, resolver)
        }.ifSuccess {
            matchingType.matchNodes(sampleData, resolver)
        }
    }

    private fun matchNodes(
        sampleData: XMLNode,
        resolver: Resolver
    ): Result {
        val results = pattern.nodes.scanIndexed(
            ConsumeResult(
                Result.Success(),
                sampleData.childNodes
            )
        ) { index, consumeResult, type ->
            when (val resolvedType = resolvedHop(type, resolver)) {
                is ListPattern -> ConsumeResult(
                    resolvedType.matches(
                        this.listOf(
                            consumeResult.remainder.subList(index, pattern.nodes.indices.last),
                            resolver
                        ), resolver
                    ),
                    emptyList()
                )
                else -> {
                    try {
                        if (sampleData.childNodes.size == 1 && consumeResult.remainder.size == 1 && sampleData.childNodes.first() is StringValue) {
                            val childValue = when (val childNode = sampleData.childNodes[index]) {
                                is StringValue -> when {
                                    childNode.isPatternToken() -> childNode.trimmed()
                                    else -> try {
                                        resolvedType.parse(childNode.string, resolver)
                                    } catch (e: Throwable) {
                                        throw ContractException("Couldn't read value ${childNode.string} as type ${resolvedType.pattern}")
                                    }
                                }
                                else -> childNode
                            }

                            val factKey = if (childValue is XMLNode) childValue.name else null
                            ConsumeResult(resolver.matchesPattern(factKey, resolvedType, childValue), emptyList())
                        } else if (expectingEmpty(sampleData, resolvedType, resolver)) {
                            ConsumeResult(Result.Success())
                        } else {
                            resolvedType.matches(consumeResult.remainder, resolver).cast("xml")
                        }
                    } catch (e: ContractException) {
                        println(e.errorMessage)
                        ConsumeResult(e.failure(), consumeResult.remainder)
                    }
                }
            }
        }

        return failureFrom(results) ?: Result.Success()
    }

    private fun failureFrom(results: List<ConsumeResult<XMLValue>>): Result? {
        val nodeStructureMismatchError = results.find {
            it.result is Result.Failure
        }?.result?.breadCrumb(this.pattern.name)

        val nothingEvenCameCloseError = lazy {
            when {
                results.isNotEmpty() && results.last().remainder.isNotEmpty() -> {
                    results.find {
                        it.provisionalError != null
                    }?.provisionalError?.let {
                        val nodeValue = it.value
                        if(nodeValue is XMLNode)
                            it.result.breadCrumb(nodeValue.name)
                        else
                            it.result.breadCrumb(this.pattern.name)
                    }
                        ?: Result.Failure("Not all child nodes matched after optional and multiple nodes. ConsumeResult list: $results").breadCrumb(this.pattern.name)
                }
                else -> null
            }
        }

        return (nodeStructureMismatchError ?: nothingEvenCameCloseError.value)
    }

    private fun matchNamespaces(sampleData: XMLNode): Result {
        if(pattern.attributes.any { it.key == "xmlns" || it.key.startsWith("xmlns:") }) {
            val patternXmlnsValues = pattern.attributes.entries.filter { it.key == "xmlns" || it.key.startsWith("xmlns:") }.map { (_, attributePattern) ->
                when(attributePattern) {
                    is ExactValuePattern -> attributePattern.pattern.toStringValue()
                    else -> attributePattern.pattern.toString()
                }
            }.toSet()
            val sampleXmlnsValues = sampleData.attributes.entries.filter { it.key == "xmlns" || it.key.startsWith("xmlns:") }.map { (_, attributeValue) ->
                attributeValue.toStringValue()
            }.toSet()

            val missing = patternXmlnsValues - sampleXmlnsValues
            if(missing.isNotEmpty())
                Result.Failure("In node named ${pattern.name}, the following namespaces were missing: $missing", breadCrumb = pattern.name)

            val extra = sampleXmlnsValues - patternXmlnsValues
            if(extra.isNotEmpty())
                return Result.Failure("In node named ${pattern.name}, the following unexpected namespaces were found: $extra", breadCrumb = pattern.name)
        }

        return Result.Success()
    }

    private fun matchAttributes(sampleData: XMLNode, resolver: Resolver): Result {
        val patternAttributesWithoutXmlns = pattern.attributes.filterNot { it.key == "xmlns" || it.key.startsWith("xmlns:") || it.key.startsWith(QONTRACT_XML_ATTRIBUTE_PREFIX) }
        val sampleAttributesWithoutXmlns = sampleData.attributes.filterNot { it.key == "xmlns" || it.key.startsWith("xmlns:") || it.key.startsWith(QONTRACT_XML_ATTRIBUTE_PREFIX) }

        val missingKey = resolver.findMissingKey(ignoreXMLNamespaces(patternAttributesWithoutXmlns), ignoreXMLNamespaces(sampleAttributesWithoutXmlns), ::validateUnexpectedKeys)
        if(missingKey != null)
            return missingKeyToResult(missingKey, "attribute").breadCrumb(pattern.name)

        return matchAttributes(patternAttributesWithoutXmlns, sampleAttributesWithoutXmlns, resolver)
    }

    private fun matchName(sampleData: XMLNode): Result {
        if(sampleData.name != pattern.name)
            return mismatchResult(pattern.name, sampleData.name).breadCrumb(pattern.name)

        return Result.Success()
    }

    private fun matchAttributes(patternAttributesWithoutXmlns: Map<String, Pattern>, sampleAttributesWithoutXmlns: Map<String, StringValue>, resolver: Resolver): Result =
            mapZip(ignoreXMLNamespaces(patternAttributesWithoutXmlns), ignoreXMLNamespaces(sampleAttributesWithoutXmlns)).asSequence().map { (key, patternValue, sampleValue) ->
                try {
                    val resolvedValue: Value = when {
                        sampleValue.isPatternToken() -> sampleValue.trimmed()
                        else -> patternValue.parse(sampleValue.string, resolver)
                    }
                    resolver.matchesPattern(key, patternValue, resolvedValue)
                } catch (e: ContractException) {
                    e.failure()
                }.breadCrumb(key).breadCrumb(pattern.name)
            }.find { it is Result.Failure } ?: Result.Success()

    private fun <ValueType> ignoreXMLNamespaces(attributes: Map<String, ValueType>): Map<String, ValueType> =
            attributes.filterNot { it.key.toLowerCase().startsWith("xmlns:") }

    private fun expectingEmpty(sampleData: XMLNode, type: Pattern, resolver: Resolver): Boolean {
        val resolvedPatternSet = type.patternSet(resolver).map { resolvedHop(it, resolver) }
        return sampleData.childNodes.isEmpty() && pattern.nodes.size == 1 && (EmptyStringPattern in resolvedPatternSet || StringPattern in resolvedPatternSet)
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return XMLNode("", "", emptyMap(), valueList.map { it as XMLNode }, "", emptyMap())
    }

    override fun generate(resolver: Resolver): XMLNode {
        val name = pattern.name

        val nonQontractAttributes = pattern.attributes.filterNot { it.key.startsWith(QONTRACT_XML_ATTRIBUTE_PREFIX) }

        val newAttributes = nonQontractAttributes.mapKeys { entry ->
            withoutOptionality(entry.key)
        }.mapValues { (key, pattern) ->
            attempt(breadCrumb = "$name.$key") { resolver.generate(key, pattern) }
        }.mapValues {
            StringValue(it.value.toStringValue())
        }

        val nodes = pattern.nodes.map { resolvedHop(it, resolver) }.map { pattern ->
            attempt(breadCrumb = name) {
                when {
                    pattern is ListPattern -> (pattern.generate(resolver) as XMLNode).childNodes
                    pattern is XMLPattern && pattern.occurMultipleTimes() -> 0.until(randomNumber(RANDOM_NUMBER_CEILING)).map {
                        pattern.generate(resolver)
                    }
                    else -> listOf(pattern.generate(resolver))
                }
            }
        }.flatten().map {
            when(it) {
                is XMLValue -> it
                else -> StringValue(it.toStringValue())
            }
        }

        return XMLNode(pattern.realName, newAttributes, nodes)
    }

    override fun newBasedOn(row: Row, resolver: Resolver): List<XMLPattern> {
        return forEachKeyCombinationIn(pattern.attributes, row) { attributePattern ->
            attempt(breadCrumb = this.pattern.name) {
                newBasedOn(attributePattern, row, resolver).map {
                    it.mapKeys { entry -> withoutOptionality(entry.key) }
                }
            }
        }.flatMap { newAttributes ->
            val newNodesList = when {
                row.containsField(pattern.name) -> {
                    attempt(breadCrumb = pattern.name) {
                        val dereferenced = dereferenceType(resolver)
                        if (dereferenced.pattern.nodes.isEmpty())
                            throw ContractException("Node ${pattern.name} is empty but an example with this name exists")

                        val parsedData = dereferenced.pattern.nodes[0].parse(row.getField(dereferenced.pattern.name), resolver)
                        val matchResult = dereferenced.pattern.nodes[0].matches(parsedData, resolver)

                        if (matchResult is Result.Failure)
                            throw ContractException(resultReport(matchResult))

                        listOf(listOf(ExactValuePattern(parsedData)))
                    }
                }
                else -> {
                    listCombinations(pattern.nodes.map { childPattern ->
                        attempt(breadCrumb = this.pattern.name) {
                            when(childPattern) {
                                is XMLPattern -> {
                                    val dereferenced: XMLPattern = childPattern.dereferenceType(resolver)

                                    when {
                                        dereferenced.occurMultipleTimes() -> {
                                            childPattern.newBasedOn(row, resolver)
                                        }
                                        dereferenced.isOptional() -> {
                                            childPattern.newBasedOn(row, resolver).plus(null)
                                        }
                                        else -> childPattern.newBasedOn(row, resolver)
                                    }
                                }
                                else -> childPattern.newBasedOn(row, resolver)
                            }
                        }
                    })
                }
            }

            newNodesList.map { newNodes ->
                XMLPattern(XMLTypeData(pattern.name, pattern.realName, newAttributes, newNodes))
            }
        }
    }

    private fun dereferenceType(resolver: Resolver): XMLPattern {
        if(!hasType()) {
            return this
        }

        val qontractType = pattern.attributes["qontract_type"]
        val resolved = resolver.getPattern("($qontractType)") as XMLPattern
        val qontractAttributes = this.pattern.attributes.filterKeys { it.startsWith(QONTRACT_XML_ATTRIBUTE_PREFIX) }
        return resolved.copy(pattern = resolved.pattern.copy(name = this.pattern.name, attributes = resolved.pattern.attributes.plus(qontractAttributes)))
    }

    fun hasType(): Boolean = pattern.attributes.containsKey(TYPE_ATTRIBUTE)

    fun occurMultipleTimes(): Boolean = occursAttributeValue() == MULTIPLE_ATTRIBUTE_VALUE

    private fun occursAttributeValue() =
        (pattern.attributes[OCCURS_ATTRIBUTE_NAME] as ExactValuePattern?)?.pattern?.toStringValue()

    fun isOptional(): Boolean = occursAttributeValue() == OPTIONAL_ATTRIBUTE_VALUE

    override fun parse(value: String, resolver: Resolver): Value {
        return toXMLNode(parseXML(value))
    }

    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack): Result {
        val otherResolvedPattern = resolvedHop(otherPattern, otherResolver)

        return when (otherResolvedPattern) {
            is ExactValuePattern -> otherResolvedPattern.fitsWithin(listOf(this), otherResolver, thisResolver, typeStack)
            is XMLPattern -> nodeNamesShouldBeEqual(otherResolvedPattern).ifSuccess {
                mapEncompassesMap(pattern.attributes, otherResolvedPattern.pattern.attributes, thisResolver, otherResolver)
            }.ifSuccess {
                val theseMembers = this.memberList
                val otherMembers = otherResolvedPattern.memberList

                otherShouldNotBeEndless(otherMembers).ifSuccess {
                    val others = otherMembers.getEncompassables(otherResolver)
                    val these = theseMembers.getEncompassables(thisResolver)

                    these.runningFold(ConsumeResult(others)) { acc, thisOne ->
                        thisOne.encompasses(adaptEmpty(acc), thisResolver, otherResolver, "The lengths of the two XML types are unequal", typeStack)
                    }.find { it.result is Result.Failure }?.result ?: Result.Success()
                }
            }
            else -> mismatchResult(this, otherResolvedPattern)
        }.breadCrumb(this.pattern.name)
    }

    private fun nodeNamesShouldBeEqual(otherResolvedPattern: XMLPattern) = when {
        pattern.name != otherResolvedPattern.pattern.name ->
            Result.Failure("Expected a node named ${pattern.name}, but got ${otherResolvedPattern.pattern.name} instead.")
        else -> Result.Success()
    }

    private fun otherShouldNotBeEndless(otherMemberList: MemberList): Result =
            when {
                otherMemberList.isEndless() -> Result.Failure("Finite list is not a superset of an infinite list")
                else -> Result.Success()
            }

    private fun adaptEmpty(acc: ConsumeResult<Pattern>) =
            acc.remainder.ifEmpty { listOf(EmptyStringPattern) }

    override val memberList: MemberList
        get() = MemberList(pattern.nodes, null)

    override val typeName: String = "xml"

    fun toGherkinString(additionalIndent: String = "", indent: String = ""): String {
        return pattern.toGherkinString(additionalIndent, indent)
    }

    fun toGherkinXMLNode(): XMLNode {
        return pattern.toGherkinishNode()
    }
}
