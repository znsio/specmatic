package io.specmatic.core.examples.module

import io.specmatic.conversions.ExampleFromFile
import io.specmatic.core.EXAMPLES_DIR_SUFFIX
import io.specmatic.core.Feature
import io.specmatic.core.METHOD_BREAD_CRUMB
import io.specmatic.core.PATH_BREAD_CRUMB
import io.specmatic.core.Result
import io.specmatic.core.Scenario
import io.specmatic.core.examples.server.InteractiveExamplesMismatchMessages
import io.specmatic.core.examples.server.SchemaExample
import io.specmatic.core.log.consoleDebug
import io.specmatic.core.utilities.exceptionCauseMessage
import io.specmatic.core.value.NullValue
import java.io.File

class ExampleModule {

    fun getExistingExampleFiles(feature: Feature, scenario: Scenario, examples: List<ExampleFromFile>): List<Pair<ExampleFromFile, Result>> {
        return examples.mapNotNull { example ->
            when (val matchResult = scenario.matches(example.request, example.response, InteractiveExamplesMismatchMessages, feature.flagsBased)) {
                is Result.Success -> example to matchResult
                is Result.Failure -> {
                    val isFailureRelatedToScenario = matchResult.getFailureBreadCrumbs("").none { breadCrumb ->
                        breadCrumb.contains(PATH_BREAD_CRUMB)
                                || breadCrumb.contains(METHOD_BREAD_CRUMB)
                                || breadCrumb.contains("REQUEST.HEADERS.Content-Type")
                                || breadCrumb.contains("STATUS")
                    }
                    if (isFailureRelatedToScenario) { example to matchResult } else null
                }
            }
        }
    }

    fun getExamplesDirPath(contractFile: File): File {
        return contractFile.canonicalFile
            .parentFile
            .resolve("""${contractFile.nameWithoutExtension}$EXAMPLES_DIR_SUFFIX""")
    }

    fun getExamplesFromDir(dir: File): List<ExampleFromFile> {
        return dir.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull {
            ExampleFromFile.fromFile(it).realise(
                hasValue = { example, _ -> example },
                orException = { err -> consoleDebug(exceptionCauseMessage(err.t)); null },
                orFailure = { null }
            )
        }
    }

    fun getSchemaExamplesWithValidation(
        feature: Feature,
        examplesDir: File
    ): List<Pair<SchemaExample, Result?>> {
        return getSchemaExamples(examplesDir).map {
            it to if(it.value !is NullValue) {
                feature.matchResultSchemaFlagBased(
                    discriminatorPatternName = it.discriminatorBasedOn,
                    patternName = it.schemaBasedOn,
                    value = it.value,
                    mismatchMessages = InteractiveExamplesMismatchMessages,
                    breadCrumbIfDiscriminatorMismatch = it.file.name
                )
            } else null
        }
    }

    private fun getSchemaExamples(dir: File): List<SchemaExample> {
        return dir.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull {
            SchemaExample.fromFile(it).realise(
                hasValue = { example, _ -> example },
                orException = { err -> consoleDebug(exceptionCauseMessage(err.t)); null },
                orFailure = { null }
            )
        }
    }

}
