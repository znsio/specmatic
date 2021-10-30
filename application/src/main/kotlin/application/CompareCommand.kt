package application

import `in`.specmatic.core.*
import `in`.specmatic.core.details
import `in`.specmatic.core.logException
import `in`.specmatic.core.pattern.ContractException
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.util.concurrent.Callable

@Command(name = "compare",
        mixinStandardHelpOptions = true,
        description = ["Checks if two contracts are equivalent"])
class CompareCommand : Callable<Int> {
    @Parameters(index = "0", description = ["Older contract file path"])
    lateinit var olderContractFilePath: String

    @Parameters(index = "1", description = ["Newer contract file path"])
    lateinit var newerContractFilePath: String

    override fun call(): Int {
        if(!olderContractFilePath.isContractFile()) {
            details.forTheUser(invalidContractExtensionMessage(olderContractFilePath))
            return 1
        }

        if(!newerContractFilePath.isContractFile()) {
            details.forTheUser(invalidContractExtensionMessage(newerContractFilePath))
            return 1
        }

        return logException {
            val olderContract = olderContractFilePath.loadContract()
            val newerContract = newerContractFilePath.loadContract()

            val report = backwardCompatible(olderContract, newerContract)
            println(report.message())
            report.exitCode
        }
    }
}

fun backwardCompatible(olderContract: Feature, newerContract: Feature): CompatibilityReport =
        try {
            testBackwardCompatibility(olderContract, newerContract).let { results ->
                when {
                    results.failureCount > 0 -> {
                        IncompatibleReport(results)
                    }
                    else -> CompatibleReport
                }
            }
        } catch(e: ContractException) {
            ContractExceptionReport(e)
        } catch(e: Throwable) {
            ExceptionReport(e)
        }

