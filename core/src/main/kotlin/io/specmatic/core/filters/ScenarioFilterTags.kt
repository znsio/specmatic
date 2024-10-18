package io.specmatic.core.filters

enum class ScenarioFilterTags(val key: String) {
    METHOD("method"),
    PATH("path"),
    STATUS_CODE("status-code"),
    HEADER("header"),
    QUERY("query")
}