package io.specmatic.core.filters

enum class FilterKeys(val key: String, val isPrefix: Boolean = false) {
    PATH("PATH"),
    METHOD("METHOD"),
    STATUS("STATUS"),
    PARAMETERS_HEADER("PARAMETERS.HEADER"),
    PARAMETERS_HEADER_KEY("PARAMETERS.HEADER.", true),
    PARAMETERS_QUERY("PARAMETERS.QUERY"),
    PARAMETERS_QUERY_KEY("PARAMETERS.QUERY.", true),
    PARAMETERS_PATH("PARAMETERS.PATH"),
    PARAMETERS_PATH_KEY("PARAMETERS.PATH.", true),
    REQUEST_BODY_CONTENT_TYPE("REQUEST-BODY.CONTENT-TYPE"),
    RESPONSE_CONTENT_TYPE("RESPONSE.CONTENT-TYPE"),
    EXAMPLE_NAME("EXAMPLE-NAME"),
    TAGS("TAGS"),
    SUMMARY("SUMMARY"),
    OPERATION_ID("OPERATION-ID"),
    DESCRIPTION("DESCRIPTION");

    companion object {
        fun fromKey(key: String): FilterKeys {
            entries.firstOrNull { it.key == key }?.let { return it }

            return entries.firstOrNull { it.isPrefix && key.startsWith(it.key) }
                ?: throw IllegalArgumentException("Invalid filter key: $key")
        }
    }
}