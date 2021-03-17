package run.qontract.core

import run.qontract.core.pattern.ContractException
import java.io.File

data class QontractFilePath(val path: String, val relativeTo: String = "") {
    fun readFeatureForValue(valueName: String): Feature {
        println(relativeTo)
        return file().let {
            println(it)
            if(!it.exists())
                throw ContractException("$APPLICATION_NAME file $path does not exist, but is used as the source of variables in value $valueName")

            parseGherkinStringToFeature(it.readText())
        }
    }

    fun file(): File {
        return when {
            relativeTo.isNotBlank() -> File(relativeTo).absoluteFile.parentFile.resolve(path)
            else -> File(path)
        }
    }
}