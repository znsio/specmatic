package application

import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import run.qontract.core.Feature
import run.qontract.core.testBackwardCompatibility2
import java.io.File
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(name = "backwardCompatible",
        mixinStandardHelpOptions = true,
        description = ["Checks if the newer contract is backward compatible with the older one"])
class BackwardCompatibleCommand : Callable<Unit> {
    @Parameters(index = "0", description = ["Older contract path"])
    lateinit var olderContractPath: String

    @Parameters(index = "1", description = ["Newer contract path"])
    lateinit var newerContractPath: String

    override fun call() {
        val olderFeature = Feature(File(olderContractPath).readText())
        val newerFeature = Feature(File(newerContractPath).readText())

        val results = testBackwardCompatibility2(olderFeature, newerFeature)

        val (resultExitCode, resultMessage) = when {
            results.success() -> Pair(0, "The newer is backward compatible with the older.")
            else -> Pair(1, "The newer is NOT backward compatible with the older.")
        }

        val countsMessage = "Tests run: ${results.successCount + results.failureCount}, Passed: ${results.successCount}, Failed: ${results.failureCount}\n\n"
        val resultReport = results.report().trim().let {
            when {
                it.isNotEmpty() -> "$it\n\n"
                else -> it
            }
        }

        println("$countsMessage$resultReport$resultMessage".trim())

        exitProcess(resultExitCode)
    }
}
