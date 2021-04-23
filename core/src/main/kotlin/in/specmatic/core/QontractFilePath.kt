package `in`.specmatic.core

import `in`.specmatic.core.pattern.ContractException
import java.io.File

data class QontractFilePath(val path: String, val relativeTo: String = "") {
    fun readFeatureForValue(valueName: String): Feature {
        return file().let {
            if(!it.exists())
                throw ContractException("$APPLICATION_NAME file $path does not exist, but is used as the source of variables in value $valueName")

            parseGherkinStringToFeature(it.readText(), it.absolutePath)
        }
    }

    fun file(): File {
        return when {
            relativeTo.isNotBlank() -> File(relativeTo).absoluteFile.parentFile.resolve(path)
            else -> File(path)
        }
    }
}