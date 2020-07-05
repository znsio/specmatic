package run.qontract.core.pattern

import run.qontract.core.*
import run.qontract.core.utilities.mapZip
import run.qontract.core.utilities.parseXML
import run.qontract.core.value.IXMLValue
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value
import run.qontract.core.value.XMLNode

fun toTypeData(node: XMLNode): XMLTypeData = XMLTypeData(node.name, attributeTypeMap(node), nodeTypes(node))

private fun nodeTypes(node: XMLNode): List<XMLPattern2> {
    return node.nodes.map {
        it.exactMatchElseType() as XMLPattern2
    }
}

private fun attributeTypeMap(node: XMLNode): Map<String, Pattern> {
    return node.attributes.mapValues { (key, value) ->
        when {
            value.isPatternToken() -> DeferredPattern(key, value.toStringValue())
            else -> ExactValuePattern(value)
        }
    }
}

data class XMLPattern2(override val pattern: XMLTypeData = XMLTypeData()) : Pattern, EncompassableList {
    constructor(node: XMLNode) : this(toTypeData(node))

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData !is XMLNode)
            return Result.Failure("Expected XML node, got ${sampleData?.displayableType()}")

        if(sampleData.name != pattern.name)
            return mismatchResult(pattern.name, sampleData.name)

        val missingKey = resolver.findMissingKey(pattern.attributes, sampleData.attributes, ::validateUnexpectedKeys)
        if(missingKey != null)
            return missingKeyToResult(missingKey, "attribute")

        mapZip(pattern.attributes, sampleData.attributes).forEach { (key, patternValue, sampleValue) ->
            when (val result = resolver.matchesPattern(key, patternValue, sampleValue)) {
                is Result.Failure -> return result.breadCrumb(key)
            }
        }

        for(index in pattern.nodes.indices) {
            when (val type = pattern.nodes[index]) {
                is ListPattern -> return type.matches(this.listOf(sampleData.nodes.subList(index, pattern.nodes.indices.last), resolver), resolver)
                else -> {
                    if(index >= sampleData.nodes.size)
                        return Result.Failure("The value had only ${sampleData.nodes.size} nodes but the contract expected more.")

                    val nodeValue: XMLNode = sampleData
                    val result = type.matches(nodeValue.nodes[index], resolver)
                    if(result is Result.Failure)
                        return result.breadCrumb("${nodeValue.name}@$index")
                }
            }
        }

        return Result.Success()
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return XMLNode("", emptyMap(), valueList.map { it as XMLNode })
    }

    override fun generate(resolver: Resolver): XMLNode {
        val name = pattern.name

        val newAttributes = pattern.attributes.mapKeys { entry -> withoutOptionality(entry.key) }.mapValues { (key, pattern) ->
            attempt(breadCrumb = key) { resolver.generate(key, pattern) }
        }.mapValues { StringValue(it.value.toStringValue()) }

        val nodes = pattern.nodes.map {
            it.generate(resolver)
        }.map { it as IXMLValue }

        return XMLNode(name, newAttributes, nodes)
    }

    override fun newBasedOn(row: Row, resolver: Resolver): List<XMLPattern2> {
        return keyCombinations(pattern.attributes, row) { pattern ->
            newBasedOn(pattern, row, resolver)
        }.flatMap { newAttributes ->
            listCombinations(pattern.nodes.mapIndexed { index, pattern ->
                attempt(breadCrumb = "[$index]") {
                    pattern.newBasedOn(row, resolver)
                }
            }).map { newNodes ->
                XMLPattern2(XMLTypeData(pattern.name, newAttributes, newNodes))
            }
        }
    }

    override fun parse(value: String, resolver: Resolver): Value {
        return XMLNode(parseXML(value))
    }

    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver): Result {
        when {
            otherPattern is ExactValuePattern -> return otherPattern.fitsWithin(listOf(this), otherResolver, thisResolver)
            otherPattern !is XMLPattern2 -> return Result.Failure("Expected XMLPattern")
            pattern.name != otherPattern.pattern.name -> return Result.Failure("Expected a node named ${pattern.name}, but got ${otherPattern.pattern.name} instead.")
            else -> {
                val myRequiredKeys = pattern.attributes.keys.filter { !isOptional(it) }
                val otherRequiredKeys = otherPattern.pattern.attributes.keys.filter { !isOptional(it) }

                val missingFixedKey = myRequiredKeys.find { it !in otherRequiredKeys }
                if (missingFixedKey != null)
                    return Result.Failure("Key $missingFixedKey was missing", breadCrumb = missingFixedKey)

                val result = pattern.attributes.keys.asSequence().map { key ->
                    val bigger = pattern.attributes.getValue(key)
                    val smaller = otherPattern.pattern.attributes[key] ?: otherPattern.pattern.attributes[withoutOptionality(key)]

                    val result = if (smaller != null)
                        bigger.encompasses(resolvedHop(smaller, otherResolver), thisResolver, otherResolver)
                    else Result.Success()

                    Pair(key, result)
                }.find { it.second is Result.Failure }

                if(result?.second is Result.Failure)
                    return result.second.breadCrumb(breadCrumb = result.first)

                if(otherPattern.isEndless()) Result.Failure("Finite list is not a superset of an infinite list.")

                val others = otherPattern.getEncompassables(otherResolver).map { resolvedHop(it, otherResolver) }
                if (others.size != pattern.nodes.size)
                    return Result.Failure("The lengths of the two XML types are unequal.")

                val these = getEncompassables(thisResolver).map { resolvedHop(it, thisResolver) }

                return these.zip(others).map { (thisOne, otherOne) ->
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

    override fun getEncompassableList(count: Int, resolver: Resolver): List<Pattern> = getEncompassables(resolver)

    fun getEncompassables(resolver: Resolver): List<Pattern> = pattern.nodes.map { resolvedHop(it, resolver) }

    override fun isEndless(): Boolean = false

    override val typeName: String = "xml"
}
