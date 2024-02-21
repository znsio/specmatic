package application

import `in`.specmatic.core.*
import `in`.specmatic.core.log.logger
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(name = "test-count",
        hidden = true,
        description = ["Estimate the count of tests that a specification will generate when without examples"])
class TestCountCommand : Callable<Unit> {
    @Parameters(index = "0", description = ["Specification file path"])
    lateinit var specification: String

    override fun call() {
        if(!specification.isContractFile()) {
            logger.log(invalidContractExtensionMessage(specification))
            exitProcess(1)
        }

        val feature = parseContractFileToFeature(specification)

        println(feature.testCounts())
    }
}
