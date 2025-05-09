package io.specmatic.core.filters

enum class ScenarioFilterTags(val key: String) {
    METHOD("METHOD"),
    PATH("PATH"),
    STATUS("STATUS"),
    HEADERS("HEADERS"),
    QUERY("QUERY"),
    EXAMPLE("EXAMPLE");

    companion object {
        fun from(key: String): ScenarioFilterTags {
            return entries.find { it.key == key } ?: throw IllegalArgumentException("Unknown filter tag: $key")
        }
    }
}