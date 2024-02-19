@file:JvmName("BundleCommand_Jvm")

package application

import `in`.specmatic.core.APPLICATION_NAME_LOWER_CASE
import `in`.specmatic.core.YAML
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.utilities.ContractPathData
import `in`.specmatic.stub.customImplicitStubBase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import picocli.CommandLine
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Callable
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

interface Bundle {
    fun contractPathData(): List<ContractPathData>
    fun ancillaryEntries(pathData: ContractPathData): List<ZipperEntry>
    fun configEntry(): List<ZipperEntry>
    val bundlePath: String
}

class StubBundle(bundlePath: String?, private val config: SpecmaticConfig, private val fileOperations: FileOperations) : Bundle {
    override val bundlePath = bundlePath ?: "./bundle.zip"

    override fun contractPathData(): List<ContractPathData> {
        return config.contractStubPathData()
    }

    override fun ancillaryEntries(pathData: ContractPathData): List<ZipperEntry> {
        val customImplicitStubBaseEntries: List<ZipperEntry> = customImplicitStubBase()?.let { zipperEntriesFromCustomImplicitBase(pathData, it) } ?: emptyList()
        val defaultBaseEntries = zipperEntriesFromDefaultBase(pathData)

        return deDup(defaultBaseEntries.plus(customImplicitStubBaseEntries))
    }

    private fun deDup(entries: List<ZipperEntry>): List<ZipperEntry> {
        val hashMap = entries.fold(HashMap<String, ZipperEntry>()) { hashMap, entry ->
            hashMap[entry.path] = entry
            hashMap
        }

        return hashMap.entries.map {
            it.value
        }
    }

    private fun zipperEntriesFromDefaultBase(pathData: ContractPathData): List<ZipperEntry> {
        val base = File(pathData.baseDir)

        val stubDataDir = stubDataDirRelative(File(pathData.path))
        val stubFiles = stubFilesIn(stubDataDir, fileOperations)

        return stubFiles.map {
            val relativeEntryPath = File(it).relativeTo(base)
            ZipperEntry("${base.name}/${relativeEntryPath.path}", fileOperations.readBytes(it))
        }
    }

    private fun zipperEntriesFromCustomImplicitBase(
        pathData: ContractPathData,
        customImplicitStubBase: String
    ): List<ZipperEntry> {
        val base = File(pathData.baseDir)
        val contractRelativePath = File(pathData.path).relativeTo(base)

        val stubRelativePath = contractRelativePath.parent?.let {
            "${contractRelativePath.parent}/${contractRelativePath.nameWithoutExtension}_data"
        } ?: "${contractRelativePath.nameWithoutExtension}_data"

        val stubFiles: List<Pair<String, String>> =
            stubFilesIn(base, File(customImplicitStubBase), File(stubRelativePath))

        return stubFiles.map { (virtualPath, actualPath) ->
            val relativeEntryPath = File(virtualPath).relativeTo(base)
            ZipperEntry("${base.name}/${relativeEntryPath.path}", fileOperations.readBytes(actualPath))
        }
    }

    override fun configEntry(): List<ZipperEntry> = emptyList()
}

class TestBundle(bundlePath: String?, private val config: SpecmaticConfig, private val fileOperations: FileOperations) : Bundle {
    override val bundlePath: String = bundlePath ?: "./test-bundle.zip"

    override fun contractPathData(): List<ContractPathData> {
        return config.contractTestPathData()
    }

    override fun ancillaryEntries(pathData: ContractPathData): List<ZipperEntry> {
        val base = File(pathData.baseDir)

        return if(yamlExists(pathData.path)) {
            val yamlFilePath = File(yamlFileName(pathData.path))
            val yamlRelativePath = yamlFilePath.relativeTo(base).path
            val yamlEntryName = "${base.name}/$yamlRelativePath"
            val yamlEntry = ZipperEntry(yamlEntryName, fileOperations.readBytes(yamlFilePath.path))

            listOf(yamlEntry)
        } else {
            emptyList()
        }
    }

    override fun configEntry(): List<ZipperEntry> {
        val configEntryName = File(config.configFilePath).name
        val configContent = fileOperations.readBytes(config.configFilePath)
        return listOf(ZipperEntry(configEntryName, configContent))
    }
}

