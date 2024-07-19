package io.specmatic.conversions

import io.specmatic.core.HttpQueryParamPattern
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.pattern.Pattern
import io.specmatic.core.pattern.QueryParameterArrayPattern
import io.specmatic.core.pattern.QueryParameterScalarPattern
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.parameters.QueryParameter

class OpenApiOperation(val operation: Operation) {
    fun validateParameters() {
        operation.parameters.orEmpty().forEach { parameter ->
            if(parameter.name == null)
                throw ContractException("A parameter does not have a name.")

            if(parameter.schema == null)
                throw ContractException("A parameter does not have a schema.")

            if(parameter.schema.type == "array" && parameter.schema.items == null)
                throw ContractException("A parameter of type \"array\" has not defined \"items\".")

        }
    }
}
