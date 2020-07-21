package application

import picocli.CommandLine
import picocli.CommandLine.Parameters
import run.qontract.core.Feature
import run.qontract.core.pattern.parsedJSONStructure
import run.qontract.core.testBackwardCompatibility2
import run.qontract.core.utilities.exceptionCauseMessage
import run.qontract.core.utilities.exitWithMessage
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.Value
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

        val git = GitWrapper(olderContractFile.parent)

        try {
            println("Updating to latest")
            git.apply {
                pull()
                checkout("master")
                merge("origin/master")
            }

            println("Adding contract")
            newerContractFile.copyTo(olderContractFile, overwrite = olderContractFile.exists())

            val manifestFile = File("./qontract.json")
            if(manifestFile.exists()) {
                println("Checking to see if manifest has CI credentials")
                val manifestData = try {
                    parsedJSONStructure(manifestFile.readText())
                } catch(e: Throwable) {
                    println("Couldn't read manifest file: ${exceptionCauseMessage(e)}")
                    return
                }

                if(manifestData !is JSONObjectValue) {
                    exitWithMessage("Manifest must contain a json object")
                }

                val azurePipelineKeyInQontractManifest = "azure-pipeline"
                when {
                    manifestData.jsonObject.containsKey(azurePipelineKeyInQontractManifest) -> {
                        println("Manifest has azure credentials, checking if they are already registered")
                        val azureInfo = manifestData.getJSONObject(azurePipelineKeyInQontractManifest)
                        if(hasAzureData(azureInfo)) {
                            val metaDataFile = File("${olderContractFile.parent}/${olderContractFile.nameWithoutExtension}.json")

                            val azurePipelinesKeyInContractMetaData = "azure-pipelines"

                            val metaData = when {
                                metaDataFile.exists() -> parsedJSONStructure(metaDataFile.readText())
                                else -> {
                                    println("Could not find metadata file")
                                    JSONObjectValue(mapOf(azurePipelinesKeyInContractMetaData to JSONArrayValue(emptyList())))
                                }
                            }

                            if (metaData !is JSONObjectValue)
                                exitWithMessage("Contract meta data must contain a json object")

                            if (!metaData.jsonObject.containsKey(azurePipelinesKeyInContractMetaData))
                                exitWithMessage("Contract meta data must contain the key \"azure-pipelines\"")

                            val pipelines = metaData.jsonObject.getValue(azurePipelinesKeyInContractMetaData)
                            if (pipelines !is JSONArrayValue)
                                exitWithMessage("\"azure-pipelines\" key must contain a list of pipelines")

                            if (pipelines.list.none {
                                        if (it !is JSONObjectValue)
                                            exitWithMessage("All values in the pipelines list must be json objects")

                                        it.jsonObject.getValue("organization") == azureInfo.getValue("organization") &&
                                                it.jsonObject.getValue("project") == azureInfo.getValue("project") &&
                                                it.jsonObject.getValue("definitionId") == azureInfo.getValue("definitionId")
                                    }) {
                                println("Updating the contract manifest to run this project's CI when ${olderContractFile.name} changes...")
                                val newPipelines = JSONArrayValue(pipelines.list.plus(JSONObjectValue(azureInfo)))
                                val newMetaData = metaData.jsonObject.plus(azurePipelinesKeyInContractMetaData to newPipelines)

                                metaDataFile.writeText(JSONObjectValue(newMetaData).toStringValue())

                                git.add()
                                git.commit()
                            }
                        }
                    }
                }
            }

            println("Adding to the repo")
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
                    println("Publishing the updates")
                    git.push()
                }
                else -> println("Nothing to publish, old and new are identical, no push required.")
            }

            println("Done")
        } catch(e: UpdateError) {
            git.resetHard().pull()

            println("Couldn't push the latest. Got error: ${exceptionCauseMessage(e)}")
            exitProcess(1)
        }
    }

    private fun hasAzureData(azureInfo: Map<String, Value>): Boolean {
        return when {
            !azureInfo.containsKey("organization") -> {
                println("Azure info must contain the Azure organisation name under the \"organisation\" key")
                false
            }
            !azureInfo.containsKey("project") -> {
                println("Azure info must contain the Azure project name under the \"project\" key")
                false
            }
            !azureInfo.containsKey("definitionId") -> {
                println("Azure info must contain the Azure definition id under the \"definitionId\" key")
                false
            }
            azureInfo.keys.size != 3 -> {
                println("Azure info keys must include nothing but organisation, project and definitionId")
                false
            }
            else -> true
        }
    }
}

class UpdateError(error: String) : Throwable(error)
