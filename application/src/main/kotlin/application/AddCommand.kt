package application

import picocli.CommandLine
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import run.qontract.core.Feature
import run.qontract.core.pattern.JSONObjectPattern
import run.qontract.core.pattern.parsedJSONStructure
import run.qontract.core.testBackwardCompatibility2
import run.qontract.core.utilities.exceptionCauseMessage
import run.qontract.core.utilities.exitWithMessage
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.Value
import run.qontract.mock.getJSONObjectValue
import java.io.File
import java.util.concurrent.Callable
import kotlin.system.exitProcess

private fun feature(file: File) = Feature(file.readText())

@CommandLine.Command(name = "add", description = ["Check the new contract for backward compatibility with the specified version, then overwrite the old one with it."], mixinStandardHelpOptions = true)
class AddCommand: Callable<Unit> {
    @Parameters(index = "0", descriptionKey = "newerContract")
    lateinit var newerContractFile: File

    @Parameters(index = "1", descriptionKey = "olderContract")
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
            git.checkout("master")
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
                pushRequired -> {
                    println("Pushing the updated contract")
                    git.push()
                }
                else -> println("Nothing to commit, old and new are identical, no push required.")
            }

            val manifestFile = File("./qontract.json")
            if(manifestFile.exists()) {
                val manifestData = try {
                    parsedJSONStructure(manifestFile.readText())
                } catch(e: Throwable) {
                    println("Couldn't read manifest file: ${exceptionCauseMessage(e)}")
                    return
                }

                if(manifestData !is JSONObjectValue) {
                    exitWithMessage("Manifest must contain a json object")
                }

                when {
                    manifestData.jsonObject.containsKey("azure") -> {
                        git.pull()
                        val azureInfo = manifestData.getJSONObject("azure")
                        exitIfNoAzureData(azureInfo)

                        val metaDataFile = File("${olderContractFile.nameWithoutExtension}.meta")

                        val metaData = when {
                            metaDataFile.exists() -> parsedJSONStructure(metaDataFile.readText())
                            else -> {
                                JSONObjectValue(mapOf("pipelines" to JSONArrayValue(emptyList())))
                            }
                        }

                        if(metaData !is JSONObjectValue)
                            exitWithMessage("Contract meta data must contain a json object")

                        if(!metaData.jsonObject.containsKey("pipelines"))
                            exitWithMessage("Contract meta data must contain the key \"pipelines\"")

                        val pipelines = metaData.jsonObject.getValue("pipelines")
                        if(pipelines !is JSONArrayValue)
                            exitWithMessage("\"pipelines\" key must contain a list of pipelines")

                        if(pipelines.list.none {
                            if(it !is JSONObjectValue)
                                exitWithMessage("All values in the pipelines list must be json objects")

                            it.jsonObject.getValue("organisation") == azureInfo.getValue("organisation") &&
                            it.jsonObject.getValue("organisation") == azureInfo.getValue("project") &&
                            it.jsonObject.getValue("organisation") == azureInfo.getValue("definitionId")
                        }) {
                            println("Updating the contract manifest to run this project's CI when ${olderContractFile.name} changes...")
                            val newMetaData = metaData.jsonObject.plus(azureInfo)

                            metaDataFile.writeText(JSONObjectValue(newMetaData).toStringValue())

                            git.add()
                            git.commit()

                            git.push()
                        }
                    }
                }
            }
        } catch(e: UpdateError) {
            git.resetHard().pull()

            println("Couldn't push the latest. Got error: ${exceptionCauseMessage(e)}")
            exitProcess(1)
        }
    }

    private fun exitIfNoAzureData(azureInfo: Map<String, Value>) {
        if (!azureInfo.containsKey("organization"))
            exitWithMessage("Azure info must contain the Azure organisation name under the \"organisation\" key")
        if (!azureInfo.containsKey("project"))
            exitWithMessage("Azure info must contain the Azure project name under the \"project\" key")
        if (!azureInfo.containsKey("definitionId"))
            exitWithMessage("Azure info must contain the Azure definition id under the \"definitionId\" key")

        if (azureInfo.keys.size != 3)
            exitWithMessage("Azure info must contain nothing but organisation, prjoect and definitionId")
    }
}

class UpdateError(error: String) : Throwable(error)
