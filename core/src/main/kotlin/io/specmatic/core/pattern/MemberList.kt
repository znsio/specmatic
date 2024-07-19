package io.specmatic.core.pattern

import io.specmatic.core.Resolver
import io.specmatic.core.utilities.withNullPattern

data class MemberList(private val finiteList: List<Pattern>, private val rest: Pattern?) {
    fun getEncompassableList(count: Int, resolver: Resolver): List<Pattern> {
        if(count > 0 && finiteList.isEmpty() && rest == null)
            throw ContractException("The lengths of the expected and actual array patterns don't match.")

        return when {
            count > finiteList.size -> {
                when(rest) {
                    null -> throw ContractException("The lengths of the expected and actual array patterns don't match.")
                    else -> {
                        val missingEntryCount = count - finiteList.size
                        val missingEntries = 0.until(missingEntryCount).map { rest }
                        val paddedPattern = finiteList.plus(missingEntries)

                        getEncompassableList(paddedPattern, resolver)
                    }
                }
            }
            else -> {
                getEncompassableList(finiteList, resolver)
            }
        }
    }

    private fun getEncompassableList(pattern: List<Pattern>, resolver: Resolver): List<Pattern> {
        val resolverWithNullType = withNullPattern(resolver)
        return pattern.map { resolvedHop(it, resolverWithNullType) }
    }

    fun getEncompassables(resolver: Resolver): List<Pattern> {
        return (rest?.let {
            finiteList.plus(it)
        } ?: finiteList).map { resolvedHop(it, resolver) }
    }

    fun isEndless(): Boolean = rest != null
}