package io.specmatic.stub.stateful

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.specmatic.core.Feature
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpRequestPattern
import io.specmatic.core.HttpResponse
import io.specmatic.core.Resolver
import io.specmatic.core.Scenario
import io.specmatic.core.SpecmaticConfig
import io.specmatic.core.loadSpecmaticConfig
import io.specmatic.core.log.HttpLogMessage
import io.specmatic.core.log.logger
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.pattern.IgnoreUnexpectedKeys
import io.specmatic.core.pattern.JSONObjectPattern
import io.specmatic.core.pattern.Pattern
import io.specmatic.core.pattern.PossibleJsonObjectPatternContainer
import io.specmatic.core.pattern.StringPattern
import io.specmatic.core.pattern.resolvedHop
import io.specmatic.core.pattern.withoutOptionality
import io.specmatic.core.route.modules.HealthCheckModule.Companion.configureHealthCheckModule
import io.specmatic.core.route.modules.HealthCheckModule.Companion.isHealthCheckRequest
import io.specmatic.core.utilities.exceptionCauseMessage
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value
import io.specmatic.mock.ScenarioStub
import io.specmatic.stub.ContractAndRequestsMismatch
import io.specmatic.stub.ContractStub
import io.specmatic.stub.CouldNotParseRequest
import io.specmatic.stub.FoundStubbedResponse
import io.specmatic.stub.HttpStubResponse
import io.specmatic.stub.NotStubbed
import io.specmatic.stub.ResponseDetails
import io.specmatic.stub.StubbedResponseResult
import io.specmatic.stub.badRequest
import io.specmatic.stub.endPointFromHostAndPort
import io.specmatic.stub.fakeHttpResponse
import io.specmatic.stub.generateHttpResponseFrom
import io.specmatic.stub.internalServerError
import io.specmatic.stub.ktorHttpRequestToHttpRequest
import io.specmatic.stub.respondToKtorHttpResponse
import io.specmatic.test.HttpClient
import java.io.File

