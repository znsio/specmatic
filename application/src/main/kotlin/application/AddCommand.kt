package application

import picocli.CommandLine
import run.qontract.core.Feature
import run.qontract.core.testBackwardCompatibility2
import run.qontract.core.utilities.exceptionCauseMessage
import run.qontract.core.utilities.exitWithMessage
import java.io.File
import java.util.concurrent.Callable
import kotlin.system.exitProcess

private fun feature(file: File) = Feature(file.readText())

@CommandLine.Command(name = "add", description = ["Check the new contract for backward compatibility with the specified version, then overwrite the old one with it."], mixinStandardHelpOptions = true)
class AddCommand: Callable<Unit> {
    @CommandLine.Parameters(index = "0", descriptionKey = "newerContract")
    lateinit var newerContractFile: File

    @CommandLine.Parameters(index = "1", descriptionKey = "olderContract")
    lateinit var olderContractFile: File

    override fun call() {
        if(olderContractFile.exists()) {
            val newerContractFeature = feature(newerContractFile)
            val olderFeature = feature(olderContractFile)

            val results = testBackwardCompatibility2(olderFeature, newerContractFeature)

            if (!results.success()) {
                println(results.report())
                println()
                exitWithMessage("The new contract is not backward compatible with the older one.")
            }
        }

        newerContractFile.copyTo(olderContractFile, overwrite = olderContractFile.exists())

        val git = GitWrapper(olderContractFile.parent)

        try {
            git.add()

            val pushRequired = try {
                git.commit()
                true
            } catch(e: UpdateError) {
                if(!exceptionMessageContains(e, listOf("nothing to commit")))
                    throw e

                exceptionMessageContains(e, listOf("branch is ahead of"))
            }

            when {
                pushRequired -> git.push()
                else -> println("Nothing to commit, old and new are identical, no push required.")
            }
        } catch(e: UpdateError) {
            git.resetHard().pull()

            println("Couldn't pull the latest. Got error: ${exceptionCauseMessage(e)}")
            exitProcess(1)
        }
    }
}

class UpdateError(error: String) : Throwable(error)
