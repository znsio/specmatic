package run.qontract.core.pattern

import run.qontract.core.*
import run.qontract.core.utilities.mapZip
import run.qontract.core.utilities.parseXML
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value
import run.qontract.core.value.XMLNode
import run.qontract.core.value.XMLValue

fun toTypeData(node: XMLNode): XMLTypeData = XMLTypeData(node.name, node.realName, attributeTypeMap(node), nodeTypes(node))

private fun nodeTypes(node: XMLNode): List<Pattern> {
    return node.nodes.map {
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
    constructor(xmlString: String, typeAlias: String? = null): this(XMLNode(parseXML(xmlString)), typeAlias)

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData !is XMLNode)
            return Result.Failure("Expected xml, got ${sampleData?.displayableType()}").breadCrumb(pattern.name)

        if(sampleData.name != pattern.name)
            return mismatchResult(pattern.name, sampleData.name).breadCrumb(pattern.name)

        val missingKey = resolver.findMissingKey(ignoreXMLNamespaces(pattern.attributes), ignoreXMLNamespaces(sampleData.attributes), ::validateUnexpectedKeys)
        if(missingKey != null)
            return missingKeyToResult(missingKey, "attribute").breadCrumb(pattern.name)

        return matchAttributes(pattern, sampleData, resolver).ifSuccess {
            pattern.nodes.asSequence().mapIndexed { index, patternItem ->
                val type = resolvedHop(patternItem, resolver)
                when {
                    type is ListPattern -> type.matches(this.listOf(sampleData.nodes.subList(index, pattern.nodes.indices.last), resolver), resolver)
                    index >= sampleData.nodes.size -> errorUnlessExpectingEmpty(sampleData, type, resolver)
                    else -> matchNodeContent(sampleData, index, type, resolver)
                }
            }.find { it is Result.Failure }?.breadCrumb(this.pattern.name) ?: Result.Success()
        }
    }

    private fun matchNodeContent(sampleData: XMLNode, index: Int, type: Pattern, resolver: Resolver): Result {
        return try {
            val childNode = when (val childNode = sampleData.nodes[index]) {
                is StringValue -> when {
                    childNode.isPatternToken() -> childNode.trimmed()
                    else -> type.parse(childNode.string, resolver)
                }
                else -> childNode
            }

            val factKey = if (childNode is XMLNode) childNode.name else null
            resolver.matchesPattern(factKey, type, childNode)
        } catch (e: ContractException) {
            e.failure()
        }
    }

    private fun errorUnlessExpectingEmpty(sampleData: XMLNode, type: Pattern, resolver: Resolver): Result {
        return when {
            !expectingEmpty(sampleData, type, resolver) -> Result.Failure("The value had only ${sampleData.nodes.size} nodes but the contract expected more")
            else -> Result.Success()
        }
    }

    private fun matchAttributes(pattern: XMLTypeData, sampleData: XMLNode, resolver: Resolver): Result =
            mapZip(ignoreXMLNamespaces(pattern.attributes), ignoreXMLNamespaces(sampleData.attributes)).asSequence().map { (key, patternValue, sampleValue) ->
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
        return sampleData.nodes.isEmpty() && pattern.nodes.size == 1 && (EmptyStringPattern in resolvedPatternSet || StringPattern in resolvedPatternSet)
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return XMLNode("", "", emptyMap(), valueList.map { it as XMLNode })
    }

    override fun generate(resolver: Resolver): XMLNode {
        val name = pattern.name

        val newAttributes = pattern.attributes.mapKeys { entry ->
            withoutOptionality(entry.key)
        }.mapValues { (key, pattern) ->
            attempt(breadCrumb = "$name.$key") { resolver.generate(key, pattern) }
        }.mapValues {
            StringValue(it.value.toStringValue())
        }

        val nodes = pattern.nodes.map { resolvedHop(it, resolver) }.map {
            attempt(breadCrumb = name) {
                when (it) {
                    is ListPattern -> (it.generate(resolver) as XMLNode).nodes
                    else -> listOf(it.generate(resolver))
                }
            }
        }.flatten().map {
            when(it) {
                is XMLValue -> it
                else -> StringValue(it.toStringValue())
            }
        }

        return XMLNode(name, pattern.realName, newAttributes, nodes)
    }

    override fun newBasedOn(row: Row, resolver: Resolver): List<XMLPattern> {
        return forEachKeyCombinationIn(pattern.attributes, row) { pattern ->
            attempt(breadCrumb = this.pattern.name) {
                newBasedOn(pattern, row, resolver).map {
                    it.mapKeys { entry -> withoutOptionality(entry.key) }
                }
            }
        }.flatMap { newAttributes ->
            val newNodesList = when {
                row.containsField(pattern.name) -> {
                    attempt(breadCrumb = pattern.name) {
                        if (pattern.nodes.isEmpty())
                            throw ContractException("Node ${pattern.name} is empty but an example with this name exists")

                        val parsedData = pattern.nodes[0].parse(row.getField(pattern.name), resolver)
                        val testResult = pattern.nodes[0].matches(parsedData, resolver)

                        if (!testResult.isTrue())
                            throw ContractException(resultReport(testResult))

                        listOf(listOf(ExactValuePattern(parsedData)))
                    }
                }
                else -> {
                    listCombinations(pattern.nodes.map { pattern ->
                        attempt(breadCrumb = this.pattern.name) {
                            pattern.newBasedOn(row, resolver)
                        }
                    })
                }
            }

            newNodesList.map { newNodes ->
                XMLPattern(XMLTypeData(pattern.name, pattern.realName, newAttributes, newNodes))
            }
        }
    }

    override fun parse(value: String, resolver: Resolver): Value {
        return XMLNode(parseXML(value))
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

                    these.asSequence().runningFold(ConsumeResult(others)) { acc, thisOne ->
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

    private fun adaptEmpty(acc: ConsumeResult) =
            acc.remainder.ifEmpty { listOf(EmptyStringPattern) }

    override val memberList: MemberList
        get() = MemberList(pattern.nodes, null)

    override val typeName: String = "xml"
}


