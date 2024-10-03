package application.exampleGeneration

class ScenarioFilter(filterName: String, filterNotName: String, val extensive: Boolean) {
    val filterNameTokens = filterToTokens(filterName)
    val filterNotNameTokens = filterToTokens(filterNotName)

    private fun filterToTokens(filterValue: String): Set<String> {
        if (filterValue.isBlank()) return emptySet()
        return filterValue.split(",").map { it.trim() }.toSet()
    }
}