package run.qontract.core.pattern

import run.qontract.core.*
import run.qontract.core.utilities.mapZip
import run.qontract.core.utilities.parseXML
import run.qontract.core.value.XMLValue
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value
import run.qontract.core.value.XMLNode

fun toTypeData(node: XMLNode): XMLTypeData = XMLTypeData(node.name, attributeTypeMap(node), nodeTypes(node))

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

data class XMLPattern(override val pattern: XMLTypeData = XMLTypeData()) : Pattern, EncompassableList {
    constructor(node: XMLNode): this(toTypeData(node))
    constructor(xmlString: String): this(XMLNode(parseXML(xmlString)))

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData !is XMLNode)
            return Result.Failure("Expected xml, got ${sampleData?.displayableType()}").breadCrumb(pattern.name)

        if(sampleData.name != pattern.name)
            return mismatchResult(pattern.name, sampleData.name).breadCrumb(pattern.name)

        val missingKey = resolver.findMissingKey(pattern.attributes, sampleData.attributes, ::validateUnexpectedKeys)
        if(missingKey != null)
            return missingKeyToResult(missingKey, "attribute").breadCrumb(pattern.name)

        mapZip(pattern.attributes, sampleData.attributes).forEach { (key, patternValue, sampleValue) ->
            val resolvedValue: Value = when {
                sampleValue.isPatternToken() -> sampleValue.trimmed()
                else -> try {
                    patternValue.parse(sampleValue.string, resolver)
                } catch (e: ContractException) {
                    return e.failure().breadCrumb(key).breadCrumb(pattern.name)
                }
            }
            when (val result = resolver.matchesPattern(key, patternValue, resolvedValue)) {
                is Result.Failure -> return result.breadCrumb(key).breadCrumb(pattern.name)
            }
        }

        for(index in pattern.nodes.indices) {
            when (val type = resolvedHop(pattern.nodes[index], resolver)) {
                is ListPattern -> return type.matches(this.listOf(sampleData.nodes.subList(index, pattern.nodes.indices.last), resolver), resolver).breadCrumb(this.pattern.name)
                else -> {
                    if(index >= sampleData.nodes.size) {
                        if(!expectingEmpty(sampleData, type, resolver))
                            return Result.Failure("The value had only ${sampleData.nodes.size} nodes but the contract expected more").breadCrumb(this.pattern.name)
                    }
                    else {
                        val nodeValue: XMLNode = sampleData
                        val childNode = when (val childNode = nodeValue.nodes[index]) {
                            is StringValue -> when {
                                childNode.isPatternToken() -> childNode.trimmed()
                                else -> try {
                                    type.parse(childNode.string, resolver)
                                } catch (e: ContractException) {
                                    return e.failure().breadCrumb(this.pattern.name)
                                }
                            }
                            else -> childNode
                        }

                        val factKey = if (childNode is XMLNode) childNode.name else null
                        val result = resolver.matchesPattern(factKey, type, childNode)
                        if (result is Result.Failure) return result.breadCrumb(this.pattern.name)
                    }
                }
            }
        }

        return Result.Success()
    }

    private fun expectingEmpty(sampleData: XMLNode, type: Pattern, resolver: Resolver) =
            sampleData.nodes.isEmpty() && pattern.nodes.size == 1 && (EmptyStringPattern in type.patternSet(resolver).map { resolvedHop(it, resolver) })

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return XMLNode("", emptyMap(), valueList.map { it as XMLNode })
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

        return XMLNode(name, newAttributes, nodes)
    }

    override fun newBasedOn(row: Row, resolver: Resolver): List<XMLPattern> {
        return keyCombinations(pattern.attributes, row) { pattern ->
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
                XMLPattern(XMLTypeData(pattern.name, newAttributes, newNodes))
            }
        }
    }

    override fun parse(value: String, resolver: Resolver): Value {
        return XMLNode(parseXML(value))
    }

    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver): Result {
        val otherResolvedPattern = resolvedHop(otherPattern, otherResolver)

        when {
            otherResolvedPattern is ExactValuePattern -> return otherResolvedPattern.fitsWithin(listOf(this), otherResolver, thisResolver).breadCrumb(this.pattern.name)
            otherResolvedPattern !is XMLPattern -> return mismatchResult(this, otherResolvedPattern).breadCrumb(this.pattern.name)
            pattern.name != otherResolvedPattern.pattern.name -> return Result.Failure("Expected a node named ${pattern.name}, but got ${otherResolvedPattern.pattern.name} instead.").breadCrumb(this.pattern.name)
            else -> {
                val myRequiredKeys = pattern.attributes.keys.filter { !isOptional(it) }
                val otherRequiredKeys = otherResolvedPattern.pattern.attributes.keys.filter { !isOptional(it) }

                val missingFixedKey = myRequiredKeys.find { it !in otherRequiredKeys }
                if (missingFixedKey != null)
                    return Result.Failure("Key $missingFixedKey was missing", breadCrumb = missingFixedKey).breadCrumb(this.pattern.name)

                val result = pattern.attributes.keys.asSequence().map { key ->
                    val bigger = pattern.attributes.getValue(key)
                    val smaller = otherResolvedPattern.pattern.attributes[key] ?: otherResolvedPattern.pattern.attributes[withoutOptionality(key)]

                    val result = when {
                        smaller != null -> bigger.encompasses(resolvedHop(smaller, otherResolver), thisResolver, otherResolver)
                        else -> Result.Success()
                    }

                    Pair(key, result)
                }.find { it.second is Result.Failure }

                if(result?.second is Result.Failure)
                    return result.second.breadCrumb(breadCrumb = result.first).breadCrumb(this.pattern.name)

                if(otherResolvedPattern.isEndless()) Result.Failure("Finite list is not a superset of an infinite list").breadCrumb(this.pattern.name)

                val others = otherResolvedPattern.getEncompassables(otherResolver).map { resolvedHop(it, otherResolver) }
                if (others.size != pattern.nodes.size && (others.isEmpty() && !containsList(thisResolver) && !containsEmpty(thisResolver)))
                    return Result.Failure("The lengths of the two XML types are unequal").breadCrumb(this.pattern.name)

                val these = getEncompassables(thisResolver).map { resolvedHop(it, thisResolver) }

                return when {
                    containsList(thisResolver) -> {
                        val list = pattern.nodes[0]
                        list.encompasses(otherPattern, thisResolver, otherResolver)
                    }
                    others.size != pattern.nodes.size && others.isEmpty() && containsEmpty(thisResolver) -> Result.Success()
                    else -> {
                        these.zip(others).map { (thisOne, otherOne) ->
                            when {
                                otherOne is ExactValuePattern && otherOne.pattern is StringValue -> {
                                    ExactValuePattern(thisOne.parse(otherOne.pattern.toStringValue(), thisResolver))
                                }
                                else -> otherOne
                            }.let { otherOneAdjustedForExactValue -> thisOne.encompasses(otherOneAdjustedForExactValue, thisResolver, otherResolver) }
                        }.find { it is Result.Failure } ?: Result.Success()
                    }
                }.breadCrumb(this.pattern.name)
            }
        }
    }

    private fun containsList(resolver: Resolver): Boolean {
        if(pattern.nodes.isEmpty())
            return false
        val resolvedType = resolvedHop(pattern.nodes[0], resolver)
        val patternSet = resolvedType.patternSet(resolver).map { resolvedHop(it, resolver) }
        return pattern.nodes.size == 1 && (resolvedType is ListPattern || patternSet.any { it is ListPattern })
    }

    private fun containsEmpty(resolver: Resolver): Boolean {
        val resolvedType = resolvedHop(pattern.nodes[0], resolver)
        val patternSet = resolvedType.patternSet(resolver).map { resolvedHop(it, resolver) }
        return pattern.nodes.size == 1 && patternSet.any { it is EmptyStringPattern }
    }

    override fun getEncompassableList(count: Int, resolver: Resolver): List<Pattern> = getEncompassables(resolver)

    fun getEncompassables(resolver: Resolver): List<Pattern> = pattern.nodes.map { resolvedHop(it, resolver) }

    override fun isEndless(): Boolean = false

    override val typeName: String = "xml"
}
