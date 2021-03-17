@file:JvmName("BundleCommand_Jvm")

package application

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import picocli.CommandLine
import `in`.specmatic.core.CONTRACT_EXTENSION
import `in`.specmatic.core.utilities.ContractPathData
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Callable
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@CommandLine.Command(name = "bundle",
        mixinStandardHelpOptions = true,
        description = ["Generate a zip file of all stub contracts in $CONTRACT_EXTENSION.json"])
class BundleCommand : Callable<Unit> {
    @CommandLine.Option(names = ["--bundlePath"], description = ["path in which to write the contract"], required = false, defaultValue = "./bundle.zip")
    lateinit var bundlePath: String

    @Autowired
    lateinit var qontractConfig: QontractConfig

    @Autowired
    lateinit var zipper: Zipper

    @Autowired
    lateinit var fileOperations: FileOperations

    override fun call() {
        val zipperEntries = qontractConfig.contractStubPathData().flatMap { pathDataToEntryPath(it, fileOperations) }
        zipper.compress(bundlePath, zipperEntries)
    }
}

fun pathDataToEntryPath(pathData: ContractPathData, fileOperations: FileOperations): List<ZipperEntry> {
    val base = File(pathData.baseDir)
    val contractFile = File(pathData.path)

    val relativePath = contractFile.relativeTo(base).path
    val zipEntryName = "${base.name}/$relativePath"

    val stubDataDir = stubDataDir(File(pathData.path))
    val stubFiles = stubFilesIn(stubDataDir, fileOperations)

    val stubEntries = stubFiles.map {
        val relativeEntryPath = File(it).relativeTo(base)
        ZipperEntry("${base.name}/${relativeEntryPath.path}", fileOperations.readBytes(it))
    }

    val contractEntry = ZipperEntry(zipEntryName, fileOperations.readBytes(pathData.path))

    return listOf(contractEntry).plus(stubEntries)
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
