package application

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import picocli.CommandLine
import run.qontract.core.utilities.ContractPathData
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Callable
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@CommandLine.Command(name = "bundle",
        mixinStandardHelpOptions = true,
        description = ["Generate a zip file of all stub contracts in qontract.json"])
class BundleCommand : Callable<Unit> {
    @CommandLine.Option(names = ["--bundlePath"], description = ["path in which to write the contract"], required = false, defaultValue = "./bundle.zip")
    lateinit var bundlePath: String

    @Autowired
    lateinit var qontractConfig: QontractConfig

    @Autowired
    lateinit var zipper: Zipper

    @Autowired
    lateinit var fileReader: RealFileReader

    override fun call() {
        val zipperEntries = qontractConfig.contractStubPathData().map { pathDataToZipperEntry(it, fileReader) }
        zipper.compress(bundlePath, zipperEntries)
    }
}

fun pathDataToZipperEntry(pathData: ContractPathData, reader: RealFileReader): ZipperEntry {
    val base = File(pathData.baseDir)
    val contractFile = File(pathData.absolutePath)

    val relativePath = contractFile.relativeTo(base).path
    val zipEntryName = "${base.name}/$relativePath"

    return ZipperEntry(zipEntryName, reader.readBytes(pathData.absolutePath))
}

@Component
class Zipper {
    fun compress(zipFilePath: String, zipperEntries: List<ZipperEntry>) {
        FileOutputStream(zipFilePath).use { zipFile ->
            ZipOutputStream(zipFile).use { zipOut ->
                for(zipperEntry in zipperEntries) {
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
