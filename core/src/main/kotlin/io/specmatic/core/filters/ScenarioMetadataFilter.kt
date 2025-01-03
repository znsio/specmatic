package io.specmatic.core.filters

data class ScenarioMetadataFilter(
    // Groups are created for || based condition, as they have lower precedence than AND.
    val filterGroups: List<FilterGroup> = emptyList()
) {
    fun isSatisfiedBy(metadata: ScenarioMetadata): Boolean {
        if (filterGroups.isEmpty()) return true

        val groupResults = mutableListOf<Boolean>() // Collect results of OR-separated groups
        var tempAndResult: Boolean? = null // Accumulate AND results

        for ((index, group) in filterGroups.withIndex()) {
            val currentResult = group.isSatisfiedBy(metadata)

            if (index == 0) {
                // First group initializes the evaluation
                tempAndResult = currentResult
            } else if (group.isAndOperation) {
                // Combine with the current AND result
                tempAndResult = tempAndResult?.and(currentResult) ?: currentResult
            } else {
                // On encountering OR, finalize the current AND result
                if (tempAndResult != null) {
                    groupResults.add(tempAndResult)
                    tempAndResult = null
                }

                // Start a new OR group with the current result
                groupResults.add(currentResult)
            }
        }

        // Add any remaining AND result to groupResults
        if (tempAndResult != null) {
            groupResults.add(tempAndResult)
        }

        // Combine all OR results
        return groupResults.any { it }
    }


    companion object {
        fun from(filterExpression: String): ScenarioMetadataFilter {
            if (filterExpression.isEmpty()) return ScenarioMetadataFilter()
            val parsedFilters = FilterSyntax(filterExpression).parse()
            return ScenarioMetadataFilter(filterGroups = parsedFilters)
        }

        fun <T> filterUsing(
            items: Sequence<T>,
            scenarioMetadataFilter: ScenarioMetadataFilter,
            toScenarioMetadata: (T) -> ScenarioMetadata
        ): Sequence<T> {
            val returnItems = items.filter {
                scenarioMetadataFilter.isSatisfiedBy(toScenarioMetadata(it))
            }
            return returnItems
        }
    }
}

// FilterGroup: Represents a group of filters combined either AND (&&) or OR (||), and supports negation.
data class FilterGroup(
    val filters: List<FilterExpression> = emptyList(),
    val subGroups: List<FilterGroup> = emptyList(),
    val isAndOperation: Boolean = false,
    var isNegated: Boolean = false
) {
    fun isSatisfiedBy(metadata: ScenarioMetadata): Boolean {
        // Evaluate the filters in the group
        val filterMatches = filters.map { it.matches(metadata) }

        // Evaluate the subgroups recursively
        val subGroupMatches = subGroups.map { it.isSatisfiedBy(metadata) }

        // Combine filters and subgroups based on the logical operation
        val allMatches = filterMatches + subGroupMatches
        val groupResult = allMatches.all { it }

        // Apply negation if the group is negated
        return if (isNegated) !groupResult else groupResult
    }
}





