package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.utilities.withNullPattern

data class MemberList(private val pattern: List<Pattern>, private val rest: Pattern?) {
    fun getEncompassableList(count: Int, resolver: Resolver): List<Pattern> {
        if(count > 0 && pattern.isEmpty() && rest == null)
            throw ContractException("The lengths of the expected and actual array patterns don't match.")

        if(count > pattern.size && rest == null)
            throw ContractException("The lengths of the expected and actual array patterns don't match.")

        if(count > pattern.size) {
            val missingEntryCount = count - pattern.size
            val missingEntries = 0.until(missingEntryCount).map { pattern.last() }
            val paddedPattern = pattern.plus(missingEntries)

            return getEncompassableList(paddedPattern, resolver)
        }

        return getEncompassableList(pattern, resolver)
    }

    private fun getEncompassableList(pattern: List<Pattern>, resolver: Resolver): List<Pattern> {
        val resolverWithNullType = withNullPattern(resolver)
        return pattern.map { patternEntry ->
            when (patternEntry) {
                !is RestPattern -> resolvedHop(patternEntry, resolverWithNullType)
                else -> resolvedHop(patternEntry.pattern, resolverWithNullType)
            }
        }
    }
    fun getEncompassables(): List<Pattern> {
        return rest?.let {
            pattern.plus(it)
        } ?: pattern
    }

    fun isEndless(): Boolean = rest != null
}