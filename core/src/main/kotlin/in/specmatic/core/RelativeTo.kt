package `in`.specmatic.core

import java.io.File

interface RelativeTo {
    fun resolve(path: String): File
}