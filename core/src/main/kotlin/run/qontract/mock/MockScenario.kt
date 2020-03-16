package run.qontract.mock

import run.qontract.core.HttpRequest
import run.qontract.core.HttpResponse

class MockScenario {
    val request: HttpRequest
    val response: HttpResponse
    val facts: MutableMap<String, Any>

    constructor(mockRequest: HttpRequest, mockResponse: HttpResponse, facts: MutableMap<String, Any>) {
        request = mockRequest
        response = mockResponse
        this.facts = facts
    }

    constructor() {
        request = HttpRequest()
        response = HttpResponse()
        facts = mutableMapOf()
    }

    @Throws(HttpMockException::class)
    fun toJSON(): MutableMap<String, Any> {
        val mockInteraction = mutableMapOf<String, Any>()
        mockInteraction[MOCK_HTTP_REQUEST] = request.toJSON()
        mockInteraction[MOCK_HTTP_RESPONSE] = response.toJSON()
        if (facts.size > 0) mockInteraction[MOCK_FACTS] = facts
        return mockInteraction
    }

    fun addFact(name: String, value: Any) {
        facts[name] = value
    }

    companion object {
        protected const val MOCK_HTTP_REQUEST = "mock-http-request"
        protected const val MOCK_HTTP_RESPONSE = "mock-http-response"
        protected const val MOCK_FACTS = "mock-facts"
        @Throws(HttpMockException::class)
        fun validate(mockSpec: Map<String, Any?>) {
            if (!mockSpec.containsKey(MOCK_HTTP_REQUEST)) throw HttpMockException("This spec does not information about the request to be mocked.")
            if (!mockSpec.containsKey(MOCK_HTTP_RESPONSE)) throw HttpMockException("This spec does not information about the response to be mocked.")
        }

        fun fromJSON(mockSpec: MutableMap<String, Any?>): MockScenario {
            val mockRequest = HttpRequest.fromJSON(mockSpec[MOCK_HTTP_REQUEST] as Map<String, Any>)
            val mockResponse = HttpResponse.fromJSON(mockSpec[MOCK_HTTP_RESPONSE] as Map<String, Any>)

            val mockFacts: MutableMap<String, Any> = if (mockSpec.containsKey(MOCK_FACTS)) {
                (mockSpec[MOCK_FACTS] as Map<String, Any>).toMutableMap()
            } else mutableMapOf()

            return MockScenario(mockRequest, mockResponse, mockFacts)
        }
    }
}