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
    keyToCandidatesOrig: Map<String, List<ValueType>>,
    private val maxCombinations: Int,
) {
    init {
        if (maxCombinations < 1) throw IllegalArgumentException("maxCombinations must be > 0 and <= ${Int.MAX_VALUE}")
    }
    // Omit entries without any candidate values
    private val keyToCandidates = keyToCandidatesOrig.filterValues { it.isNotEmpty() }
    private val indexToKeys = keyToCandidates.keys.toList()
    private val indexToCandidates = keyToCandidates.values
    private val maxCandidateCount = indexToCandidates.maxOfOrNull { it.size } ?: 0
    private val allCombosCount = indexToCandidates.map { it.size.toLong()}.reduceOrNull{ acc, cnt -> acc * cnt} ?: 0
    private val reversedIndexToKeys = indexToKeys.reversed()
    private val reversedIndexToCandidates = indexToCandidates.reversed()
    private val lastCombination = min(maxCombinations, min(allCombosCount, Int.MAX_VALUE.toLong()).toInt()) - 1
    private val prioritizedComboIndexes = calculatePrioritizedComboIndexes()

    val selectedCombinations = toSelectedCombinations()

    private fun calculatePrioritizedComboIndexes(): List<Int> {
        // Prioritizes using each candidate value as early as possible so uses first candidate of each set,
        // then second candidate, and so on.
        val prioritizedCombos = (0 until maxCandidateCount).map { lockStepOffset ->
            val fullComboIndex = indexToCandidates.foldIndexed(0) {index, acc, candidates ->
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

    private fun toSelectedCombinations(): List<Map<String, ValueType>> {
        val prioritizedCombos = prioritizedComboIndexes.map { toCombo(it) }
        val remainingCombos = (0 .. lastCombination)
            .filterNot { prioritizedComboIndexes.contains(it) }
            .map { toCombo(it) }

        val combined = prioritizedCombos.plus(remainingCombos)

        val combinedAndTrimmed = if (combined.size > maxCombinations)
            combined.subList(0, maxCombinations)
        else combined

        return combinedAndTrimmed
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