@CommandLine.Command(name = "bundle",
        mixinStandardHelpOptions = true,
        description = ["Generate a zip file of all stub contracts in $APPLICATION_NAME_LOWER_CASE.json"])
class BundleCommand : Callable<Unit> {
    @CommandLine.Option(names = ["--bundlePath"], description = ["Path in which to create the bundle"], required = false)
    var bundlePath: String? = null

    @CommandLine.Option(names = ["--test"], description = ["Create a bundle from of the test components"], required = false)
    var testBundle: Boolean = false

    @Autowired
    lateinit var specmaticConfig: SpecmaticConfig

    @Autowired
    lateinit var zipper: Zipper

    @Autowired
    lateinit var fileOperations: FileOperations

    var bundleOutputPath: String? = null

    override fun call() {
        createBundle(bundlePath, specmaticConfig, fileOperations, zipper, testBundle, bundleOutputPath)
    }

}

private fun createBundle(
    bundlePath: String?,
    specmaticConfig: SpecmaticConfig,
    fileOperations: FileOperations,
    zipper: Zipper,
    testBundle: Boolean,
    bundleOutputPath: String?
) {
    val bundle = when {
        testBundle -> TestBundle(bundlePath, specmaticConfig, fileOperations)
        else -> StubBundle(bundlePath, specmaticConfig, fileOperations)
    }

    val pathData = bundle.contractPathData()

    val zipperEntries = pathData.flatMap { contractPathData ->
        pathDataToZipperEntry(bundle, contractPathData, fileOperations)
    }.plus(bundle.configEntry())

    zipper.compress(bundleOutputPath ?: bundle.bundlePath, zipperEntries)
}

private fun yamlFileName(path: String): String = path.removeSuffix(".spec") + ".${YAML}"

private fun yamlExists(pathData: String): Boolean =
        File(yamlFileName(pathData)).exists()

fun pathDataToZipperEntry(bundle: Bundle, pathData: ContractPathData, fileOperations: FileOperations): List<ZipperEntry> {
    val base = File(pathData.baseDir)
    val contractFile = File(pathData.path)

    val relativePath = contractFile.relativeTo(base).path
    val zipEntryName = "${base.name}/$relativePath"

    logger.debug("Reading contract ${pathData.path} (Canonical path: ${File(pathData.path).canonicalPath})")
    val contractEntry = ZipperEntry(zipEntryName, fileOperations.readBytes(pathData.path))
    val ancillaryEntries = bundle.ancillaryEntries(pathData)

    return listOf(contractEntry).plus(ancillaryEntries)
}

fun stubFilesIn(base: File, customImplicitStubBase: File, stubRelativePath: File): List<Pair<String, String>> {
    return base.resolve(customImplicitStubBase).resolve(stubRelativePath).listFiles()?.flatMap {
        when {
            it.extension == "json" -> {
                val actualPath = it.path
                val virtualPath = base.resolve(it.relativeTo(base.resolve(customImplicitStubBase))).path

                listOf(Pair(virtualPath, actualPath))
            }
            it.isDirectory -> stubFilesIn(base, customImplicitStubBase, it.relativeTo(base.resolve(customImplicitStubBase)))
            else -> emptyList()
        }
    } ?: emptyList()
}

fun stubFilesIn(stubDataDir: String, fileOperations: FileOperations): List<String> =
        fileOperations.files(stubDataDir).flatMap {
            when {
                fileOperations.isJSONFile(it) -> listOf(it.path)
                it.isDirectory -> stubFilesIn(File(stubDataDir).resolve(it.name).path, fileOperations)
                else -> emptyList()
            }
        }

fun stubDataDirRelative(path: File): String {
    return "${path.parent}/${path.nameWithoutExtension}_data"
}

@Component
class Zipper {
    fun compress(zipFilePath: String, zipperEntries: List<ZipperEntry>) {
        logger.log("Writing contracts to $zipFilePath")

        FileOutputStream(zipFilePath).use { zipFile ->
            ZipOutputStream(zipFile).use { zipOut ->
                for (zipperEntry in zipperEntries) {
                    val entry = ZipEntry(zipperEntry.path)
                    zipOut.putNextEntry(entry)
                    zipOut.write(zipperEntry.bytes)
                }
            }
        }
    }
}

data class ZipperEntry(val path: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ZipperEntry

        if (path != other.path) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}
