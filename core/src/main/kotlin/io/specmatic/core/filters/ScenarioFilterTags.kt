package io.specmatic.core.filters

enum class ScenarioFilterTags(val key: String) {
    METHOD("METHOD"),
    PATH("PATH"),
    STATUS("STATUS"),
    HEADERS("HEADERS"),
    QUERY("QUERY"),
    EXAMPLE_NAME("EXAMPLE_NAME");

    companion object {
        fun from(key: String): ScenarioFilterTags {
            return entries.find { it.key == key } ?: throw IllegalArgumentException("Unknown filter tag: $key")
        }
    }
}