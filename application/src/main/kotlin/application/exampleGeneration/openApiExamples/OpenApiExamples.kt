package application.exampleGeneration.openApiExamples

import application.exampleGeneration.ExamplesBase
import io.specmatic.core.Feature
import io.specmatic.core.Scenario
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "examples",
    mixinStandardHelpOptions = true,
    description = ["Generate JSON Examples with Request and Response from an OpenApi Contract File"],
    subcommands = [OpenApiExamplesValidate::class, OpenApiExamplesInteractive::class]
)
class OpenApiExamples: ExamplesBase<Feature, Scenario>(OpenApiExamplesCommon()) {
    @Option(names = ["--extensive"], description = ["Generate all examples (by default, generates one example per 2xx API)"], defaultValue = "false")
    override var extensive: Boolean = false
}