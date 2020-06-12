package run.qontract.core

import run.qontract.core.Result.Failure
import run.qontract.core.Result.Success
import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.Pattern
import run.qontract.core.pattern.Row
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
        return when {
            name != value.name -> Failure("The contract expected a part name to be $name, but got ${value.name}", fluff = true)
            value !is MultiPartContentValue -> Failure("The contract expected content, but got a file.")
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
        return when {
            name != value.name -> Failure("The contract expected a part name to be $name, but got ${value.name}.", fluff = true)
            value !is MultiPartFileValue -> Failure("The contract expected a file, but got content instead.")
            contentType != null && value.contentType != contentType -> Failure("The contract expected ${contentType.let { "content type $contentType" }}, but got ${value.contentType?.let { "content type $value.contentType" } ?: "no content type."}.")
            contentEncoding != null && value.contentEncoding != contentEncoding -> {
                val contentEncodingMessage = contentEncoding.let { "content encoding $contentEncoding" }
                val receivedContentEncodingMessage = value.contentEncoding?.let { "content encoding ${value.contentEncoding}" }
                        ?: "no content encoding"

                Failure("The contract expected ${contentEncodingMessage}, but got ${receivedContentEncodingMessage}.", breadCrumb = "contentEncoding")
            }
            else -> Success()
        }
    }
}
