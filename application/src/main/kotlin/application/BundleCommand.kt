@file:JvmName("BundleCommand_Jvm")

package application

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import picocli.CommandLine
import `in`.specmatic.core.CONTRACT_EXTENSION
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.utilities.ContractPathData
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

class StubBundle(private val _bundlePath: String?, private val config: QontractConfig, private val fileOperations: FileOperations) : Bundle {
    override val bundlePath = _bundlePath ?: "./bundle.zip"

    override fun contractPathData(): List<ContractPathData> {
        return config.contractStubPathData()
    }

    override fun ancillaryEntries(pathData: ContractPathData): List<ZipperEntry> {
        val base = File(pathData.baseDir)

        val stubDataDir = stubDataDir(File(pathData.path))
        val stubFiles = stubFilesIn(stubDataDir, fileOperations)

        return stubFiles.map {
            val relativeEntryPath = File(it).relativeTo(base)
            ZipperEntry("${base.name}/${relativeEntryPath.path}", fileOperations.readBytes(it))
        }
    }

    override fun configEntry(): List<ZipperEntry> = emptyList()
}

class TestBundle(private val _bundlePath: String?, private val config: QontractConfig, private val fileOperations: FileOperations) : Bundle {
    override val bundlePath: String = _bundlePath ?: "./test-bundle.zip"

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
        description = ["Generate a zip file of all stub contracts in $CONTRACT_EXTENSION.json"])
class BundleCommand : Callable<Unit> {
    @CommandLine.Option(names = ["--bundlePath"], description = ["Path in which to create the bundle"], required = false)
    var bundlePath: String? = null

    @CommandLine.Option(names = ["--test"], description = ["Create a bundle from of the test components"], required = false)
    var testBundle: Boolean = false

    @Autowired
    lateinit var qontractConfig: QontractConfig

    @Autowired
    lateinit var zipper: Zipper

    @Autowired
    lateinit var fileOperations: FileOperations

    override fun call() {
        val bundle = when {
            testBundle -> TestBundle(bundlePath, qontractConfig, fileOperations)
            else -> StubBundle(bundlePath, qontractConfig, fileOperations)
        }

        val pathData = bundle.contractPathData()

        val zipperEntries = pathData.flatMap { contractPathData ->
            pathDataToZipperEntry(bundle, contractPathData, fileOperations)
        }.plus(bundle.configEntry())

        zipper.compress(bundle.bundlePath, zipperEntries)
    }
}

private fun yamlFileName(path: String): String = path.removeSuffix(".spec") + ".yaml"

private fun yamlExists(pathData: String): Boolean =
        File(yamlFileName(pathData)).exists()

fun pathDataToZipperEntry(bundle: Bundle, pathData: ContractPathData, fileOperations: FileOperations): List<ZipperEntry> {
    val base = File(pathData.baseDir)
    val contractFile = File(pathData.path)

    val relativePath = contractFile.relativeTo(base).path
    val zipEntryName = "${base.name}/$relativePath"

    val contractEntry = ZipperEntry(zipEntryName, fileOperations.readBytes(pathData.path))
    val ancillaryEntries = bundle.ancillaryEntries(pathData)

    return listOf(contractEntry).plus(ancillaryEntries)
}

fun stubFilesIn(stubDataDir: String, fileOperations: FileOperations): List<String> =
        fileOperations.files(stubDataDir).flatMap {
            when {
                fileOperations.isJSONFile(it) -> listOf(it.path)
                it.isDirectory -> stubFilesIn(File(stubDataDir).resolve(it.name).path, fileOperations)
                else -> emptyList()
            }
        }

fun stubDataDir(path: File): String {
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
