package `in`.specmatic.core

import java.io.File

object NoAnchor: RelativeTo {
    override fun resolve(path: String): File {
        return File(path)
    }

}