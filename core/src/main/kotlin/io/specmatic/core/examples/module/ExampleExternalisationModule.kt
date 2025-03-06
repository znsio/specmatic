package io.specmatic.core.examples.module

import io.specmatic.core.examples.server.ExampleModule
import io.specmatic.core.log.consoleLog
import io.specmatic.core.parseContractFileToFeature
import io.specmatic.core.utilities.uniqueNameForApiOperation
import io.specmatic.mock.ScenarioStub
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class ExampleExternalisationModule(
    private val exampleModule: ExampleModule
) {
    private val exampleFileNamePostFixCounter = AtomicInteger(0)

    fun resetExampleFileNameCounter() {
        exampleFileNamePostFixCounter.set(0)
    }

    fun externaliseInlineExamples(contractFile: File): File {
        val feature = parseContractFileToFeature(contractFile)
        val inlineStubs: List<ScenarioStub> = feature.stubsFromExamples.flatMap {
            it.value.map { (request, response) -> ScenarioStub(request, response) }
        }
        try {
            inlineStubs.forEach { writeToExampleFile(it, contractFile) }
        } catch(e: Exception) {
            consoleLog(e)
        }
        return exampleModule.getExamplesDirPath(contractFile)
    }

    private fun writeToExampleFile(
        scenarioStub: ScenarioStub,
        contractFile: File
    ): File {
        val examplesDir = exampleModule.getExamplesDirPath(contractFile)
        if (examplesDir.exists().not()) examplesDir.mkdirs()
        val stubJSON = scenarioStub.toJSON()
        val uniqueNameForApiOperation =
            uniqueNameForApiOperation(scenarioStub.request, "", scenarioStub.response.status)

        val file =
            examplesDir.resolve("${uniqueNameForApiOperation}_${exampleFileNamePostFixCounter.incrementAndGet()}.json")
        println("Writing to file: ${file.relativeTo(contractFile.canonicalFile.parentFile).path}")
        file.writeText(stubJSON.toStringLiteral())
        return file
    }
}