package application

import picocli.CommandLine
import run.qontract.core.Constants
import run.qontract.core.Constants.Companion.QONTRACT_CONFIG_IN_CURRENT_DIRECTORY
import run.qontract.core.Feature
import run.qontract.core.git.SystemGit
import run.qontract.core.git.NonZeroExitError
import run.qontract.core.git.exitErrorMessageContains
import run.qontract.core.git.loadFromPath
import run.qontract.core.pattern.parsedJSONStructure
import run.qontract.core.testBackwardCompatibility
import run.qontract.core.utilities.*
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.Value
import java.io.File
import java.util.concurrent.Callable
import kotlin.system.exitProcess

private const val pipelineKeyInQontractManifest = "pipeline"

@CommandLine.Command(name = "push", description = ["Check the new contract for backward compatibility with the specified version, then overwrite the old one with it."], mixinStandardHelpOptions = true)
class PushCommand: Callable<Unit> {
    override fun call() {
        val userHome = File(System.getProperty("user.home"))
        val workingDirectory = userHome.resolve(".qontract/repos")
        val manifestFile = File(QONTRACT_CONFIG_IN_CURRENT_DIRECTORY)
        val manifestData = loadJSONFromManifest(manifestFile)
        val sources = loadSourceDataFromManifest(manifestData)

        for(source in sources) {
            val sourceDir = source.directoryRelativeTo(workingDirectory)
            val sourceGit = SystemGit(sourceDir.path)

            try {
                if(sourceGit.workingDirectoryIsGitRepo()) {
                    if(source is GitRepo)
                        sourceGit.pull()

                    val changedQontractFiles = sourceGit.getChangedFiles().filter { it.endsWith(".qontract") }
                    for(contractPath in changedQontractFiles) {
                        testBackwardCompatibility(sourceDir, contractPath, sourceGit, source)
                        subscribeToContract(manifestData, sourceDir.resolve(contractPath).path, sourceGit)
                    }

                    for(contractPath in changedQontractFiles) {
                        sourceGit.add(contractPath)
                    }

                    if(source is GitRepo)
                        commitAndPush(sourceGit)

                    println("Done")
                }
            } catch (e: NonZeroExitError) {
                println("Couldn't push the latest. Got error: ${exceptionCauseMessage(e)}")
                exitProcess(1)
            }
        }
    }

    private fun testBackwardCompatibility(sourceDir: File, contractPath: String, sourceGit: SystemGit, source: ContractSource) {
        val sourcePath = sourceDir.resolve(contractPath)
        val newVersion = sourcePath.readText()

        val oldVersion = try {
            val gitRoot = File(sourceGit.gitRoot()).absoluteFile
            println("Git root: ${gitRoot.path}")
            println("Source path: ${sourcePath.absoluteFile.path}")
            val relativeSourcePath = sourcePath.absoluteFile.relativeTo(gitRoot)
            println("Relative source path: ${relativeSourcePath.path}")
            sourceGit.show("HEAD", relativeSourcePath.path)
        } catch (e: Throwable) {
            ""
        }

        if (oldVersion.isNotEmpty()) {
            val newVersionFeature = Feature(newVersion)
            val oldVersionFeature = Feature(oldVersion)

            val results = testBackwardCompatibility(oldVersionFeature, newVersionFeature)

            if (!results.success()) {
                println(results.report())
                println()
                exitWithMessage("The new version of ${source.pathDescriptor(contractPath)} is not backward compatible.")
            }
        }
    }
}

fun hasAzureData(azureInfo: Map<String, Value>): Boolean {
    val expectedKeys = listOf("organization", "project", "definitionId", "provider")
    val missingKey = expectedKeys.find { it !in azureInfo }

    return when(missingKey) {
        null -> true
        else -> {
            println("Azure info must contain the key \"organisation\"")
            false
        }
    }
}

fun subscribeToContract(manifestData: Value, contractPath: String, sourceGit: SystemGit) {
    println("Checking to see if manifest has CI credentials")

    if (manifestData !is JSONObjectValue)
        exitWithMessage("Manifest must contain a json object")

    if (manifestData.jsonObject.containsKey(pipelineKeyInQontractManifest))
        registerPipelineCredentials(manifestData, contractPath, sourceGit)
}

fun registerPipelineCredentials(manifestData: JSONObjectValue, contractPath: String, sourceGit: SystemGit) {
    println("Manifest has pipeline credentials, checking if they are already registered")

    val provider = loadFromPath(manifestData, listOf(pipelineKeyInQontractManifest, "provider"))?.toStringValue()
    val pipelineInfo = manifestData.getJSONObject(pipelineKeyInQontractManifest)

    if (provider == "azure" && hasAzureData(pipelineInfo)) {
        val filePath = File(contractPath)
        val qontractMetaDataFile = File("${filePath.parent}/${filePath.nameWithoutExtension}.json")

        val pipelinesKeyInContractMetaData = "pipelines"

        val qontractMetaData = when {
            qontractMetaDataFile.exists() -> parsedJSONStructure(qontractMetaDataFile.readText())
            else -> {
                println("Could not find metadata file")
                JSONObjectValue(mapOf(pipelinesKeyInContractMetaData to JSONArrayValue(emptyList())))
            }
        }

        if (qontractMetaData !is JSONObjectValue)
            exitWithMessage("Contract meta data must contain a json object")

        if (!qontractMetaData.jsonObject.containsKey(pipelinesKeyInContractMetaData))
            exitWithMessage("Contract meta data must contain the key \"azure-pipelines\"")

        val pipelines = qontractMetaData.jsonObject.getValue(pipelinesKeyInContractMetaData)
        if (pipelines !is JSONArrayValue)
            exitWithMessage("\"azure-pipelines\" key must contain a list of pipelines")

        if(pipelines.list.none { it is JSONObjectValue && it.jsonObject == pipelineInfo }) {
            println("Updating the contract manifest to run this project's CI when ${filePath.name} changes...")

            val newPipelines = JSONArrayValue(pipelines.list.plus(JSONObjectValue(pipelineInfo)))
            val newMetaData = qontractMetaData.jsonObject.plus(pipelinesKeyInContractMetaData to newPipelines)

            qontractMetaDataFile.writeText(JSONObjectValue(newMetaData).toStringValue())

            sourceGit.add()
        }
    }
}

fun commitAndPush(sourceGit: SystemGit) {
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
            println("Pushing changes")
            sourceGit.push()
        }
        else -> println("No changes were made to the repo, so nothing was pushed.")
    }
}
