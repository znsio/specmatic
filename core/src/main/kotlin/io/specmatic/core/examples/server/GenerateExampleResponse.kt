package io.specmatic.core.examples.server

data class GenerateExampleResponse (
    val examples: List<GenerateExample>
) {
    companion object {
        fun from(infos: List<ExamplesInteractiveServer.Companion.ExamplePathInfo>): GenerateExampleResponse {
            return GenerateExampleResponse(infos.map { GenerateExample(it.path, it.created) })
        }
    }
}
