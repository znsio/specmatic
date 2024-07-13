package `in`.specmatic.stub

import `in`.specmatic.core.HttpRequest

interface RequestHandler {
    val name: String
    fun handleRequest(httpRequest: HttpRequest): HttpStubResponse?
}