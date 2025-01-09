package io.specmatic.core.config.v1

import com.fasterxml.jackson.annotation.JsonAlias
import io.specmatic.core.ATTRIBUTE_SELECTION_DEFAULT_FIELDS
import io.specmatic.core.ATTRIBUTE_SELECTION_QUERY_PARAM_KEY
import io.specmatic.core.utilities.readEnvVarOrProperty

data class AttributeSelectionPattern(
    @field:JsonAlias("default_fields")
    val defaultFields: List<String> = readEnvVarOrProperty(
        ATTRIBUTE_SELECTION_DEFAULT_FIELDS,
        ATTRIBUTE_SELECTION_DEFAULT_FIELDS
    ).orEmpty().split(",").filter { it.isNotBlank() },
    @field:JsonAlias("query_param_key")
    val queryParamKey: String = readEnvVarOrProperty(
        ATTRIBUTE_SELECTION_QUERY_PARAM_KEY,
        ATTRIBUTE_SELECTION_QUERY_PARAM_KEY
    ).orEmpty()
)