package `in`.specmatic.core

import java.io.File

data class AnchorFile(val anchorFilePath: String): RelativeTo {
    override fun resolve(path: String): File {
        return File(anchorFilePath).absoluteFile.parentFile.resolve(path).canonicalFile
    }
}