package io.specmatic.core.examples.server

import io.specmatic.core.examples.module.ExamplePathInfo

data class GenerateExampleResponse (
    val examples: List<GenerateExample>
) {
    companion object {
        fun from(infos: List<ExamplePathInfo>): GenerateExampleResponse {
            return GenerateExampleResponse(infos.map { GenerateExample(it.path, it.created) })
        }
    }
}
