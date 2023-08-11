package `in`.specmatic.core.pattern

import `in`.specmatic.core.*
import `in`.specmatic.core.Result.Failure
import `in`.specmatic.core.Result.Success
import `in`.specmatic.core.utilities.mapZip
import `in`.specmatic.core.utilities.parseXML
import `in`.specmatic.core.value.*
import `in`.specmatic.core.wsdl.parser.message.MULTIPLE_ATTRIBUTE_VALUE
import `in`.specmatic.core.wsdl.parser.message.OCCURS_ATTRIBUTE_NAME

const val SPECMATIC_XML_ATTRIBUTE_PREFIX = "${APPLICATION_NAME_LOWER_CASE}_"
const val TYPE_ATTRIBUTE_NAME = "specmatic_type"
const val SOAP_BODY = "body"
const val SOAP_FAULT = "fault"

fun toTypeData(node: XMLNode): XMLTypeData = XMLTypeData(node.name, node.realName, attributeTypeMap(node), nodeTypes(node))

private fun nodeTypes(node: XMLNode): List<Pattern> {
    return node.childNodes.map {
        it.exactMatchElseType()
    }
}

private fun attributeTypeMap(node: XMLNode): Map<String, Pattern> {
    return node.attributes.mapValues { (key, value) ->
        when {
            value.isPatternToken() -> DeferredPattern(value.trimmed().toStringLiteral(), key)
            else -> ExactValuePattern(value)
        }
    }
}

data class XMLPattern(override val pattern: XMLTypeData = XMLTypeData(realName = ""), override val typeAlias: String? = null) : Pattern, SequenceType {
    constructor(node: XMLNode, typeAlias: String? = null) : this(toTypeData(node), typeAlias)
    constructor(xmlString: String, typeAlias: String? = null) : this(toXMLNode(parseXML(xmlString)), typeAlias)

    fun toPrettyString(): String {
        return pattern.toGherkinishNode().toPrettyStringValue()
    }

    override fun matches(sampleData: List<Value>, resolver: Resolver): ConsumeResult<Value, Value> {
        val xmlValues = sampleData.filterIsInstance<XMLValue>()
        if (xmlValues.size != sampleData.size)
            return ConsumeResult(Failure("XMLPattern can only match XML values"))

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
    ): ConsumeResult<Value, Value> = if (xmlValues.isEmpty())
        ConsumeResult(Failure("Didn't get enough values", breadCrumb = this.pattern.name), sampleData)
    else
        ConsumeResult(matches(xmlValues.first(), resolver), xmlValues.drop(1))

    private fun matchesMultipleNodes(
            xmlValues: List<XMLValue>,
            resolver: Resolver
    ): ConsumeResult<Value, Value> {
        val remainder = xmlValues.dropWhile {
            matches(it, resolver) is Success
        }

        return if (remainder.isNotEmpty() && remainder.first().let { it is XMLNode && it.name == this.pattern.name }) {
            ConsumeResult(matches(remainder.first(), resolver), remainder)
        } else if (remainder.isNotEmpty()) {
            val provisionalError = ProvisionalError<Value>(
                    matches(remainder.first(), resolver) as Failure,
                    this,
                    remainder.first()
            )
            ConsumeResult(Success(), remainder, provisionalError)
        } else {
            ConsumeResult(Success(), remainder)
        }
    }

    private fun matchesOptionalNode(
            xmlValues: List<XMLValue>,
            resolver: Resolver
    ): ConsumeResult<Value, Value> = if (xmlValues.isEmpty())
        ConsumeResult(Success())
    else {
        val xmlValue = xmlValues.first()
        val result = matches(xmlValue, resolver)

        when (result) {
            is Success -> ConsumeResult(Success(), xmlValues.drop(1))
            is Failure -> when {
                xmlValue is XMLNode && xmlValue.name == this.pattern.name -> ConsumeResult(result, xmlValues)
                else -> ConsumeResult(Success(), xmlValues, ProvisionalError(result, this, xmlValue))
            }
        }
    }

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if (sampleData !is XMLNode)
            return Failure("Expected xml, got ${sampleData?.displayableType()}").breadCrumb(pattern.name)

