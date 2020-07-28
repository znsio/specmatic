package application

import picocli.CommandLine
import run.qontract.core.Feature
import run.qontract.core.git.GitCommand
import run.qontract.core.git.NonZeroExitError
import run.qontract.core.git.exitErrorMessageContains
import run.qontract.core.pattern.parsedJSONStructure
import run.qontract.core.testBackwardCompatibility2
import run.qontract.core.utilities.*
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.Value
import java.io.File
import java.util.concurrent.Callable
import kotlin.system.exitProcess

private const val azurePipelineKeyInQontractManifest = "azure-pipeline"

@CommandLine.Command(name = "push", description = ["Check the new contract for backward compatibility with the specified version, then overwrite the old one with it."], mixinStandardHelpOptions = true)
class PushCommand: Callable<Unit> {
    override fun call() {
        val workingDirectory = File(".qontract/repos")

        val sources = loadSourceDataFromManifest("./qontract.json")
        val manifestFile = File("./qontract.json")
        val manifestData = try {
            parsedJSONStructure(manifestFile.readText())
        } catch (e: Throwable) {
            println("Couldn't read manifest file: ${exceptionCauseMessage(e)}")
            return
        }

        for(source in sources) {
            val sourceDir = when(source) {
                is GitRepo -> workingDirectory.resolve(source.gitRepositoryURL.split("/").last())
                is GitMonoRepo -> File(".")
            }

            val sourceGit = GitCommand(sourceDir.path)

            try {
                if (sourceGit.isGitRepository()) {
                    sourceGit.pull()

                    for (contractPath in source.paths) {
                        testBackwardCompatibility(sourceDir, contractPath, sourceGit, source)
                        subscribeToContract(manifestData, contractPath, sourceGit)
                    }

                    for(contractPath in source.paths) {
                        sourceGit.add(contractPath)
                    }

                    commitAndPush(sourceGit)

                    println("Done")
                }
            } catch (e: NonZeroExitError) {
                println("Couldn't push the latest. Got error: ${exceptionCauseMessage(e)}")
                exitProcess(1)
            }
        }
    }

    private fun subscribeToContract(manifestData: Value, contractPath: String, sourceGit: GitCommand) {
        println("Checking to see if manifest has CI credentials")

        if (manifestData !is JSONObjectValue)
            exitWithMessage("Manifest must contain a json object")

        if (manifestData.jsonObject.containsKey(azurePipelineKeyInQontractManifest))
            registerAzureCredentials(manifestData, contractPath, sourceGit)
    }

    private fun testBackwardCompatibility(sourceDir: File, contractPath: String, sourceGit: GitCommand, source: ContractSource) {
        val sourcePath = sourceDir.resolve(contractPath)
        val newVersion = sourcePath.readText()

        val oldVersion = try {
            sourceGit.show("HEAD", sourceDir.resolve(contractPath).path)
        } catch (e: Throwable) {
            ""
        }

        if (oldVersion.isNotEmpty()) {
            val newVersionFeature = Feature(newVersion)
            val oldVersionFeature = Feature(oldVersion)

            val results = testBackwardCompatibility2(oldVersionFeature, newVersionFeature)

            if (!results.success()) {
                println(results.report())
                println()
                exitWithMessage("The new version of ${source.pathDescriptor(contractPath)} is not backward compatible.")
            }
        }
    }

    private fun commitAndPush(sourceGit: GitCommand) {
        val pushRequired = try {
            sourceGit.commit()
            true
        } catch (e: NonZeroExitError) {
            if (!exitErrorMessageContains(e, listOf("nothing to commit")))
                throw e

            exitErrorMessageContains(e, listOf("branch is ahead of"))
        }

        when {
            pushRequired -> {
                println("Publishing the updates")
                sourceGit.push()
            }
            else -> println("Nothing to publish, old and new are identical, no push required.")
        }
    }

    private fun registerAzureCredentials(manifestData: JSONObjectValue, path: String, sourceGit: GitCommand) {
        println("Manifest has azure credentials, checking if they are already registered")
        val azureInfo = manifestData.getJSONObject(azurePipelineKeyInQontractManifest)
        if (hasAzureData(azureInfo)) {
            val filePath = File(path)
            val metaDataFile = File("${filePath.parent}/${filePath.nameWithoutExtension}.json")

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
                println("Updating the contract manifest to run this project's CI when ${filePath.name} changes...")
                val newPipelines = JSONArrayValue(pipelines.list.plus(JSONObjectValue(azureInfo)))
                val newMetaData = metaData.jsonObject.plus(azurePipelinesKeyInContractMetaData to newPipelines)

                metaDataFile.writeText(JSONObjectValue(newMetaData).toStringValue())

                sourceGit.add()
            }
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
