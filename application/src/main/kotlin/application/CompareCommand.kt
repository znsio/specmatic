package application

import `in`.specmatic.core.Feature
import `in`.specmatic.core.parseGherkinStringToFeature
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.testBackwardCompatibility
import `in`.specmatic.core.utilities.readFile
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.util.concurrent.Callable

@Command(name = "compare",
        mixinStandardHelpOptions = true,
        description = ["Checks if two contracts are equivalent"])
class CompareCommand : Callable<Unit> {
    @Parameters(index = "0", description = ["Older contract file path"])
    lateinit var path1: String

    @Parameters(index = "1", description = ["Newer contract file path"])
    lateinit var path2: String

    override fun call() {
        val report = backwardCompatibilityOfContractPaths(path1, path2)
        println(report.message())
    }
}

fun backwardCompatibilityOfContractPaths(path1: String, path2: String): CompatibilityReport {
    val behaviour1 = parseGherkinStringToFeature(readFile(path1))
    val behaviour2 = parseGherkinStringToFeature(readFile(path2))

    return backwardCompatible(behaviour1, behaviour2)
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

