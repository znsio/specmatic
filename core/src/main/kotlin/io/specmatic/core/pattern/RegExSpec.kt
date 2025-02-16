package io.specmatic.core.pattern

import com.mifmif.common.regex.Generex
import dk.brics.automaton.RegExp
import dk.brics.automaton.State

class RegExSpec(val regex: String?) {

    fun generateShortestStringOrRandom(minLen: Int): String {
        if (regex == null) return randomString(minLen)
        val shortestExample = RegExp(regex).toAutomaton().getShortestExample(true)
        if (minLen <= shortestExample.length) return shortestExample
        return Generex(regex).random(minLen, minLen)
    }

    fun generateLongestStringOrRandom(maxLen: Int): String {
        if (regex == null) return randomString(maxLen)
        val generex = Generex(regex)
        if (generex.isInfinite) {
            return generex.random(maxLen, maxLen)
        }
        val automaton = RegExp(regex).toAutomaton()
        return longestFrom(automaton.initialState, maxLen, mutableMapOf())
            ?: throw IllegalStateException("No valid string found")
    }

    /**
     * Recursively computes the longest accepted string (using at most [remaining] transitions)
     * from [state]. Returns null if no accepted string can be formed within the given limit.
     *
     * The tie-breaker when strings have the same length is the lexicographical order.
     */
    private fun longestFrom(state: State, remaining: Int, memo: MutableMap<Pair<State, Int>, String?>): String? {
        val key = state to remaining
        memo[key]?.let { return it }

        var best: String? = if (state.isAccept) "" else null

        if (remaining > 0) {
            state.transitions.forEach { t ->
                val sub = longestFrom(t.dest, remaining - 1, memo)
                sub?.let {
                    val candidate = t.max.toString() + it
                    best = when {
                        best == null -> candidate
                        candidate.length > best!!.length -> candidate
                        candidate.length == best!!.length && candidate > best!! -> candidate
                        else -> best
                    }
                }
            }
        }

        memo[key] = best
        return best
    }
}
