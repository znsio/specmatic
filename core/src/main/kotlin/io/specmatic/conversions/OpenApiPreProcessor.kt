package io.specmatic.conversions

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

const val REF_KEY = "\$ref"

data class OpenApiPreProcessor (
    private val parsedYamlContent: Map<*, *>,
    private val openApiFile: File,
    private val openApiFilePath: Path = openApiFile.toPath().toAbsolutePath()
) {
    constructor(yamlContent: String, openApiFilePath: String): this(
        parsedYamlContent = mapper.readValue(yamlContent, Map::class.java),
        openApiFile = File(openApiFilePath).canonicalFile
    )

    fun toYAML(): String {
        return mapper.writeValueAsString(parsedYamlContent)
    }

    fun inlinePathReferences(): OpenApiPreProcessor {
        val pathsMap = parsedYamlContent["paths"] as? Map<*, *> ?: return this
        val updatedPaths = pathsMap.mapValues { (path, pathValue) ->
            if (path !is String || pathValue !is Map<*, *> || !pathValue.containsNonHttpRef()) return@mapValues pathValue
            val ref = pathValue["\$ref"] as? String ?: return@mapValues pathValue
            readFromRef(ref).relativizeRefs(from = ref)
        }

        val updatedYamlContent = parsedYamlContent.toMutableMap().apply { this["paths"] = updatedPaths }
        return this.copy(parsedYamlContent = updatedYamlContent)
    }

    private fun Any?.containsNonHttpRef(): Boolean {
        val ref = (this as? Map<*, *>)?.get(REF_KEY) as? String ?: return false
        return !ref.isHttpRef()
    }

    private fun readFromRef(ref: String): Map<*, *> {
        val refFilePath = openApiFile.canonicalFile.parentFile.resolve(ref)
        return mapper.readValue(refFilePath, Map::class.java)
    }

    private fun Any?.relativizeRefs(from: String): Any? {
        return when (this) {
            is Map<*, *> -> {
                this.mapValues { (key, value) ->
                    if (key != REF_KEY || value !is String) return@mapValues value.relativizeRefs(from)
                    relativizePaths(value, from)
                }
            }
            is List<*> -> this.map { it.relativizeRefs(from) }
            else -> this
        }
    }

    private fun relativizePaths(ref: String, from: String): String {
        if (ref.isHttpOrAbsolute()) return ref

        val to = openApiFilePath.parent ?: return ref
        val base = to.resolve(from).normalizeAndAbsolute().parent ?: return ref
        val target = base.resolve(ref).normalizeAndAbsolute()

        return to.relativize(target).toString().replace(File.separatorChar, '/')
    }

    private fun String.isHttpOrAbsolute(): Boolean {
        return this.isHttpRef() || Paths.get(this).isAbsolute
    }

    private fun String.isHttpRef(): Boolean {
        return this.startsWith("http://") || this.startsWith("https://")
    }

    private fun Path.normalizeAndAbsolute(): Path = this.normalize().toAbsolutePath()

    companion object {
        private val mapper = ObjectMapper(YAMLFactory())
    }
}
