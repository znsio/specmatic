package io.specmatic.core.filters

interface FilterContext {
    fun includes(key: String, values: List<String>): Boolean
    fun compare(filterKey: String, operator: String, filterValue: String): Boolean
}
