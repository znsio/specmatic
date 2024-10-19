package io.specmatic.core.filters

import io.specmatic.core.HEADERS_BREADCRUMB
import io.specmatic.core.METHOD_BREAD_CRUMB
import io.specmatic.core.PATH_BREAD_CRUMB
import io.specmatic.core.QUERY_PARAMS_BREADCRUMB

enum class ScenarioFilterTags(val key: String) {
    METHOD(METHOD_BREAD_CRUMB),
    PATH(PATH_BREAD_CRUMB),
    STATUS_CODE("STATUS-CODE"),
    HEADER(HEADERS_BREADCRUMB),
    QUERY(QUERY_PARAMS_BREADCRUMB),
    EXAMPLE_NAME("EXAMPLE-NAME")
}