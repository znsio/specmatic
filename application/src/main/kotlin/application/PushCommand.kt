package application

import picocli.CommandLine
import `in`.specmatic.core.*
import `in`.specmatic.core.Constants.Companion.DEFAULT_QONTRACT_CONFIG_IN_CURRENT_DIRECTORY
import `in`.specmatic.core.git.NonZeroExitError
import `in`.specmatic.core.git.SystemGit
import `in`.specmatic.core.git.loadFromPath
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.parsedJSON
import `in`.specmatic.core.utilities.*
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.Value
import java.io.File
import java.util.concurrent.Callable
import kotlin.system.exitProcess

private const val pipelineKeyInQontracConfig = "pipeline"

@CommandLine.Command(name = "push", description = ["Check the new contract for backward compatibility with the specified version, then overwrite the old one with it."], mixinStandardHelpOptions = true)
class PushCommand: Callable<Unit> {
    override fun call() {
        val userHome = File(System.getProperty("user.home"))
        val workingDirectory = userHome.resolve(".$CONTRACT_EXTENSION/repos")
        val manifestFile = File(DEFAULT_QONTRACT_CONFIG_IN_CURRENT_DIRECTORY)
        val manifestData = try { loadConfigJSON(manifestFile) } catch(e: ContractException) { exitWithMessage(resultReport(e.failure())) }
        val sources = try { loadSources(manifestData) } catch(e: ContractException) { exitWithMessage(resultReport(e.failure())) }

        for (source in sources) {
            val sourceDir = source.directoryRelativeTo(workingDirectory)
            val sourceGit = SystemGit(sourceDir.path)

            try {
                if (sourceGit.workingDirectoryIsGitRepo()) {
                    source.getLatest(sourceGit)

                    val changedQontractFiles = sourceGit.getChangedFiles().filter {
                        File(it).extension in CONTRACT_EXTENSIONS
                    }
                    for (contractPath in changedQontractFiles) {
                        testBackwardCompatibility(sourceDir, contractPath, sourceGit, source)
                        subscribeToContract(manifestData, sourceDir.resolve(contractPath).path, sourceGit)
                    }

                    for (contractPath in changedQontractFiles) {
                        sourceGit.add(contractPath)
                    }

                    source.pushUpdates(sourceGit)

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
            val newVersionFeature = parseGherkinStringToFeature(newVersion)
            val oldVersionFeature = parseGherkinStringToFeature(oldVersion)

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

    if (manifestData.jsonObject.containsKey(pipelineKeyInQontracConfig))
        registerPipelineCredentials(manifestData, contractPath, sourceGit)
}

fun registerPipelineCredentials(manifestData: JSONObjectValue, contractPath: String, sourceGit: SystemGit) {
    println("Manifest has pipeline credentials, checking if they are already registered")

    val provider = loadFromPath(manifestData, listOf(pipelineKeyInQontracConfig, "provider"))?.toStringValue()
    val pipelineInfo = manifestData.getJSONObject(pipelineKeyInQontracConfig)

    if (provider == "azure" && hasAzureData(pipelineInfo)) {
        val filePath = File(contractPath)
        val qontractMetaDataFile = File("${filePath.parent}/${filePath.nameWithoutExtension}.json")

        val pipelinesKeyInContractMetaData = "pipelines"

        val qontractMetaData = when {
            qontractMetaDataFile.exists() -> parsedJSON(qontractMetaDataFile.readText())
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
