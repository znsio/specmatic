package io.specmatic.core.filters

import java.util.regex.Pattern

data class ScenarioMetadataFilter(
    // Groups are created for || based condition, as they have lower precedence than AND.
    val filterGroups: List<FilterGroup> = emptyList()
) {
    fun isSatisfiedBy(metadata: ScenarioMetadata): Boolean {
        if (filterGroups.isEmpty()) return true
        return filterGroups.any { it.isSatisfiedBy(metadata) }
    }

    companion object {
        fun from(filter: String): ScenarioMetadataFilter {
            if (filter.isEmpty()) return ScenarioMetadataFilter()
            val parsedFilters = FilterParser.parse(filter)
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

// FilterGroup: Represents a group of filters combined || i.e. OR.
data class FilterGroup(val filters: List<FilterExpression>, val isAndOperation: Boolean = true) {
    fun isSatisfiedBy(metadata: ScenarioMetadata): Boolean {
        val matches = filters.map { it.matches(metadata) }
        return if (isAndOperation) matches.all { it } else matches.any { it }
    }
}




