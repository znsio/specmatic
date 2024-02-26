package `in`.specmatic.core.pattern

import kotlin.math.min

/**
 * Provides utility access to all combinations of multiple sets of candidate values. Represented as the cartesian
 * product of all sets where each combination is represented as a numerical index from 0 to (MAX_COMBOS - 1).
 *
 * Supports sequential iteration over all combinations, which is used to test as many combinations as possible.
 *
 * Supports translating a combination of candidate values to an index, which is used to first test priority combinations
 * (and to remember these when later iterating remaining combinations to not double-include these priority combinations)
 *
 * See <a href="https://softwareengineering.stackexchange.com/questions/228478/testing-all-combinations"> this
 * stackoverflow article</a> accepted answer. A notable difference is that our representation is reversed such that
 * a sequential iteration produces all combinations with the first candidate value of the first set before producing
 * all combinations using the second candidate value of the first set, and so on for each subsequent value and set.
 */
class CombinationSpec<ValueType>(
    keyToCandidatesOrig: Map<String, Sequence<ValueType>>,
    private val maxCombinations: Int,
) {
    init {
        if (maxCombinations < 1) throw IllegalArgumentException("maxCombinations must be > 0 and <= ${Int.MAX_VALUE}")
    }

    // Omit entries without any candidate values
    private val keyToCandidates = keyToCandidatesOrig.mapValues { it.value.toList() }.filterValues { it.isNotEmpty() }
    private val indexToKeys = keyToCandidates.keys.toList()
    private val indexToCandidates = keyToCandidates.values
    private val maxCandidateCount = indexToCandidates.maxOfOrNull { it.size } ?: 0
    private val allCombosCount = indexToCandidates.map { it.size.toLong()}.reduceOrNull{ acc, cnt -> acc * cnt} ?: 0
    private val reversedIndexToKeys = indexToKeys.reversed()
    private val reversedIndexToCandidates = indexToCandidates.reversed()
    private val lastCombination = min(maxCombinations, min(allCombosCount, Int.MAX_VALUE.toLong()).toInt()) - 1
    private val prioritizedComboIndexes = calculatePrioritizedComboIndexes()

    val selectedCombinations: Sequence<Map<String, ValueType>> = toSelectedCombinations2(keyToCandidatesOrig, maxCombinations)

    fun <ValueType> toSelectedCombinations2(rawPatternCollection: Map<String, Sequence<ValueType>>, maxCombinations: Int): Sequence<Map<String, ValueType>> {
        val patternCollection = rawPatternCollection.filterValues { it.any() }

        if (patternCollection.isEmpty())
            return emptySequence()

        //TODO: STREAMING (we should be able to avoid maintaining cachedValues)
        val cachedValues = patternCollection.mapValues { mutableListOf<ValueType>() }
        val prioritisedGenerations = mutableSetOf<Map<String, ValueType>>()

        val ranOut = cachedValues.mapValues { false }.toMutableMap()

        val iterators = patternCollection.mapValues {
            it.value.iterator()
        }.filter {
            it.value.hasNext()
        }

        return sequence {
            //TODO: STREAMING (can we manage without this ctr?)
            var ctr = 0

            while (true) {
                val nextValue = iterators.mapValues { (key, iterator) ->
                    val nextValueFromIterator = if (iterator.hasNext()) {
                        val value = iterator.next()

                        cachedValues.getValue(key).add(value)

                        value
                    } else {
                        ranOut[key] = true

                        val cachedValuesForKey = cachedValues.getValue(key)
                        val value = cachedValuesForKey.get(ctr % cachedValuesForKey.size)

                        value
                    }

                    nextValueFromIterator
                }

                if(ranOut.all { it.value })
                    break

                ctr ++

                yield(nextValue)
                prioritisedGenerations.add(nextValue)

                if(prioritisedGenerations.size == maxCombinations)
                    break
            }

            if(prioritisedGenerations.size == maxCombinations)
                return@sequence

            val otherPatterns = patternValues2(patternCollection)

            val maxCountOfUnPrioritisedGenerations = maxCombinations - prioritisedGenerations.size

            val filtered = otherPatterns.filter { it !in prioritisedGenerations }
            val limited = filtered.take(maxCountOfUnPrioritisedGenerations)

            yieldAll(limited)
        }
    }
    fun <ValueType> patternValues2(patternCollection: Map<String, Sequence<ValueType>>): Sequence<Map<String, ValueType>> {
        if(patternCollection.isEmpty())
            return sequenceOf(emptyMap())

        val entry = patternCollection.entries.first()

        val subsequentGenerations: Sequence<Map<String, ValueType>> = patternValues2(patternCollection - entry.key)

        return entry.value.flatMap { value ->
            subsequentGenerations.map { mapOf(entry.key to value) + it }
        }
    }

    private fun calculatePrioritizedComboIndexes(): Sequence<Int> {
        // Prioritizes using each candidate value as early as possible so uses first candidate of each set,
        // then second candidate, and so on.
        val prioritizedCombos = (0 until maxCandidateCount).asSequence().map { lockStepOffset ->
            val fullComboIndex = indexToCandidates.fold(0) {acc, candidates ->
                // Lower-cardinality sets run out of candidates first so are reused round-robin until other sets finish
                val candidateOffset = lockStepOffset % candidates.size

                toComboIndex(candidates.size, candidateOffset, acc)
            }
            // val combo = toCombo(finalComboIndex)
            fullComboIndex
        }
        return prioritizedCombos
    }

    // Combo index is based on each candidate set size and offset, plus any index accumulated so far
    private fun toComboIndex(candidateSetSize: Int, candidateOffset: Int, comboIndexSoFar: Int) =
        (comboIndexSoFar * candidateSetSize) + candidateOffset

    private fun toSelectedCombinations(): Sequence<Map<String, ValueType>> {
        val prioritizedCombos = prioritizedComboIndexes.map { toCombo(it) }.toList()
        val remainingCombos = (0..lastCombination)
            .filterNot { prioritizedComboIndexes.contains(it) }
            .map { toCombo(it) }

        val combined = prioritizedCombos.plus(remainingCombos)

        return if (combined.size > maxCombinations)
            combined.subList(0, maxCombinations).asSequence()
        else combined.asSequence()
    }
    private fun toCombo(comboIndex: Int): Map<String, ValueType> {
        var subIndex = comboIndex

        return reversedIndexToCandidates.mapIndexed{ reversedIndex, candidates ->
            val candidateOffset = subIndex % candidates.size
            subIndex /= candidates.size
            reversedIndexToKeys[reversedIndex] to  candidates[candidateOffset]
        }.toMap()
    }

}
