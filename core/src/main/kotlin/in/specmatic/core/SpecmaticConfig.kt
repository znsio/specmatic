package `in`.specmatic.core

import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.utilities.readFile
import java.io.File

const val APPLICATION_NAME = "Specmatic"
const val APPLICATION_NAME_LOWER_CASE = "specmatic"
const val APPLICATION_NAME_LOWER_CASE_LEGACY = "qontract"
const val CONTRACT_EXTENSION = "spec"
const val LEGACY_CONTRACT_EXTENSION = "qontract"
val CONTRACT_EXTENSIONS = listOf(CONTRACT_EXTENSION, LEGACY_CONTRACT_EXTENSION)
const val DATA_DIR_SUFFIX = "_data"
const val SPECMATIC_GITHUB_ISSUES = "https://github.com/znsio/specmatic/issues"

fun invalidContractExtensionMessage(filename: String): String {
    return "The file $filename does not seem like a contract file. Valid extensions for contract files are ${CONTRACT_EXTENSIONS.joinToString(", ")}"
}

fun String.isContractFile(): Boolean {
    return File(this).extension in CONTRACT_EXTENSIONS
}

fun String.loadContract(): Feature {
    if(!this.isContractFile())
        throw ContractException(invalidContractExtensionMessage(this))

    return parseGherkinStringToFeature(readFile(this))
}
