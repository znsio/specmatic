package `in`.specmatic.core

import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.utils.io.streams.*
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.pattern.isPatternToken
import `in`.specmatic.core.pattern.parsedPattern
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import io.ktor.util.*
import io.ktor.utils.io.core.*
import java.io.File
import kotlin.text.String

const val CONTENT_DISPOSITION = "Content-Disposition"

sealed class MultiPartFormDataValue(open val name: String) {
    abstract fun inferType(): MultiPartFormDataPattern
    abstract fun toDisplayableValue(): String
    abstract fun toJSONObject(): JSONObjectValue
    abstract fun addTo(formBuilder: FormBuilder)
    abstract fun toClauseData(
        clauses: List<GherkinClause>,
        newTypes: Map<String, Pattern>,
        examples: ExampleDeclarations
    ): Triple<List<GherkinClause>, Map<String, Pattern>, ExampleDeclarations>
}

data class MultiPartContentValue(override val name: String, val content: Value, val boundary: String = "#####") : MultiPartFormDataValue(name) {
    override fun inferType(): MultiPartFormDataPattern {
        return MultiPartContentPattern(name, content.exactMatchElseType())
    }

    override fun toDisplayableValue(): String = """
--$boundary
$CONTENT_DISPOSITION: form-data; name="$name"
Content-Type: ${content.httpContentType}

$content
""".trim()

    override fun toJSONObject(): JSONObjectValue =
            JSONObjectValue(mapOf("name" to StringValue(name), "content" to StringValue(content.toStringLiteral()), "contentType" to StringValue(content.httpContentType)))

    @OptIn(InternalAPI::class)
    override fun addTo(formBuilder: FormBuilder) {
        formBuilder.append(name, content.toStringLiteral(), Headers.build {
            append(HttpHeaders.ContentType, ContentType.parse(content.httpContentType))
        })
    }

    override fun toClauseData(
        clauses: List<GherkinClause>,
        newTypes: Map<String, Pattern>,
        examples: ExampleDeclarations
    ): Triple<List<GherkinClause>, Map<String, Pattern>, ExampleDeclarations> {
        val (typeDeclaration, newExamples) = this.content.typeDeclarationWithKey(this.name, newTypes, examples)

        val newGherkinClause = GherkinClause(
            "request-part ${this.name} ${typeDeclaration.typeValue}",
            GherkinSection.When
        )
        return Triple(clauses.plus(newGherkinClause), typeDeclaration.types, examples.plus(newExamples))
    }
}

data class MultiPartContent(val bytes: ByteArray) {
    constructor(text: String): this(text.encodeToByteArray())
    constructor(file: File): this(file.readBytes())
    constructor(): this(ByteArray(0))

    val length: Long = bytes.size.toLong()
    val text: String = String(bytes)
    val input: Input = bytes.inputStream().asInput()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MultiPartContent

        if (!bytes.contentEquals(other.bytes)) return false
        if (length != other.length) return false
        if (text != other.text) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + length.hashCode()
        result = 31 * result + text.hashCode()
        return result
    }
}

data class MultiPartFileValue(override val name: String, val filename: String, val contentType: String? = null, val contentEncoding: String? = null, val content: MultiPartContent = MultiPartContent(), val boundary: String = "#####") : MultiPartFormDataValue(name) {
    override fun inferType(): MultiPartFormDataPattern {
        return MultiPartFilePattern(name, filenameToType(), contentType, contentEncoding)
    }

    private fun filenameToType() = parsedPattern(filename.removePrefix("@"))

    override fun toDisplayableValue(): String {
        val headers = mapOf (
                CONTENT_DISPOSITION to """form-data; name="$name"; filename="$filename"""",
                "Content-Type" to (contentType ?: ""),
                "Content-Encoding" to (contentEncoding ?: "")
        ).filter { it.value.isNotBlank() }

        val headerString = headers.entries.joinToString("\n") {
            "${it.key}: ${it.value}"
        }

        return """
--$boundary
$headerString

(File content not shown here.  Please examine the file ${filename.removePrefix("@")})
""".trim()
    }

    override fun toJSONObject() =
            JSONObjectValue(mapOf("name" to StringValue(name), "filename" to StringValue("@${filename}")).let { map ->
                when (contentType) {
                    null -> map
                    else -> map.plus("contentType" to StringValue(contentType))
                }
            }.let { map ->
                when (contentEncoding) {
                    null -> map
                    else -> map.plus("contentEncoding" to StringValue(contentEncoding))
                }
            })

    @OptIn(InternalAPI::class)
    override fun addTo(formBuilder: FormBuilder) {
        formBuilder.appendInput(name, Headers.build {
            if(contentType != null)
                append(HttpHeaders.ContentType, ContentType.parse(contentType))

            if(contentEncoding != null)
                append(HttpHeaders.ContentEncoding, contentEncoding)

            append("Content-Transfer-Encoding", "binary")

            val partFilePath = filename.removePrefix("@")
            val partFileName = File(partFilePath).name
            append(CONTENT_DISPOSITION, "filename=$partFileName")
        }, content.length) {
            content.input
        }
    }

    override fun toClauseData(clauses: List<GherkinClause>, newTypes: Map<String, Pattern>, examples: ExampleDeclarations ): Triple<List<GherkinClause>, Map<String, Pattern>, ExampleDeclarations> {
        val contentEncoding = this.contentType?.let { this.contentEncoding } ?: ""
        val contentType = this.contentType ?: ""

        val (newFilename, newExamples) = when {
            !isPatternToken(filename) -> {
                val filenameExampleName = examples.getNewName("${name}_filename", newTypes.keys)
                val newExamples = examples.plus(filenameExampleName to filename)

                Pair("(string)", newExamples)
            }
            isPatternToken(filename) && filename.trim() != "(string)" -> throw ContractException("Only (string) is supported as a type", name)
            else -> Pair(filename, examples)
        }

        return Triple(
            clauses.plus(
                GherkinClause(
                    "request-part ${this.name} @$newFilename $contentType $contentEncoding".trim(),
                    GherkinSection.When
                )
            ), newTypes, newExamples
        )
    }
}
