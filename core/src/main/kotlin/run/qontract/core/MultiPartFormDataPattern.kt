package run.qontract.core

import run.qontract.core.Result.Failure
import run.qontract.core.Result.Success
import run.qontract.core.pattern.*
import run.qontract.core.value.StringValue

sealed class MultiPartFormDataPattern(open val name: String) {
    abstract fun newBasedOn(row: Row, resolver: Resolver): List<MultiPartFormDataPattern?>
    abstract fun generate(resolver: Resolver): MultiPartFormDataValue
    abstract fun matches(value: MultiPartFormDataValue, resolver: Resolver): Result
    abstract fun nonOptional(): MultiPartFormDataPattern
}

data class MultiPartContentPattern(override val name: String, val content: Pattern) : MultiPartFormDataPattern(name) {
    override fun newBasedOn(row: Row, resolver: Resolver): List<MultiPartContentPattern?> =
            newBasedOn(row, withoutOptionality(name), content, resolver).map { newContent -> MultiPartContentPattern(withoutOptionality(name), newContent) }.let {
                when{
                    isOptional(name) && !row.containsField(withoutOptionality(name)) -> listOf(null).plus(it)
                    else -> it
                }
            }

    override fun generate(resolver: Resolver): MultiPartFormDataValue =
            MultiPartContentValue(name, content.generate(resolver))

    override fun matches(value: MultiPartFormDataValue, resolver: Resolver): Result {
        return when {
            name != value.name -> Failure("The contract expected a part name to be $name, but got ${value.name}", failureReason = FailureReason.PartNameMisMatch)
            value !is MultiPartContentValue -> Failure("The contract expected content, but got a file.")
            value.content is StringValue -> {
                try {
                    val parsedContent = try { content.parse(value.content.toStringValue(), resolver) } catch (e: Throwable) { StringValue(value.content.toStringValue()) }
                    resolver.matchesPattern(name, content, parsedContent)
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

    override fun nonOptional(): MultiPartFormDataPattern {
        return copy(name = withoutOptionality(name))
    }
}

data class MultiPartFilePattern(override val name: String, val filename: Pattern, val contentType: String? = null, val contentEncoding: String? = null) : MultiPartFormDataPattern(name) {
    override fun newBasedOn(row: Row, resolver: Resolver): List<MultiPartFormDataPattern?> = listOf(this)
    override fun generate(resolver: Resolver): MultiPartFormDataValue =
            MultiPartFileValue(name, filename.generate(resolver).toStringValue(), contentType, contentEncoding)

    override fun matches(value: MultiPartFormDataValue, resolver: Resolver): Result {
        return when {
            value !is MultiPartFileValue -> Failure("The contract expected a file, but got content instead.")
            name != value.name -> Failure("The contract expected a part name to be $name, but got ${value.name}.", failureReason = FailureReason.PartNameMisMatch)
            !filename.matches(StringValue(value.filename), resolver).isTrue() -> Failure("In the part named $name, the contract expected the filename to be ${filename.typeName}, but got ${value.filename}.", failureReason = FailureReason.PartNameMisMatch, cause = filename.matches(StringValue(value.filename), resolver) as Failure)
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

    override fun nonOptional(): MultiPartFormDataPattern {
        return copy(name = withoutOptionality(name))
    }
}
