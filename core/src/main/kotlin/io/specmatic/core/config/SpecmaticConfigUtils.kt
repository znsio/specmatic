package io.specmatic.core.config

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import io.specmatic.core.Configuration
import io.specmatic.core.Feature
import io.specmatic.core.config.transform.VersionTransform
import io.specmatic.core.log.logger
import io.specmatic.core.parseContractFileToFeature
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.utilities.exceptionCauseMessage
import java.io.File
import java.io.IOException

fun invalidContractExtensionMessage(filename: String): String {
    return "The file $filename does not seem like a contract file. Valid extensions for contract files are ${CONTRACT_EXTENSIONS.joinToString(", ")}"
}

fun String.isContractFile(): Boolean {
    return File(this).extension in CONTRACT_EXTENSIONS
}

fun String.loadContract(): Feature {
    if(!this.isContractFile())
        throw ContractException(invalidContractExtensionMessage(this))

    return parseContractFileToFeature(File(this))
}

fun loadSpecmaticConfigOrDefault(configFileName: String? = null): SpecmaticConfig {
    return if(configFileName == null)
	    SpecmaticConfig()
    else try {
	    loadSpecmaticConfig(configFileName)
    }
    catch (e: ContractException) {
        logger.log(exceptionCauseMessage(e))
	    SpecmaticConfig()
    }
}

fun loadSpecmaticConfig(configFileName: String? = null): SpecmaticConfig {
    val configFile = File(configFileName ?: Configuration.configFilePath)
    if (!configFile.exists()) {
        throw ContractException("Could not find the Specmatic configuration at path ${configFile.canonicalPath}")
    }
    try {
        return VersionTransform.parseConfigFile(configFile)
    } catch(e: LinkageError) {
        logger.log(e, "A dependency version conflict has been detected. If you are using Spring in a maven project, a common resolution is to set the property <kotlin.version></kotlin.version> to your pom project.")
        throw e
    } catch (e: JsonParseException) {
        val msg = "Invalid configuration file format: " + e.message
        logger.log(e, msg)
        throw IllegalArgumentException(msg, e)
    } catch (e: JsonMappingException) {
        val msg = "Your configuration file does not match the expected structure: " + e.message
        logger.log(e, msg)
        throw IllegalArgumentException(msg, e)
    } catch (e: IOException) {
        val msg = "Error reading configuration file: " + e.message
        logger.log(e, msg)
        throw RuntimeException(msg, e)
    } catch (e: Throwable) {
        logger.log(e, "Your configuration file may have some missing configuration sections. Please ensure that the ${configFile.path} file adheres to the schema described at: https://specmatic.io/documentation/specmatic_json.html")
        throw Exception("Your configuration file may have some missing configuration sections. Please ensure that the ${configFile.path} file adheres to the schema described at: https://specmatic.io/documentation/specmatic_json.html", e)
    }
}