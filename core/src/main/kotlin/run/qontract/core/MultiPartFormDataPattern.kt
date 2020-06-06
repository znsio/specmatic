package run.qontract.core

import run.qontract.core.Result.Failure
import run.qontract.core.Result.Success
import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.Pattern
import run.qontract.core.pattern.Row
import run.qontract.core.pattern.isPatternToken
import run.qontract.core.value.StringValue

sealed class MultiPartFormDataPattern(open val name: String) {
    abstract fun newBasedOn(row: Row, resolver: Resolver): List<MultiPartFormDataPattern>
    abstract fun generate(resolver: Resolver): MultiPartFormDataValue
    abstract fun matches(value: MultiPartFormDataValue, resolver: Resolver): Result
}

data class MultiPartContentPattern(override val name: String, val content: Pattern) : MultiPartFormDataPattern(name) {
    override fun newBasedOn(row: Row, resolver: Resolver): List<MultiPartFormDataPattern> {
        return run.qontract.core.pattern.newBasedOn(row, name, content, resolver).map { newContent -> MultiPartContentPattern(name, newContent) }
    }

    override fun generate(resolver: Resolver): MultiPartFormDataValue =
            MultiPartContentValue(name, content.generate(resolver))

    override fun matches(value: MultiPartFormDataValue, resolver: Resolver): Result {
        return _matches(value, resolver).let {
            if (it is Failure) it.breadCrumb("MULTIPART-FORMDATA") else it
        }
    }

    private fun _matches(value: MultiPartFormDataValue, resolver: Resolver): Result {
        return when {
            value !is MultiPartContentValue -> Failure("The contract expected a file, got a non-file part.")
            name != value.name -> Failure("The contract expected part name to be $name, but got ${value.name}", breadCrumb = "name")
            value.content is StringValue -> {
                try {
                    val parsedContent = try { content.parse(value.content.toStringValue(), resolver) } catch (e: Throwable) { StringValue(value.content.toStringValue()) }
                    content.matches(parsedContent, resolver)
                } catch (e: ContractException) {
                    Failure(e.report(), breadCrumb = "content")
                } catch (e: Throwable) {
                    Failure("Expected a ${content.typeName} but got ${value.content.toStringValue()}", breadCrumb = "content")
                }
            }
            else -> {
                content.matches(value.content, resolver)
            }
        }
    }
}

data class MultiPartFilePattern(override val name: String, val filename: String, val contentType: String? = null, val contentEncoding: String? = null) : MultiPartFormDataPattern(name) {
    override fun newBasedOn(row: Row, resolver: Resolver): List<MultiPartFormDataPattern> = listOf(this)
    override fun generate(resolver: Resolver): MultiPartFormDataValue =
            MultiPartFileValue(name, filename, contentType, contentEncoding)

    override fun matches(value: MultiPartFormDataValue, resolver: Resolver): Result {
        return _matches(value).let {
            if (it is Failure) it.breadCrumb("MULTIPART-FORMDATA") else it
        }
    }

    private fun _matches(value: MultiPartFormDataValue): Result {
        return when {
            value !is MultiPartFileValue -> Failure("The contract expected a file, but got content instead.")
            name != value.name -> Failure("The contract expected part name to be $name, but got ${value.name}.", breadCrumb = "name")
            value.filename.removePrefix("@") != filename.removePrefix("@") -> Failure("The contract expected filename $filename, but got ${value.filename}.", breadCrumb = "filename")
            value.contentType != contentType -> Failure("The contract expected ${contentType?.let { "content type $contentType" } ?: "no content type"}, but got ${value.contentType?.let { "content type $contentType" } ?: "no content type"}.", breadCrumb = "contentType")
            value.contentEncoding != contentEncoding -> {
                val contentEncodingMessage = contentEncoding?.let { "content encoding $contentEncoding" }
                        ?: "no content encoding"
                val receivedContentEncodingMessage = value.contentEncoding?.let { "content encoding ${value.contentEncoding}" }
                        ?: "no content encoding"

                Failure("The contract expected ${contentEncodingMessage}, but got ${receivedContentEncodingMessage}.", breadCrumb = "contentEncoding")
            }
            else -> Success()
        }
    }
}
