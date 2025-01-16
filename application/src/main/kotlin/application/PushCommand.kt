package application

import com.fasterxml.jackson.databind.ObjectMapper
import io.specmatic.core.*
import io.specmatic.core.Configuration.Companion.configFilePath
import io.specmatic.core.SpecmaticConfig
import io.specmatic.core.config.toSpecmaticConfig
import io.specmatic.core.git.NonZeroExitError
import io.specmatic.core.git.SystemGit
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.utilities.*
import io.specmatic.core.value.JSONObjectValue
import picocli.CommandLine
import java.io.File
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@CommandLine.Command(
    name = "push",
    description = [
"""
Check the new contract for backward compatibility with the specified version, then overwrite the old one with it.
DEPRECATED: This command will be removed in the next major release. Use 'backward-compatibility-check' command instead.
"""
    ],
    mixinStandardHelpOptions = true
)
class PushCommand: Callable<Unit> {
    override fun call() {
        val userHome = File(System.getProperty("user.home"))
        val workingDirectory = userHome.resolve(".$APPLICATION_NAME_LOWER_CASE/repos")
        val manifestData = try {
            loadSpecmaticConfigOrDefault(configFilePath)
        } catch (e: ContractException) {
            exitWithMessage(e.failure().toReport().toText())
        }
        val sources = try { loadSources(manifestData) } catch(e: ContractException) { exitWithMessage(e.failure().toReport().toText()) }

        val unsupportedSources = sources.filter { it !is GitSource }.mapNotNull { it.type }.distinct()

        if(unsupportedSources.isNotEmpty()) {
            println("The following types of sources are not supported: ${unsupportedSources.joinToString(", ")}")
        }

        val supportedSources = sources.filter { it is GitSource }

        for (source in supportedSources) {
            val sourceDir = source.directoryRelativeTo(workingDirectory)
            val sourceGit = SystemGit(sourceDir.path)

            try {
                if (sourceGit.workingDirectoryIsGitRepo()) {
                    source.getLatest(sourceGit)

                    val changedSpecFiles = sourceGit.getChangedFiles().filter {
                        File(it).extension in CONTRACT_EXTENSIONS
                    }
                    for (contractPath in changedSpecFiles) {
                        testBackwardCompatibility(sourceDir, contractPath, sourceGit, source)
                        subscribeToContract(manifestData, sourceDir.resolve(contractPath).path, sourceGit)
                    }

                    for (contractPath in changedSpecFiles) {
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
                println(results.report(PATH_NOT_RECOGNIZED_ERROR))
                println()
                exitWithMessage("The new version of ${source.pathDescriptor(contractPath)} is not backward compatible.")
            }
        }
    }
}

fun hasAzureData(azureInfo: Pipeline): Boolean {
    return when {
        azureInfo.organization.isBlank() -> {
            println("Azure info must contain \"organisation\"")
            false
        }

        azureInfo.project.isBlank() -> {
            println("Azure info must contain \"project\"")
            false
        }

        azureInfo.definitionId.equals(0) -> {
            println("Azure info must contain \"definitionId\"")
            false
        }

        else -> true
    }
}

fun subscribeToContract(manifestData: SpecmaticConfig, contractPath: String, sourceGit: SystemGit) {
    println("Checking to see if manifest has CI credentials")

    if (manifestData.pipeline != null)
        registerPipelineCredentials(manifestData, contractPath, sourceGit)
}

fun registerPipelineCredentials(manifestData: SpecmaticConfig, contractPath: String, sourceGit: SystemGit) {
    println("Manifest has pipeline credentials, checking if they are already registered")

    val provider = manifestData.pipeline?.provider
    val manifestPipelineInfo = manifestData.pipeline

    if (provider != null && manifestPipelineInfo != null && provider == PipelineProvider.azure && hasAzureData(
            manifestPipelineInfo
        )
    ) {
        val filePath = File(contractPath)
        val specmaticConfigFile = File("${filePath.parent}/${filePath.nameWithoutExtension}.json")

        val specmaticConfig = when {
            specmaticConfigFile.exists() -> specmaticConfigFile.toSpecmaticConfig()
            else -> {
                println("Could not find Specmatic config file")
                SpecmaticConfig()
            }
        }

        if (specmaticConfig.pipeline?.equals(manifestPipelineInfo) != true) {
            println("Updating the contract manifest to run this project's CI when ${filePath.name} changes...")

            specmaticConfig.pipeline = manifestPipelineInfo
            val configJson =
                JSONObjectValue(jsonStringToValueMap(ObjectMapper().writeValueAsString(specmaticConfig)))
            specmaticConfigFile.writeText(configJson.toStringLiteral())

            sourceGit.add()
        }
    }
}