class StatefulHttpStub(
    host: String = "127.0.0.1",
    port: Int = 9000,
    private val features: List<Feature>,
    private val specmaticConfigPath: String? = null,
    private val scenarioStubs: List<ScenarioStub> = emptyList(),
    private val timeoutMillis: Long = 2000,
): ContractStub {

    private val environment = applicationEngineEnvironment {
        module {
            install(DoubleReceive)

            install(CORS) {
                allowMethod(HttpMethod.Options)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Delete)
                allowMethod(HttpMethod.Patch)

                allowHeaders { true }

                allowCredentials = true
                allowNonSimpleContentTypes = true

                anyHost()
            }

            routing {
                staticResources("/", "swagger-ui")

                get("/openapi.yaml") {
                    call.respondFile(File(features.first().path))
                }
            }

            intercept(ApplicationCallPipeline.Call) {
                val httpLogMessage = HttpLogMessage()

                try {
                    val rawHttpRequest = ktorHttpRequestToHttpRequest(call)
                    httpLogMessage.addRequest(rawHttpRequest)

                    if(rawHttpRequest.isHealthCheckRequest()) return@intercept

                    val httpStubResponse: HttpStubResponse = cachedHttpResponse(rawHttpRequest).response

                    respondToKtorHttpResponse(
                        call,
                        httpStubResponse.response,
                        httpStubResponse.delayInMilliSeconds,
                        specmaticConfig
                    )
                    httpLogMessage.addResponse(httpStubResponse)
                } catch (e: ContractException) {
                    val response = badRequest(e.report())
                    httpLogMessage.addResponse(response)
                    respondToKtorHttpResponse(call, response)
                } catch (e: CouldNotParseRequest) {
                    val response = badRequest("Could not parse request")
                    httpLogMessage.addResponse(response)

                    respondToKtorHttpResponse(call, response)
                } catch (e: Throwable) {
                    val response = internalServerError(exceptionCauseMessage(e) + "\n\n" + e.stackTraceToString())
                    httpLogMessage.addResponse(response)

                    respondToKtorHttpResponse(call, response)
                }

                logger.log(httpLogMessage)
            }

            configureHealthCheckModule()

            connector {
                this.host = host
                this.port = port
            }
        }

    }

    private val server: ApplicationEngine = embeddedServer(Netty, environment, configure = {
        this.callGroupSize = 20
    })

    init {
        if(features.isEmpty()) {
            throw IllegalArgumentException("The stateful stub requires at least one API specification to function.")
        }
        server.start()
    }

    override val client = HttpClient(endPointFromHostAndPort(host, port, null))

    override fun setExpectation(json: String) {
        return
    }

    override fun close() {
        server.stop(gracePeriodMillis = timeoutMillis, timeoutMillis = timeoutMillis)
    }

    private val specmaticConfig = loadSpecmaticConfig()
    private val stubCache = stubCacheWithExampleData()

    private fun cachedHttpResponse(
        httpRequest: HttpRequest,
    ): StubbedResponseResult {
        if (features.isEmpty())
            return NotStubbed(HttpStubResponse(HttpResponse(400, "No valid API specifications loaded")))

        val responses: Map<Int, ResponseDetails> = responseDetailsFrom(features, httpRequest)
        val fakeResponse = responses.responseWithStatusCodeStartingWith("2")
            ?: return badRequestOrFakeResponse(responses, httpRequest)

        val updatedResponse = cachedResponse(
            fakeResponse,
            httpRequest,
            specmaticConfig.stub.includeMandatoryAndRequestedKeysInResponse,
            responses.responseWithStatusCodeStartingWith("404")?.successResponse?.responseBodyPattern
        ) ?: generateHttpResponseFrom(fakeResponse, httpRequest)

        return FoundStubbedResponse(
            HttpStubResponse(
                updatedResponse,
                contractPath = fakeResponse.feature.path,
                feature = fakeResponse.feature,
                scenario = fakeResponse.successResponse?.scenario
            )
        )
    }

    private fun badRequestOrFakeResponse(
        responses: Map<Int, ResponseDetails>,
        httpRequest: HttpRequest
    ): StubbedResponseResult {
        val badRequestScenario = features.scenarioWith(
            httpRequest.method,
            httpRequest.path?.split("/")?.getOrNull(1),
            400
        )
        val badRequestResponseDetails = responses.responseWithStatusCodeStartingWith("400")
            ?: return fakeHttpResponse(features, httpRequest, specmaticConfig)

        val response = generate4xxResponseWithMessage(
            badRequestScenario?.resolvedResponseBodyPattern(),
            badRequestScenario,
            badRequestResponseDetails.results.distinctReport(),
            400
        )
        return FoundStubbedResponse(
            HttpStubResponse(
                response,
                contractPath = badRequestResponseDetails.feature.path,
                feature = badRequestResponseDetails.feature,
                scenario = badRequestResponseDetails.successResponse?.scenario
            )
        )
    }

    private fun List<Feature>.scenarioWith(method: String?, path: String?, statusCode: Int): Scenario? {
        return this.flatMap { it.scenarios }.firstOrNull { scenario ->
            scenario.method == method
                    && scenario.path.split("/").getOrNull(1) == path
                    && scenario.status == statusCode
        }
    }

    private fun Map<Int, ResponseDetails>.responseWithStatusCodeStartingWith(value: String): ResponseDetails? {
        val responseDetailMatchingPredicate: (Int, ResponseDetails) -> Boolean = { statusCode, responseDetails ->
            statusCode.toString().startsWith(value) && responseDetails.successResponse != null
        }

        val non202Response = this.entries.filter {
            it.key != 202
        }.firstOrNull {
            responseDetailMatchingPredicate(it.key, it.value)
        }?.value
        if(non202Response != null) return non202Response

        return this.entries.firstOrNull {
            responseDetailMatchingPredicate(it.key, it.value)
        }?.value
    }

    private fun cachedResponse(
        fakeResponse: ResponseDetails,
        httpRequest: HttpRequest,
        includeMandatoryAndRequestedKeysInResponse: Boolean?,
        notFoundResponseBodyPattern: Pattern?
    ): HttpResponse? {
        val scenario = fakeResponse.successResponse?.scenario

        val generatedResponse = generateHttpResponseFrom(fakeResponse, httpRequest)
        val method = scenario?.method
        val pathSegments = httpRequest.pathSegments()

        if(isUnsupportedResponseBodyForCaching(generatedResponse, method, pathSegments)) return null

        val (resourcePath, resourceId) = resourcePathAndIdFrom(httpRequest)
        val resourceIdKey = resourceIdKeyFrom(scenario?.httpRequestPattern)
        val attributeSelectionKeys: Set<String> =
            scenario?.getFieldsToBeMadeMandatoryBasedOnAttributeSelection(httpRequest.queryParams).orEmpty()

        val notFoundResponse = generate4xxResponseWithMessage(
            notFoundResponseBodyPattern,
            scenario,
            message = "Resource with resourceId '$resourceId' not found",
            statusCode = 404
        )
        val cachedResponseWithId = stubCache.findResponseFor(resourcePath, resourceIdKey, resourceId)?.responseBody
        if(pathSegments.size > 1 && cachedResponseWithId == null) return notFoundResponse

        if (method == "POST") {
            val responseBody = generatePostResponse(generatedResponse, httpRequest, scenario.resolver) ?: return null

            val finalResponseBody = if (attributeSelectionKeys.isEmpty()) {
                responseBody.includeMandatoryAndRequestedKeys(
                    fakeResponse,
                    httpRequest,
                    includeMandatoryAndRequestedKeysInResponse
                )
            } else responseBody

            stubCache.addResponse(resourcePath, finalResponseBody)
            return generatedResponse.withUpdated(finalResponseBody, attributeSelectionKeys)
        }

        if(method == "PATCH" && pathSegments.size > 1) {
            val responseBody =
                generatePatchResponse(
                    httpRequest,
                    resourcePath,
                    resourceIdKey,
                    resourceId,
                    fakeResponse
                ) ?: return null

            stubCache.updateResponse(resourcePath, responseBody, resourceIdKey, resourceId)
            return generatedResponse.withUpdated(responseBody, attributeSelectionKeys)
        }

        if(method == "GET" && pathSegments.size == 1) {
            val responseBody = stubCache.findAllResponsesFor(
                resourcePath,
                attributeSelectionKeys,
                httpRequest.queryParams.asMap()
            )
            return generatedResponse.withUpdated(responseBody, attributeSelectionKeys)
        }

        if(method == "GET" && pathSegments.size > 1) {
            if(cachedResponseWithId == null) return notFoundResponse
            return generatedResponse.withUpdated(cachedResponseWithId, attributeSelectionKeys)
        }

        if(method == "DELETE" && pathSegments.size > 1) {
            stubCache.deleteResponse(resourcePath, resourceIdKey, resourceId)
            return generatedResponse
        }

        return null
    }


    private fun generate4xxResponseWithMessage(
        responseBodyPattern: Pattern?,
        scenario: Scenario?,
        message: String,
        statusCode: Int
    ): HttpResponse {
        if(statusCode.toString().startsWith("4").not()) {
            throw IllegalArgumentException("The statusCode should be of 4xx type")
        }

        if (responseBodyPattern == null || responseBodyPattern !is JSONObjectPattern) {
            return HttpResponse(statusCode, message)
        }
        val messageKey =
            responseBodyPattern.pattern.entries.firstOrNull { it.value is StringPattern }?.key
        if (messageKey == null || scenario?.resolver == null) {
            return HttpResponse(statusCode, message)
        }

        val jsonObjectWithNotFoundMessage = responseBodyPattern.generate(
            scenario.resolver
        ).jsonObject.plus(
            mapOf(withoutOptionality(messageKey) to StringValue(message))
        )
        return HttpResponse(statusCode, JSONObjectValue(jsonObject = jsonObjectWithNotFoundMessage))
    }

    private fun resourcePathAndIdFrom(httpRequest: HttpRequest): Pair<String, String> {
        val pathSegments = httpRequest.pathSegments()
        val resourcePath = "/${pathSegments.first()}"
        val resourceId = pathSegments.last()
        return Pair(resourcePath, resourceId)
    }

    private fun HttpRequest.pathSegments(): List<String> {
        return this.path?.split("/")?.filter { it.isNotBlank() }.orEmpty()
    }

    private fun isUnsupportedResponseBodyForCaching(
        generatedResponse: HttpResponse,
        method: String?,
        pathSegments: List<String>
    ): Boolean {
        return (generatedResponse.body is JSONObjectValue ||
                (method == "DELETE" && pathSegments.size > 1) ||
                (method == "GET" &&
                        generatedResponse.body is JSONArrayValue &&
                        generatedResponse.body.list.firstOrNull() is JSONObjectValue)).not()
    }

    private fun generatePostResponse(
        generatedResponse: HttpResponse,
        httpRequest: HttpRequest,
        resolver: Resolver
    ): JSONObjectValue? {
        val responseBody = generatedResponse.body
        if (responseBody !is JSONObjectValue || httpRequest.body !is JSONObjectValue)
            return null

        val patchedResponseBodyMap = patchValuesFromRequestIntoResponse(httpRequest.body, responseBody)

        return responseBody.copy(
            jsonObject = responseBodyMapWithUniqueId(httpRequest, patchedResponseBodyMap, resolver)
        )
    }

    private fun generatePatchResponse(
        httpRequest: HttpRequest,
        resourcePath: String,
        resourceIdKey: String,
        resourceId: String,
        fakeResponse: ResponseDetails
    ): JSONObjectValue? {
        if (httpRequest.body !is JSONObjectValue) return null

        val responseBodyPattern = responseBodyPatternFrom(fakeResponse) ?: return null
        val resolver = fakeResponse.successResponse?.resolver ?: return null

        val cachedResponse = stubCache.findResponseFor(resourcePath, resourceIdKey, resourceId)
        val responseBody = cachedResponse?.responseBody ?: return null

        return responseBody.copy(
            jsonObject = patchAndAppendValuesFromRequestIntoResponse(
                httpRequest.body,
                responseBody,
                responseBodyPattern,
                resolver
            )
        )
    }

    private fun JSONObjectValue.includeMandatoryAndRequestedKeys(
        fakeResponse: ResponseDetails,
        httpRequest: HttpRequest,
        includeMandatoryAndRequestedKeysInResponse: Boolean?
    ): JSONObjectValue {
        val responseBodyPattern = fakeResponse.successResponse?.responseBodyPattern ?: return this
        val resolver = fakeResponse.successResponse.resolver ?: return this

        val resolvedResponseBodyPattern = responseBodyPatternFrom(fakeResponse) ?: return this

        if (includeMandatoryAndRequestedKeysInResponse == true && httpRequest.body is JSONObjectValue) {
            return this.copy(
                jsonObject = patchAndAppendValuesFromRequestIntoResponse(
                    httpRequest.body,
                    responseBodyPattern.eliminateOptionalKey(this, resolver) as JSONObjectValue,
                    resolvedResponseBodyPattern,
                    resolver
                )
            )
        }

        return this
    }

    private fun patchValuesFromRequestIntoResponse(
        requestBody: JSONObjectValue,
        responseBody: JSONObjectValue,
        nonPatchableKeys: Set<String> = emptySet()
    ): Map<String, Value> {
        return responseBody.jsonObject.mapValues { (key, value) ->
            if(key in nonPatchableKeys) return@mapValues value

            val patchValueFromRequest = requestBody.jsonObject.entries.firstOrNull {
                it.key == key
            }?.value ?: return@mapValues value

            if(patchValueFromRequest::class.java == value::class.java) return@mapValues patchValueFromRequest
            value
        }
    }

    private fun patchAndAppendValuesFromRequestIntoResponse(
        requestBody: JSONObjectValue,
        responseBody: JSONObjectValue,
        responseBodyPattern: JSONObjectPattern,
        resolver: Resolver
    ): Map<String, Value> {
        val acceptedKeysInResponseBody = responseBodyPattern.keysInNonOptionalFormat()

        val entriesFromRequestMissingInTheResponse = requestBody.jsonObject.filter {
            it.key in acceptedKeysInResponseBody
                    && responseBodyPattern.patternForKey(it.key)?.matches(it.value, resolver)?.isSuccess() == true
                    && responseBody.jsonObject.containsKey(it.key).not()
        }.map {
            it.key to it.value
        }.toMap()

        return patchValuesFromRequestIntoResponse(
            requestBody,
            responseBody,
            specmaticConfig.virtualService.nonPatchableKeys
        ).plus(entriesFromRequestMissingInTheResponse)
    }

    private fun responseBodyMapWithUniqueId(
        httpRequest: HttpRequest,
        responseBodyMap: Map<String, Value>,
        resolver: Resolver,
    ): Map<String, Value> {
        val idKey = "id"
        val maxAttempts = 100_000

        val initialIdValue = responseBodyMap[idKey] ?: return responseBodyMap

        val (resourcePath, _) = resourcePathAndIdFrom(httpRequest)
        var currentIdValue = initialIdValue

        repeat(maxAttempts) {
            if (stubCache.findResponseFor(resourcePath, idKey, currentIdValue.toStringLiteral()) == null) {
                return responseBodyMap + mapOf(idKey to currentIdValue)
            }
            currentIdValue = currentIdValue.deepPattern().generate(resolver)
        }

        return responseBodyMap
    }

    private fun responseBodyPatternFrom(fakeResponse: ResponseDetails): JSONObjectPattern? {
        val responseBodyPattern = fakeResponse.successResponse?.responseBodyPattern ?: return null
        val resolver = fakeResponse.successResponse.resolver ?: return null
        val resolvedPattern = resolver.withCyclePrevention(responseBodyPattern) {
            resolvedHop(responseBodyPattern, it)
        }
        if(resolvedPattern !is PossibleJsonObjectPatternContainer) {
            return null
        }

        return resolvedPattern.jsonObjectPattern(resolver)
    }

    private fun resourceIdKeyFrom(httpRequestPattern: HttpRequestPattern?): String {
        return httpRequestPattern?.getPathSegmentPatterns()?.last()?.key.orEmpty()
    }

    private fun HttpResponse.withUpdated(body: Value, attributeSelectionKeys: Set<String>): HttpResponse {
        if(body !is JSONObjectValue) return this.copy(body = body)
        return this.copy(body = body.removeKeysNotPresentIn(attributeSelectionKeys))
    }

    private fun loadSpecmaticConfig(): SpecmaticConfig {
        return if(specmaticConfigPath != null && File(specmaticConfigPath).exists())
            loadSpecmaticConfig(specmaticConfigPath)
        else
            SpecmaticConfig()
    }

    private fun stubCacheWithExampleData(): StubCache {
        val stubCache = StubCache()

        scenarioStubs.forEach {
            val httpRequest = it.request
            if (httpRequest.method !in setOf("GET", "POST")) return@forEach
            if (isUnsupportedResponseBodyForCaching(
                    generatedResponse = it.response,
                    method = httpRequest.method,
                    pathSegments = httpRequest.pathSegments()
                )
            ) return@forEach

            val (resourcePath, _) = resourcePathAndIdFrom(httpRequest)
            val responseBody = it.response.body
            if (httpRequest.method == "GET" && httpRequest.pathSegments().size == 1) {
                val responseBodies = (it.response.body as JSONArrayValue).list.filterIsInstance<JSONObjectValue>()
                responseBodies.forEach { body ->
                    stubCache.addResponse(resourcePath, body)
                }
            } else {
                if (responseBody !is JSONObjectValue) return@forEach
                if(httpRequest.method == "POST" && httpRequest.body !is JSONObjectValue) return@forEach

                stubCache.addResponse(resourcePath, responseBody)
            }
        }

        return stubCache
    }

    private fun responseDetailsFrom(features: List<Feature>, httpRequest: HttpRequest): Map<Int, ResponseDetails> {
        return features.asSequence().map { feature ->
            feature.stubResponseMap(
                httpRequest,
                ContractAndRequestsMismatch,
                IgnoreUnexpectedKeys
            ).map { (statusCode, responseResultPair) ->
                statusCode to ResponseDetails(feature, responseResultPair.first, responseResultPair.second)
            }.toMap()
        }.flatMap { map -> map.entries.map { it.toPair() } }.toMap()
    }
}