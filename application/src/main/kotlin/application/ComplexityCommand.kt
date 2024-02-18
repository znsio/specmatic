package application

import `in`.specmatic.core.*
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.log.logException
import `in`.specmatic.core.pattern.ContractException
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(name = "complexity",
        hidden = true,
        description = ["Show the complexity of a specification"])
class ComplexityCommand : Callable<Unit> {
    @Parameters(index = "0", description = ["Specification file path"])
    lateinit var specification: String

    override fun call() {
        if(!specification.isContractFile()) {
            logger.log(invalidContractExtensionMessage(specification))
            exitProcess(1)
        }

        val feature = parseContractFileToFeature(specification)

        println(feature.complexity())
    }
}
