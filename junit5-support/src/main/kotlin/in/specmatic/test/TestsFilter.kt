package `in`.specmatic.test

data class TestsFilter(private val filterNames: List<String>, private val filterNotNames: List<String>) {
    constructor(filterName: String? = null, filterNotName: String? = null) :
            this(
                filterName?.split(",")?.map { it.trim() } ?: emptyList(),
                filterNotName?.split(",")?.map { it.trim() } ?: emptyList())

    fun selectTestsToRun(
        testScenarios: List<ContractTest>
    ): List<ContractTest> {
        val filteredByNames = if(filterNames.isNotEmpty()) testScenarios.filter { test ->
            filterNames.any { test.testDescription().contains(it) }
        } else testScenarios

        val filterByNotNames = if(filterNotNames.isNotEmpty()) filteredByNames.filterNot { test ->
            filterNotNames.isNotEmpty() && filterNotNames.any { test.testDescription().contains(it) }
        } else testScenarios


        return filterByNotNames
    }
}