package io.specmatic.stub.stateful

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.CORS
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.conversions.OpenApiSpecification.Companion.applyOverlay
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
import io.specmatic.core.value.mergeWith
import io.specmatic.core.Result
import io.specmatic.mock.ScenarioStub
import io.specmatic.stub.*
import io.specmatic.stub.stateful.StubCache.Companion.idValueFor
import io.specmatic.test.ExampleProcessor
import io.specmatic.test.LegacyHttpClient
import java.io.File

const val DEFAULT_ACCEPTED_RESPONSE_QUERY_ENDPOINT = "/monitor"
const val ACCEPTED_STATUS_CODE = 202
const val NOT_FOUND_STATUS_CODE = 404

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
                    val openApiFilePath = features.first().path
                    val overlayContent = OpenApiSpecification.getImplicitOverlayContent(openApiFilePath)
                    val openApiSpec = File(openApiFilePath).readText().applyOverlay(overlayContent)
                    call.respond(openApiSpec)
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

    override val client = LegacyHttpClient(endPointFromHostAndPort(host, port, null))

    override fun setExpectation(json: String) {
        return
    }

    override fun close() {
        server.stop(gracePeriodMillis = timeoutMillis, timeoutMillis = timeoutMillis)
    }

    private val specmaticConfig = loadSpecmaticConfig()
    private val stubCache = stubCacheWithExampleSeedData()

    private fun cachedHttpResponse(
        httpRequest: HttpRequest,
    ): StubbedResponseResult {
        return responseDetailsFrom(features, httpRequest).let { responses ->
            val fakeResponse = responses.responseWithStatusCodeStartingWith(
                "2"
            ) ?: return@let badRequestOrFakeResponse(responses, httpRequest)

            val fakeAcceptedResponse = if (httpRequest.isRequestExpectingAcceptedResponse())
                responses.responseWithStatusCodeStartingWith(ACCEPTED_STATUS_CODE.toString())
            else null

            val notFoundResponseBodyPattern = responses.responseWithStatusCodeStartingWith(
                NOT_FOUND_STATUS_CODE.toString()
            )?.successResponse?.responseBodyPattern

            return@let FoundStubbedResponse(
                HttpStubResponse(
                    response = cachedResponse(
                        httpRequest,
                        fakeAcceptedResponse,
                        fakeResponse,
                        notFoundResponseBodyPattern
                    ),
                    contractPath = fakeResponse.feature.path,
                    feature = fakeResponse.feature,
                    scenario = fakeResponse.successResponse?.scenario
                )
            )
        }
    }

    private fun cachedResponse(
        httpRequest: HttpRequest,
        fakeAcceptedResponse: ResponseDetails?,
        fakeResponse: ResponseDetails,
        notFoundResponseBodyPattern: Pattern?
    ): HttpResponse {
        val generatedResponse = generateHttpResponseFrom(fakeResponse, httpRequest, true)
        val scenario = fakeResponse.successResponse?.scenario

        if (
            scenario == null ||
            isUnsupportedResponseBodyForCaching(generatedResponse, scenario.method, httpRequest.pathSegments())
        ) return generatedResponse

        if (
            httpRequest.pathSegments().size > 1
            && cachedResponseWithIdFor(httpRequest, scenario) == null
        ) {
            return generate4xxResponseWithMessage(
                notFoundResponseBodyPattern,
                scenario,
                message = "Resource with resourceId '${resourcePathAndIdFrom(httpRequest).second}' not found",
                statusCode = 404
            )
        }

        return when {
            scenario.method == "POST" -> cachePostResponseAndReturn(
                httpRequest = httpRequest,
                generatedResponse = generatedResponse,
                scenario = scenario,
                fakeResponse = fakeResponse,
                fakeAcceptedResponse = fakeAcceptedResponse,
                attributeSelectionKeys = scenario.fieldsToBeMadeMandatoryBasedOnAttributeSelection(httpRequest.queryParams),
            )

            scenario.method == "PATCH" && httpRequest.pathSegments().size > 1 -> cachePatchResponseAndReturn(
                httpRequest = httpRequest,
                generatedResponse = generatedResponse,
                scenario = scenario,
                fakeResponse = fakeResponse,
                fakeAcceptedResponse = fakeAcceptedResponse,
                resourceIdKey = resourceIdKeyFrom(scenario.httpRequestPattern),
                attributeSelectionKeys = scenario.fieldsToBeMadeMandatoryBasedOnAttributeSelection(httpRequest.queryParams)
            )

            scenario.method == "GET" && httpRequest.pathSegments().size == 1 -> cacheGetAllResponseAndReturn(
                httpRequest = httpRequest,
                generatedResponse = generatedResponse,
                attributeSelectionKeys = scenario.fieldsToBeMadeMandatoryBasedOnAttributeSelection(httpRequest.queryParams),
            )

            scenario.method == "GET" && httpRequest.pathSegments().size > 1 -> cacheGetResponseAndReturn(
                httpRequest = httpRequest,
                generatedResponse = generatedResponse,
                scenario = scenario,
                attributeSelectionKeys = scenario.fieldsToBeMadeMandatoryBasedOnAttributeSelection(httpRequest.queryParams)
            )

            scenario.method == "DELETE" && httpRequest.pathSegments().size > 1 -> cacheDeleteResponseAndReturn(
                httpRequest = httpRequest,
                generatedResponse = generatedResponse,
                resourceIdKey = resourceIdKeyFrom(scenario.httpRequestPattern),
            )

            else -> null
        } ?: generatedResponse
    }

    private fun cacheDeleteResponseAndReturn(
        httpRequest: HttpRequest,
        resourceIdKey: String,
        generatedResponse: HttpResponse
    ): HttpResponse {
        val (resourcePath, resourceId) = resourcePathAndIdFrom(httpRequest)
        stubCache.deleteResponse(resourcePath, resourceIdKey, resourceId)
        return generatedResponse
    }

    private fun cacheGetResponseAndReturn(
        httpRequest: HttpRequest,
        generatedResponse: HttpResponse,
        attributeSelectionKeys: Set<String>,
        scenario: Scenario
    ): HttpResponse? {
        val cachedResponseWithId = cachedResponseWithIdFor(httpRequest, scenario) ?: return null

        val isAcceptedResponseQueryRequest = scenario.path.startsWith(DEFAULT_ACCEPTED_RESPONSE_QUERY_ENDPOINT)

        if(isAcceptedResponseQueryRequest) return responseForAcceptedResponseQueryRequest(
            scenario,
            cachedResponseWithId
        ) ?: generatedResponse

        return generatedResponse.withUpdated(cachedResponseWithId, attributeSelectionKeys)
    }

    private fun cacheGetAllResponseAndReturn(
        attributeSelectionKeys: Set<String>,
        httpRequest: HttpRequest,
        generatedResponse: HttpResponse
    ): HttpResponse {
        val responseBody = stubCache.findAllResponsesFor(
            resourcePathAndIdFrom(httpRequest).first,
            attributeSelectionKeys,
            httpRequest.queryParams.asMap()
        )
        return generatedResponse.withUpdated(responseBody, attributeSelectionKeys)
    }

    private fun cachePatchResponseAndReturn(
        httpRequest: HttpRequest,
        resourceIdKey: String,
        fakeResponse: ResponseDetails,
        generatedResponse: HttpResponse,
        attributeSelectionKeys: Set<String>,
        fakeAcceptedResponse: ResponseDetails?,
        scenario: Scenario
    ): HttpResponse? {
        val (resourcePath, resourceId) = resourcePathAndIdFrom(httpRequest)
        val responseBody =
            generatePatchResponse(
                httpRequest,
                resourcePath,
                resourceIdKey,
                resourceId,
                fakeResponse
            ) ?: return null

        stubCache.updateResponse(resourcePath, responseBody, resourceIdKey, resourceId)

        if (httpRequest.isRequestExpectingAcceptedResponse()) {
            return updateCacheAndReturnAcceptedResponse(
                fakeAcceptedResponse,
                responseBody,
                httpRequest,
                generatedResponse,
                scenario.resolver
            )
        }
        return generatedResponse.withUpdated(responseBody, attributeSelectionKeys)
    }

    private fun cachePostResponseAndReturn(
        generatedResponse: HttpResponse,
        httpRequest: HttpRequest,
        scenario: Scenario,
        attributeSelectionKeys: Set<String>,
        fakeResponse: ResponseDetails,
        fakeAcceptedResponse: ResponseDetails?
    ): HttpResponse? {
        val responseBody = generatePostResponse(generatedResponse, httpRequest, scenario.resolver) ?: return null

        val finalResponseBody = if (attributeSelectionKeys.isEmpty())
            responseBody.includeMandatoryAndRequestedKeys(fakeResponse, httpRequest)
        else responseBody

        stubCache.addResponse(
            path = resourcePathAndIdFrom(httpRequest).first,
            responseBody = finalResponseBody,
            idKey = DEFAULT_CACHE_RESPONSE_ID_KEY,
            idValue = idValueFor(DEFAULT_CACHE_RESPONSE_ID_KEY, finalResponseBody)
        )

        if (httpRequest.isRequestExpectingAcceptedResponse()) {
            return updateCacheAndReturnAcceptedResponse(
                fakeAcceptedResponse,
                finalResponseBody,
                httpRequest,
                generatedResponse,
                scenario.resolver
            )
        }

        return generatedResponse.withUpdated(finalResponseBody, attributeSelectionKeys)
    }

    private fun responseForAcceptedResponseQueryRequest(
        scenario: Scenario,
        cachedResponseWithId: JSONObjectValue
    ): HttpResponse? {
        val matchingStub = scenarioStubs.firstOrNull {
            scenario.matches(httpRequest = it.request, resolver = scenario.resolver)  is Result.Success
        } ?: return null

        ExampleProcessor.store(
            cachedResponseWithId,
            JSONObjectValue(jsonObject = mapOf("${'$'}store" to StringValue("replace")))
        )
        return ExampleProcessor.resolve(matchingStub.response, ExampleProcessor::defaultIfNotExits)
    }

    private fun updateCacheAndReturnAcceptedResponse(
        fakeAcceptedResponse: ResponseDetails?,
        finalResponseBody: JSONObjectValue,
        httpRequest: HttpRequest,
        httpResponse: HttpResponse,
        resolver: Resolver
    ): HttpResponse {
        if(fakeAcceptedResponse == null) throw acceptedResponseSchemaNotFoundException()
        val responseIdValue = idValueFor(DEFAULT_CACHE_RESPONSE_ID_KEY, finalResponseBody)

        val acceptedResponseIdValue = stubCache.addAcceptedResponse(
            path = DEFAULT_ACCEPTED_RESPONSE_QUERY_ENDPOINT,
            finalResponseBody = finalResponseBody,
            httpResponse = httpResponse,
            httpRequest = httpRequest,
            resolver = resolver
        )
        val generatedResponse = generateHttpResponseFrom(fakeAcceptedResponse, httpRequest, true)

        return generatedResponse.copy(
            headers = generatedResponse.headers.mapValues {
                if (it.key.contains("Specmatic")) it.value
                else createAcceptedResponseQueryLink(
                    resourcePathAndIdFrom(httpRequest).first,
                    responseIdValue,
                    acceptedResponseIdValue.toStringLiteral()
                )
            }
        )
    }

    private fun createAcceptedResponseQueryLink(
        originalResourcePath: String,
        responseIdValue: String,
        acceptedResponseIdValue: String
    ): String {
        return "<$DEFAULT_ACCEPTED_RESPONSE_QUERY_ENDPOINT/$acceptedResponseIdValue>;rel=related;title=${DEFAULT_ACCEPTED_RESPONSE_QUERY_ENDPOINT.substringAfterLast("/")},<$originalResourcePath/$responseIdValue>;rel=self,<$originalResourcePath/$responseIdValue>;rel=canonical"
    }

    private fun acceptedResponseSchemaNotFoundException(): ContractException {
        return ContractException("No 202 (Accepted) response schema found for this request as expected by the request header $SPECMATIC_RESPONSE_CODE_HEADER.")
    }

    private fun cachedResponseWithIdFor(httpRequest: HttpRequest, scenario: Scenario): JSONObjectValue? {
        val (resourcePath, resourceId) = resourcePathAndIdFrom(httpRequest)
        val resourceIdKey = resourceIdKeyFrom(scenario.httpRequestPattern)
        return stubCache.findResponseFor(resourcePath, resourceIdKey, resourceId)?.responseBody
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

    private fun Map<Int, ResponseDetails>.responseWithStatusCodeStartingWith(
        value: String
    ): ResponseDetails? {
        val isValueMatchingStatusCodeFrom: (Int, ResponseDetails) -> Boolean = { statusCode, responseDetails ->
            statusCode.toString().startsWith(value) && responseDetails.successResponse != null
        }

        val response = this.entries.firstOrNull { (statusCode, responseDetails) ->
            isValueMatchingStatusCodeFrom(statusCode, responseDetails)
        }?.value

        if (value == ACCEPTED_STATUS_CODE.toString()) {
            return response ?: this.responseWithStatusCodeStartingWith("2")
        }

        return this.entries.filter { (statusCode, _) ->
            statusCode != ACCEPTED_STATUS_CODE
        }.firstOrNull { (statusCode, responseDetails) ->
            isValueMatchingStatusCodeFrom(statusCode, responseDetails)
        }?.value ?: response
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
        val warningMessage = "WARNING: The response is in string format since no schema found in the specification for $statusCode response"

        val resolver = scenario?.resolver
        if (
            responseBodyPattern == null ||
            responseBodyPattern !is PossibleJsonObjectPatternContainer ||
            resolver == null
        ) {
            return HttpResponse(statusCode, "$message${System.lineSeparator()}$warningMessage")
        }
        val responseBodyJsonObjectPattern =
            (responseBodyPattern as PossibleJsonObjectPatternContainer).jsonObjectPattern(resolver)

        val messageKey = messageKeyFor4xxResponseMessage(responseBodyJsonObjectPattern)
        if (messageKey == null || responseBodyJsonObjectPattern == null) {
            return HttpResponse(statusCode, "$message${System.lineSeparator()}$warningMessage")
        }

        val jsonObjectWithNotFoundMessage = responseBodyJsonObjectPattern.generate(
            resolver
        ).jsonObject.plus(
            mapOf(withoutOptionality(messageKey) to StringValue(message))
        )
        return HttpResponse(statusCode, JSONObjectValue(jsonObject = jsonObjectWithNotFoundMessage))
    }

    private fun messageKeyFor4xxResponseMessage(
        responseBodyJsonObjectPattern: JSONObjectPattern?
    ): String? {
        val messageKeyWithStringType = responseBodyJsonObjectPattern?.pattern?.entries?.firstOrNull {
            it.value is StringPattern && withoutOptionality(it.key) in setOf("message", "msg")
        }?.key

        if (messageKeyWithStringType != null) return messageKeyWithStringType

        return responseBodyJsonObjectPattern?.pattern?.entries?.firstOrNull {
            it.value is StringPattern
        }?.key
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
        httpRequest: HttpRequest
    ): JSONObjectValue {
        val responseBodyPattern = fakeResponse.successResponse?.responseBodyPattern ?: return this
        val resolver = fakeResponse.successResponse.resolver ?: return this
        val resolvedResponseBodyPattern = responseBodyPatternFrom(fakeResponse) ?: return this

        if (specmaticConfig.getStubIncludeMandatoryAndRequestedKeysInResponse() && httpRequest.body is JSONObjectValue) {
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
            specmaticConfig.getVirtualServiceNonPatchableKeys()
        ).plus(entriesFromRequestMissingInTheResponse)
    }

    private fun responseBodyMapWithUniqueId(
        httpRequest: HttpRequest,
        responseBodyMap: Map<String, Value>,
        resolver: Resolver,
    ): Map<String, Value> {
        val idKey = DEFAULT_CACHE_RESPONSE_ID_KEY
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

    private fun stubCacheWithExampleSeedData(): StubCache {
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
                if (httpRequest.queryParams.asMap().containsKey(specmaticConfig.attributeSelectionQueryParamKey())) {
                    return@forEach
                }

                val responseBodies = when (responseBody) {
                    is JSONArrayValue -> responseBody.list.filterIsInstance<JSONObjectValue>()
                    is JSONObjectValue -> responseBody.jsonObject.entries
                        .filter { (_, value) -> value is JSONArrayValue }
                        .map { (_, value) ->
                            (value as JSONArrayValue).list.filterIsInstance<JSONObjectValue>()
                        }
                        .flatten()
                    else -> emptyList()
                }

                responseBodies.forEach { body ->
                    stubCache.addResponse(
                        path = resourcePath,
                        responseBody = body,
                        idKey = DEFAULT_CACHE_RESPONSE_ID_KEY,
                        idValue = idValueFor(DEFAULT_CACHE_RESPONSE_ID_KEY, body)
                    )
                }
                return@forEach
            }

            if (responseBody !is JSONObjectValue) return@forEach
            if(httpRequest.method == "POST" && httpRequest.body !is JSONObjectValue) return@forEach

            val requestBody = httpRequest.body
            if(requestBody is JSONObjectValue) {
                stubCache.addResponse(
                    path = resourcePath,
                    responseBody = requestBody.mergeWith(responseBody) as JSONObjectValue,
                    idKey = DEFAULT_CACHE_RESPONSE_ID_KEY,
                    idValue = idValueFor(DEFAULT_CACHE_RESPONSE_ID_KEY, responseBody)
                )
            } else {
                stubCache.addResponse(
                    path = resourcePath,
                    responseBody = responseBody,
                    idKey = DEFAULT_CACHE_RESPONSE_ID_KEY,
                    idValue = idValueFor(DEFAULT_CACHE_RESPONSE_ID_KEY, responseBody)
                )
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