        if(pattern.isNillable()) {
            if(sampleData.childNodes.isEmpty())
                return Success()
        }

        val matchingType = if (this.pattern.attributes.containsKey(TYPE_ATTRIBUTE_NAME)) {
            val typeName = this.pattern.getAttributeValue(TYPE_ATTRIBUTE_NAME)
            val xmlType = (resolver.getPattern("($typeName)") as XMLPattern)
            xmlType.copy(pattern = xmlType.pattern.copy(name = this.pattern.name, realName = this.pattern.realName, attributes = this.pattern.attributes.filterKeys { it != TYPE_ATTRIBUTE_NAME }))
        } else {
            this
        }

        return matchName(sampleData, resolver).ifSuccess {
            matchingType.matchNamespaces(sampleData)
        }.ifSuccess {
            matchingType.matchAttributes(sampleData, resolver)
        }.ifSuccess {
            matchingType.matchNodes(sampleData, resolver)
        }.breadCrumb(pattern.name)
    }

    private fun matchNodes(
            sampleData: XMLNode,
            resolver: Resolver
    ): Result {
        if(sampleData.name.lowercase() == SOAP_BODY && sampleData.firstNode() is XMLNode && sampleData.firstNode()?.name?.lowercase() == SOAP_FAULT)
            return Success()

        val results = pattern.nodes.scanIndexed(
                ConsumeResult<XMLValue, Value>(
                        Success(),
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
                                    else -> {
                                        attempt(
                                                "Couldn't read value ${childNode.string} as type ${resolvedType.pattern}",
                                                breadCrumb = breadCrumbIfXMLTag(resolvedType)
                                        ) { resolvedType.parse(childNode.string, resolver) }
                                    }
                                }
                                else -> childNode
                            }

                            val factKey = if (childValue is XMLNode) childValue.name else null
                            ConsumeResult(resolver.matchesPattern(factKey, resolvedType, childValue), emptyList())
                        } else if (expectingEmpty(sampleData, resolvedType, resolver)) {
                            ConsumeResult(Success())
                        } else {
                            resolvedType.matches(consumeResult.remainder, resolver).cast("xml")
                        }
                    } catch (e: ContractException) {
                        ConsumeResult(e.failure(), consumeResult.remainder)
                    }
                }
            }
        }

        return failureFrom(results) ?: Success()
    }

    private fun breadCrumbIfXMLTag(resolvedType: Pattern): String {
        return when (resolvedType) {
            is XMLPattern -> resolvedType.pattern.name
            else -> ""
        }
    }

    private fun failureFrom(results: List<ConsumeResult<XMLValue, Value>>): Result? {
        val nodeStructureMismatchError = results.find {
            it.result is Failure
        }?.result

        val nothingEvenCameCloseError = lazy {
            when {
                results.isNotEmpty() && results.last().remainder.isNotEmpty() -> {
                    val unexpectedValue = results.last().remainder.first()
                    unexpectedValue.matchFailure()
                }
                else -> null
            }
        }

        return (nodeStructureMismatchError ?: nothingEvenCameCloseError.value)
    }

    private fun matchNamespaces(sampleData: XMLNode): Result {
        return PLACEHOLDER_USE_GIT_BLAME_TO_FIND_RELEVANT_COMMIT(Success(), "Removed namespace validation but we should put it back")
    }

    private fun matchAttributes(sampleData: XMLNode, resolver: Resolver): Result {
        val patternAttributesWithoutXmlns = pattern.attributes.filterNot {
            it.key == "xmlns" || it.key.startsWith("xmlns:") || it.key.startsWith(SPECMATIC_XML_ATTRIBUTE_PREFIX)
        }
        val sampleAttributesWithoutXmlns = sampleData.attributes.filterNot {
            it.key == "xmlns" || it.key.startsWith("xmlns:") || it.key.startsWith(SPECMATIC_XML_ATTRIBUTE_PREFIX)
        }

        val missingKey = resolver.findKeyError(
                ignoreXMLNamespaces(patternAttributesWithoutXmlns),
                ignoreXMLNamespaces(sampleAttributesWithoutXmlns)
        )
        if (missingKey != null)
            return missingKey.missingKeyToResult("attribute", resolver.mismatchMessages)

        return matchAttributes(patternAttributesWithoutXmlns, sampleAttributesWithoutXmlns, resolver)
    }

    private fun matchName(sampleData: XMLNode, resolver: Resolver): Result {
        if (sampleData.name != pattern.name)
            return mismatchResult(pattern.name, sampleData.name, resolver.mismatchMessages)

        return Success()
    }

    private fun matchAttributes(
            patternAttributesWithoutXmlns: Map<String, Pattern>,
            sampleAttributesWithoutXmlns: Map<String, StringValue>,
            resolver: Resolver
    ): Result =
            mapZip(
                    ignoreXMLNamespaces(patternAttributesWithoutXmlns),
                    ignoreXMLNamespaces(sampleAttributesWithoutXmlns)
            ).asSequence().map { (key, patternValue, sampleValue) ->
                try {
                    val resolvedValue: Value = when {
                        sampleValue.isPatternToken() -> sampleValue.trimmed()
                        else -> patternValue.parse(sampleValue.string, resolver)
                    }
                    resolver.matchesPattern(key, patternValue, resolvedValue)
                } catch (e: ContractException) {
                    e.failure()
                }.breadCrumb(key)
            }.find { it is Failure } ?: Success()

    private fun <ValueType> ignoreXMLNamespaces(attributes: Map<String, ValueType>): Map<String, ValueType> =
            attributes.filterNot { it.key.lowercase().startsWith("xmlns:") }

    private fun expectingEmpty(sampleData: XMLNode, type: Pattern, resolver: Resolver): Boolean {
        val resolvedPatternSet = type.patternSet(resolver).map { resolvedHop(it, resolver) }
        return sampleData.childNodes.isEmpty() && pattern.nodes.size == 1 && (EmptyStringPattern in resolvedPatternSet || StringPattern() in resolvedPatternSet)
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return XMLNode("", "", emptyMap(), valueList.map { it as XMLNode }, "", emptyMap())
    }

    override fun generate(resolver: Resolver): XMLNode {
        val name = pattern.name

        val nonQontractAttributes = pattern.attributes.filterNot { it.key.startsWith(SPECMATIC_XML_ATTRIBUTE_PREFIX) }

        val newAttributes = nonQontractAttributes.mapKeys { entry ->
            withoutOptionality(entry.key)
        }.mapValues { (key, pattern) ->
            attempt(breadCrumb = "$name.$key") {
                resolver.withCyclePrevention(pattern) { cyclePreventedResolver ->
                    cyclePreventedResolver.generate(key, pattern)
                }
            }
        }.mapValues {
            StringValue(it.value.toStringLiteral())
        }

        val nodes = pattern.nodes.asSequence().map {
            resolvedHop(it, resolver)
        }.map { pattern ->
            attempt(breadCrumb = name) {
                resolver.withCyclePrevention(pattern) { cyclePreventedResolver ->
                    when {
                        pattern is ListPattern -> (pattern.generate(cyclePreventedResolver) as XMLNode).childNodes
                        pattern is XMLPattern && pattern.occurMultipleTimes() ->
                            0.until(randomNumber(RANDOM_NUMBER_CEILING))
                                .map { pattern.generate(cyclePreventedResolver) }

                        else -> listOf(pattern.generate(cyclePreventedResolver))
                    }
                }
            }
        }.flatten().map {
            when (it) {
                is XMLValue -> it
                else -> StringValue(it.toStringLiteral())
            }
        }.toList()

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

                        val parsedData =
                                dereferenced.pattern.nodes[0].parse(row.getField(dereferenced.pattern.name), resolver)
                        val matchResult = dereferenced.pattern.nodes[0].matches(parsedData, resolver)

                        if (matchResult is Failure)
                            throw ContractException(matchResult.toFailureReport())

                        listOf(listOf(ExactValuePattern(parsedData)))
                    }
                }
                else -> {
                    listCombinations(pattern.nodes.map { childPattern ->
                        attempt(breadCrumb = this.pattern.name) {
                            when (childPattern) {
                                is XMLPattern -> {
                                    val dereferenced: XMLPattern = childPattern.dereferenceType(resolver)

                                    resolver.withCyclePrevention(dereferenced) { cyclePreventedResolver ->
                                        when {
                                            dereferenced.occurMultipleTimes() -> {
                                                dereferenced.newBasedOn(row, cyclePreventedResolver)
                                            }

                                            dereferenced.isOptional() -> {
                                                dereferenced.newBasedOn(row, cyclePreventedResolver).plus(null)
                                            }

                                            else -> dereferenced.newBasedOn(row, cyclePreventedResolver)
                                        }
                                    }
                                }
                                else -> resolver.withCyclePrevention(childPattern) { cyclePreventedResolver ->
                                    childPattern.newBasedOn(row, cyclePreventedResolver)
                                }
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

    override fun newBasedOn(resolver: Resolver): List<XMLPattern> {
        return allOrNothingCombinationIn(pattern.attributes) { attributePattern ->
            attempt(breadCrumb = this.pattern.name) {
                newBasedOn(attributePattern, resolver).map {
                    it.mapKeys { entry -> withoutOptionality(entry.key) }
                }
            }
        }.flatMap { newAttributes ->
            val newNodesList = allOrNothingListCombinations(pattern.nodes.map { childPattern ->
                attempt(breadCrumb = this.pattern.name) {
                    when (childPattern) {
                        is XMLPattern -> {
                            val dereferenced: XMLPattern = childPattern.dereferenceType(resolver)

                            resolver.withCyclePrevention(dereferenced) { cyclePreventedResolver ->
                                when {
                                    dereferenced.occurMultipleTimes() -> {
                                        dereferenced.newBasedOn(cyclePreventedResolver)
                                    }

                                    dereferenced.isOptional() -> {
                                        dereferenced.newBasedOn(cyclePreventedResolver).plus(null)
                                    }

                                    else -> dereferenced.newBasedOn(cyclePreventedResolver)
                                }
                            }
                        }
                        else -> resolver.withCyclePrevention(childPattern) { cyclePreventedResolver ->
                            childPattern.newBasedOn(cyclePreventedResolver)
                        }
                    }
                }
            })

            newNodesList.map { newNodes ->
                XMLPattern(XMLTypeData(pattern.name, pattern.realName, newAttributes, newNodes))
            }
        }
    }

    override fun negativeBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        return newBasedOn(row, resolver)
    }

    private fun dereferenceType(resolver: Resolver): XMLPattern {
        if (!hasType()) {
            return this
        }

        val qontractType = pattern.attributes[TYPE_ATTRIBUTE_NAME]
        val resolved = resolver.getPattern("($qontractType)") as XMLPattern
        val qontractAttributes = this.pattern.attributes.filterKeys { it.startsWith(SPECMATIC_XML_ATTRIBUTE_PREFIX) }
        return resolved.copy(
                pattern = resolved.pattern.copy(
                        name = this.pattern.name,
                        realName = this.pattern.realName,
                        attributes = resolved.pattern.attributes.plus(qontractAttributes)
                )
        )
    }

    private fun hasType(): Boolean = pattern.attributes.containsKey(TYPE_ATTRIBUTE_NAME)

    fun occurMultipleTimes(): Boolean = pattern.getNodeOccurrence() == NodeOccurrence.Multiple

    private fun isOptional(): Boolean = pattern.getNodeOccurrence() == NodeOccurrence.Optional

    override fun parse(value: String, resolver: Resolver): Value {
        return toXMLNode(parseXML(value))
    }

    override fun encompasses(
            otherPattern: Pattern,
            thisResolver: Resolver,
            otherResolver: Resolver,
            typeStack: TypeStack
    ): Result {
        val otherResolvedPattern = resolvedHop(otherPattern, otherResolver)

        return when (otherResolvedPattern) {
            is ExactValuePattern -> otherResolvedPattern.fitsWithin(
                    listOf(this),
                    otherResolver,
                    thisResolver,
                    typeStack
            )
            is XMLPattern -> nodeNamesShouldBeEqual(otherResolvedPattern).ifSuccess {
                mapEncompassesMap(
                        pattern.attributes.filterNot { it.key.startsWith(SPECMATIC_XML_ATTRIBUTE_PREFIX) || it.key.startsWith("xmlns:") },
                        otherResolvedPattern.pattern.attributes.filterNot { it.key.startsWith(SPECMATIC_XML_ATTRIBUTE_PREFIX) || it.key.startsWith("xmlns:") },
                        thisResolver,
                        otherResolver
                )
            }.ifSuccess {
                thisOccurrenceEncompassesTheOther(this, otherResolvedPattern)
            }.ifSuccess {
                val theseMembers = this.memberList
                val otherMembers = otherResolvedPattern.memberList

                val others = otherMembers.getEncompassables(otherResolver)
                val these = adapt(adaptFromList(theseMembers.getEncompassables(thisResolver), thisResolver), thisResolver)

                val adaptedOthers = adapt(others, otherResolver)

                val results =
                        these.runningFold(ConsumeResult<Pattern, Pattern>(adaptedOthers)) { consumeResult, thisOne ->
                            val unmatched = dropOneIfMatching(consumeResult.remainder) { otherOne ->
                                encompassResult(thisOne, otherOne, thisResolver, otherResolver, typeStack)
                            }

                            if (unmatched.size == consumeResult.remainder.size) {
                                val failure = encompassResult(
                                        thisOne,
                                        unmatched.first(),
                                        thisResolver,
                                        otherResolver,
                                        typeStack
                                ) as Failure
                                ConsumeResult(failure, unmatched)
                            } else {
                                ConsumeResult(Success(), unmatched)
                            }
                        }

                val failureResult = results.find { it.result is Failure }?.result

                failureResult ?: provisionalFailure(results) ?: Success()
            }
            else -> mismatchResult(this, otherResolvedPattern, thisResolver.mismatchMessages)
        }.breadCrumb(this.pattern.name)
    }

    private fun provisionalFailure(results: List<ConsumeResult<Pattern, Pattern>>): Failure? {
        if (results.isEmpty() || results.last().remainder.isEmpty())
            return null

        val provisionalError = results.map { it.provisionalError?.result }.filterIsInstance<Failure>().firstOrNull()

        return provisionalError ?: genericProvisionalFailure(results)
    }

    private fun genericProvisionalFailure(results: List<ConsumeResult<Pattern, Pattern>>): Failure {
        val unmatched = results.last().remainder

        val nodeDescriptor = when (unmatched.size) {
            1 -> "Node"
            else -> "Nodes"
        }

        val names = unmatched.joinToString(", ") {
            when (it) {
                is XMLPattern -> it.pattern.name
                else -> it.typeName
            }
        }

        return Failure("$nodeDescriptor named $names were not matched")
    }

    private fun dropOneIfMatching(remainder: List<Pattern>, test: (Pattern) -> Result): List<Pattern> {
        if(remainder.isEmpty())
            return remainder

        val first = remainder.first()

        return when(test(first)) {
            is Success -> remainder.drop(1)
            is Failure -> remainder
        }
    }

    private fun encompassResult(
            thisOne: Pattern,
            otherOne: Pattern,
            thisResolver: Resolver,
            otherResolver: Resolver,
            typeStack: TypeStack
    ): Result {
        return try {
            val otherOneAdjustedForExactValue = when {
                otherOne is ExactValuePattern && otherOne.pattern is StringValue -> ExactValuePattern(
                        thisOne.parse(
                                otherOne.pattern.string,
                                thisResolver
                        )
                )
                else -> otherOne
            }

            thisOne.encompasses(otherOneAdjustedForExactValue, thisResolver, otherResolver, typeStack)
        } catch (e: Throwable) {
            Failure(e.message ?: e.javaClass.name)
        }

    }

    private fun getNodeOccurrence(): NodeOccurrence {
        return pattern.getNodeOccurrence()
    }

    private fun thisOccurrenceEncompassesTheOther(thisOne: XMLPattern, otherOne: XMLPattern): Result {
        val thisTypeOccurrence = thisOne.pattern.getAttributeValue(OCCURS_ATTRIBUTE_NAME)
        val otherTypeOccurrence = otherOne.pattern.getAttributeValue(OCCURS_ATTRIBUTE_NAME)

        return when (thisTypeOccurrence) {
            otherTypeOccurrence -> Success()
            else -> thisOne.getNodeOccurrence().encompasses(otherOne.getNodeOccurrence())
        }
    }

    private fun nodeNamesShouldBeEqual(otherResolvedPattern: XMLPattern) = when {
        pattern.name != otherResolvedPattern.pattern.name ->
            Failure("Expected a node named ${pattern.name}, but got ${otherResolvedPattern.pattern.name} instead.")
        else -> Success()
    }

    private fun adapt(types: List<Pattern>, resolver: Resolver): List<Pattern> {
        if (types.isEmpty())
            return listOf(EmptyStringPattern)

        return adaptFromList(types, resolver)
    }

    private fun adaptFromList(types: List<Pattern>, resolver: Resolver): List<Pattern> {
        val first = types.firstOrNull()

        if (first is ListPattern) {
            val typeName = first.pattern.pattern.toString().removeSurrounding("(", ")")
            val type = resolver.getPattern("($typeName)")
            if (type !is XMLPattern)
                throw ContractException("A list specification inside an XML definition must refer to an XML type. But $typeName is not an XML type.")

            val multipleOccursAttribute =
                    mapOf(OCCURS_ATTRIBUTE_NAME to ExactValuePattern(StringValue(MULTIPLE_ATTRIBUTE_VALUE)))

            return listOf(
                    type.copy(
                            pattern = type.pattern.copy(
                                    attributes = type.pattern.attributes.plus(
                                            multipleOccursAttribute
                                    )
                            )
                    )
            )
        }

        return types
    }

    val referredType: String?
        get() {
            return pattern.attributes[TYPE_ATTRIBUTE_NAME]?.let {
                it as ExactValuePattern
                it.pattern.toStringLiteral()
            }
        }

    override val memberList: MemberList
        get() = MemberList(pattern.nodes, null)

    override val typeName: String = "xml"

    // TODO not sure if this is still needed
    fun toGherkinString(additionalIndent: String = "", indent: String = ""): String {
        return pattern.toGherkinString(additionalIndent, indent)
    }

    fun toGherkinXMLNode(): XMLNode {
        return pattern.toGherkinishNode()
    }

    fun toGherkinStatement(qontractTypeName: String): String {
        val typeString = this.toGherkinXMLNode().toPrettyStringValue().trim()
        return "And type $qontractTypeName\n\"\"\"\n$typeString\n\"\"\""
    }
}

private fun <T> PLACEHOLDER_USE_GIT_BLAME_TO_FIND_RELEVANT_COMMIT (value: T, s: String): T {
    return value
}
