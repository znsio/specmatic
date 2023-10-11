package `in`.specmatic.conversions

import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.attempt
import `in`.specmatic.core.pattern.parsedJSONObject
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.Value
import java.io.File

class ExampleFromFile(val json: JSONObjectValue, val file: File) {
    constructor(file: File) : this(parsedJSONObject(file.readText()), file)

    val expectationFilePath: String = file.canonicalPath

    val responseStatus: Int = attempt("Error reading status in file ${file.parentFile.canonicalPath}") {
        json.findFirstChildByPath("http-response.status")?.toStringLiteral()?.toInt()
    } ?: throw ContractException("Response status code was not found.")

    val requestMethod: String = attempt("Error reading method in file ${file.parentFile.canonicalPath}") {
        json.findFirstChildByPath("http-request.method")?.toStringLiteral()
    } ?: throw ContractException("Request method was not found.")

    val requestPath: String = attempt("Error reading path in file ${file.parentFile.canonicalPath}") {
        json.findFirstChildByPath("http-request.path")?.toStringLiteral()
    } ?: throw ContractException("Request path was not found.")

    val testName: String = attempt("Error reading expectation name in file ${file.parentFile.canonicalPath}") {
        json.findFirstChildByPath("name")?.toStringLiteral()
    } ?: throw ContractException("Name was not found.")

    val queryParams: Map<String, String> =
        attempt("Error reading query params in file ${file.parentFile.canonicalPath}") {
            (json.findFirstChildByPath("http-request.query") as JSONObjectValue?)?.jsonObject?.mapValues { (_, value) ->
                value.toStringLiteral()
            } ?: emptyMap()
        }

    val headers: Map<String, String> = attempt("Error reading headers in file ${file.parentFile.canonicalPath}") {
        (json.findFirstChildByPath("http-request.headers") as JSONObjectValue?)?.jsonObject?.mapValues { (_, value) ->
            value.toStringLiteral()
        } ?: emptyMap()
    }

    val pathParams: Map<String, String> =
        attempt("Error reading path params in file ${file.parentFile.canonicalPath}") {
            (json.findFirstChildByPath("http-request.path-params") as JSONObjectValue?)?.jsonObject?.mapValues { (_, value) ->
                value.toStringLiteral()
            } ?: emptyMap()
        }

    val requestBody: Value? = attempt("Error reading request body in file ${file.parentFile.canonicalPath}") {
        json.findFirstChildByPath("http-request.body")
    }
}
