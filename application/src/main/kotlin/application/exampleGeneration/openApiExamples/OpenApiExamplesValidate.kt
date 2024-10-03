package application.exampleGeneration.openApiExamples

import application.exampleGeneration.ExamplesValidationBase
import io.specmatic.core.Feature
import io.specmatic.core.Scenario
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(name = "validate", description = ["Validate OpenAPI inline and external examples"])
class OpenApiExamplesValidate: ExamplesValidationBase<Feature, Scenario>(OpenApiExamplesCommon()) {
    @Option(names = ["--validate-external"], description = ["Validate external examples, defaults to true"])
    override var validateExternal: Boolean = true

    @Option(names = ["--validate-inline"], description = ["Validate inline examples, defaults to false"])
    override var validateInline: Boolean = false

    override var extensive: Boolean = true
}