package application.backwardCompatibility

import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.CONTRACT_EXTENSION
import io.specmatic.core.CONTRACT_EXTENSIONS
import io.specmatic.core.Feature
import io.specmatic.core.IFeature
import io.specmatic.core.Results
import io.specmatic.core.WSDL
import io.specmatic.core.testBackwardCompatibility
import io.specmatic.stub.isOpenAPI
import org.springframework.stereotype.Component
import picocli.CommandLine.Command
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern
import kotlin.io.path.extension
import kotlin.io.path.pathString

@Component
@Command(
    name = "backwardCompatibilityCheck",
    aliases = ["backward-compatibility-check"],
    mixinStandardHelpOptions = true,
    description = ["Checks backward compatibility of a directory across the current HEAD and the base branch"]
)
class BCCheckCommand: BackwardCompatibilityCheckBaseCommand() {

    override fun checkBackwardCompatibility(oldFeature: IFeature, newFeature: IFeature): Results {
        return testBackwardCompatibility(oldFeature as Feature, newFeature as Feature)
    }

    override fun File.isValidSpec(): Boolean {
        if (this.extension !in CONTRACT_EXTENSIONS) return false
        return OpenApiSpecification.isParsable(this.path)
    }

    override fun getFeatureFromSpecPath(path: String): Feature {
        return OpenApiSpecification.fromFile(path).toFeature()
    }

    override fun getSpecsReferringTo(schemaFiles: Set<String>): Set<String> {
        if (schemaFiles.isEmpty()) return emptySet()

        val inputFileNames = schemaFiles.map { File(it).name }
        val result = allOpenApiSpecFiles().filter {
            it.readText().trim().let { specContent ->
                inputFileNames.any { inputFileName ->
                    val pattern = Pattern.compile("\\b$inputFileName\\b")
                    val matcher = pattern.matcher(specContent)
                    matcher.find()
                }
            }
        }.map { it.path }.toSet()

        return result.flatMap {
            getSpecsReferringTo(setOf(it)).ifEmpty { setOf(it) }
        }.toSet()
    }

    override fun getSpecsOfChangedExternalisedExamples(filesChangedInCurrentBranch: Set<String>): Set<String> {
        data class CollectedFiles(
            val specifications: MutableSet<String> = mutableSetOf(),
            val examplesMissingSpecifications: MutableList<String> = mutableListOf(),
            val ignoredFiles: MutableList<String> = mutableListOf()
        )

        val collectedFiles = filesChangedInCurrentBranch.fold(CollectedFiles()) { acc, filePath ->
            val path = Paths.get(filePath)
            val examplesDir = path.find { it.toString().endsWith("_examples") || it.toString().endsWith("_tests") }

            if (examplesDir == null) {
                acc.ignoredFiles.add(filePath)
            } else {
                val parentPath = examplesDir.parent
                val strippedPath = parentPath.resolve(examplesDir.fileName.toString().removeSuffix("_examples"))
                val specFiles = findSpecFiles(strippedPath)

                if (specFiles.isNotEmpty()) {
                    acc.specifications.addAll(specFiles.map { it.toString() })
                } else {
                    acc.examplesMissingSpecifications.add(filePath)
                }
            }
            acc
        }

        val result = collectedFiles.specifications.toMutableSet()

        collectedFiles.examplesMissingSpecifications.forEach { filePath ->
            val path = Paths.get(filePath)
            val examplesDir = path.find { it.toString().endsWith("_examples") || it.toString().endsWith("_tests") }
            if (examplesDir != null) {
                val parentPath = examplesDir.parent
                val strippedPath = parentPath.resolve(examplesDir.fileName.toString().removeSuffix("_examples"))
                val specFiles = findSpecFiles(strippedPath)
                if (specFiles.isNotEmpty()) {
                    result.addAll(specFiles.map { it.toString() })
                } else {
                    result.add("${strippedPath}.yaml")
                }
            }
        }

        return result
    }

    override fun areExamplesValid(feature: IFeature, which: String): Boolean {
        feature as Feature
        return try {
            feature.validateExamplesOrException()
            true
        } catch (t: Throwable) {
            println()
            false
        }
    }

    override fun getUnusedExamples(feature: IFeature): Set<String> {
        feature as Feature
        return feature.loadExternalisedExamplesAndListUnloadableExamples().second
    }

    private fun allOpenApiSpecFiles(): List<File> {
        return File(".").walk().toList().filterNot {
            ".git" in it.path
        }.filter { it.isFile && it.isValidSpec() }
    }

    private fun findSpecFiles(path: Path): List<Path> {
        val extensions = CONTRACT_EXTENSIONS
        return extensions.map { path.resolveSibling(path.fileName.toString() + it) }
            .filter { Files.exists(it) && (isOpenAPI(it.pathString) || it.extension in listOf(WSDL, CONTRACT_EXTENSION)) }
    }
}