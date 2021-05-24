package `in`.specmatic.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.mock.ScenarioStub
import `in`.specmatic.mock.mockFromJSON
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.fail
import java.lang.ref.Reference

class FeatureKtTest {
    @Test
    fun `should lex type identically to pattern`() {
        val contractGherkin = """
            Feature: Math API
            
            Scenario: Addition
              Given type Numbers (number*)
              When POST /add
              And request-body (Numbers)
              Then status 200
              And response-body (number)
        """.trimIndent()

        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)

        val request = HttpRequest().updateMethod("POST").updatePath("/add").updateBody("[1, 2]")

        val response = contractBehaviour.lookupResponse(request)

        assertEquals(200, response.status)

        try {
            response.body.displayableValue().toInt()
        } catch (e: Exception) { fail("${response.body} is not a number")}
    }

    @Test
    fun `should parse tabular pattern directly in request body`() {
        val contractGherkin = """
            Feature: Pet API

            Scenario: Get details
              When POST /pets
              And request-body
                | name        | (string) |
                | description | (string) |
              Then status 200
              And response-body (number)
        """.trimIndent()

        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)

        val request = HttpRequest().updateMethod("POST").updatePath("/pets").updateBody("""{"name": "Benny", "description": "Fluffy and white"}""")
        val response = contractBehaviour.lookupResponse(request)

        assertThat(response.body).isInstanceOf(NumberValue::class.java)
    }

    @Test
    fun `should parse tabular pattern directly in response body`() {
        val contractGherkin = """
            Feature: Pet API
            
            Scenario: Get details
              When GET /pets/(id:number)
              Then status 200
              And response-body
                | id   | (number) |
                | name | (string) |
        """.trimIndent()

        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)

        val request = HttpRequest().updateMethod("GET").updatePath("/pets/10")
        val response = contractBehaviour.lookupResponse(request)

        response.body.let { body ->
            if(body !is JSONObjectValue) fail("Expected JSON object")

            assertTrue(body.jsonObject.getValue("id") is NumberValue)
            assertTrue(body.jsonObject.getValue("name") is StringValue)
        }
    }

    @Test
    fun `should lex http PATCH`() {
        val contractGherkin = """
            Feature: Pet Store
            
            Scenario: Update all pets
              When PATCH /pets
              And request-body {"health": "good"}
              Then status 202
        """.trimIndent()

        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)

        val request = HttpRequest().updateMethod("PATCH").updatePath("/pets").updateBody("""{"health": "good"}""")

        val response = contractBehaviour.lookupResponse(request)

        assertEquals(202, response.status)
    }

    @Test
    fun `should parse multipart file spec`() {
        val feature = parseGherkinStringToFeature("""
            Feature: Customer Data API
            
            Scenario: Upload customer information
              When POST /data
              And request-part customer_info @customer_info.csv text/csv gzip
              Then status 200
        """.trimIndent())

        val pattern = feature.scenarios.single().httpRequestPattern.multiPartFormDataPattern.single() as MultiPartFilePattern
        assertThat(pattern.name).isEqualTo("customer_info")
        val filename = (pattern.filename as ExactValuePattern).pattern.toStringValue()
        assertThat(filename).endsWith("/customer_info.csv")
        assertThat(pattern.contentType).isEqualTo("text/csv")
        assertThat(pattern.contentEncoding).isEqualTo("gzip")
    }

    @Test
    fun `should parse multipart file spec without content encoding`() {
        val behaviour = parseGherkinStringToFeature("""
            Feature: Customer Data API
            
            Scenario: Upload customer information
              When POST /data
              And request-part customer_info @customer_info.csv text/csv
              Then status 200
        """.trimIndent())

        val pattern = behaviour.scenarios.single().httpRequestPattern.multiPartFormDataPattern.single() as MultiPartFilePattern
        assertThat(pattern.name).isEqualTo("customer_info")
        val filename = (pattern.filename as ExactValuePattern).pattern.toStringValue()
        assertThat(filename).endsWith("/customer_info.csv")
        assertThat(pattern.contentType).isEqualTo("text/csv")
        assertThat(pattern.contentEncoding).isEqualTo(null)
    }

    @Test
    fun `should parse multipart file spec without content type and content encoding`() {
        val behaviour = parseGherkinStringToFeature("""
            Feature: Customer Data API
            
            Scenario: Upload customer information
              When POST /data
              And request-part customer_info @customer_info.csv
              Then status 200
        """.trimIndent())

        val pattern = behaviour.scenarios.single().httpRequestPattern.multiPartFormDataPattern.single() as MultiPartFilePattern
        assertThat(pattern.name).isEqualTo("customer_info")
        val filename = (pattern.filename as ExactValuePattern).pattern.toStringValue()
        assertThat(filename).endsWith("/customer_info.csv")
        assertThat(pattern.contentType).isEqualTo(null)
        assertThat(pattern.contentEncoding).isEqualTo(null)
    }

    @Test
    fun `should parse multipart content spec`() {
        val behaviour = parseGherkinStringToFeature("""
            Feature: Customer Data API
            
            Scenario: Upload multipart info
              Given json Customer
                | customerId | (number) |
              And json Order
                | orderId | (number) |
              When POST /data
              And request-part customer_info (Customer)
              And request-part order_info (Order)
              Then status 200
        """.trimIndent())

        val patterns = behaviour.scenarios.single().httpRequestPattern.multiPartFormDataPattern.map { it as MultiPartContentPattern }

        val resolver = Resolver(newPatterns = behaviour.scenarios.single().patterns)

        assertThat(patterns[0].name).isEqualTo("customer_info")
        val pattern0 = deferredToJsonPatternData(patterns[0].content, resolver)
        val contentPattern0 = deferredToNumberPattern(pattern0.getValue("customerId"), resolver)
        assertThat(contentPattern0).isEqualTo(NumberPattern)

        assertThat(patterns[1].name).isEqualTo("order_info")
        val pattern1 = deferredToJsonPatternData(patterns[1].content, resolver)
        val contentPattern1 = deferredToNumberPattern(pattern1.getValue("orderId"), resolver)
        assertThat(contentPattern1).isEqualTo(NumberPattern)
    }

    @Test
    fun `should parse a row lookup pattern`() {
        val behaviour = parseGherkinStringToFeature("""
            Feature: Customer Data API

            Scenario: Upload multipart info
              Given json Data
                | id1 | (customerId:number) |
                | id2 | (orderId:number)    |
              When POST /data
              And request-body (Data)
              Then status 200

              Examples:
              | customerId | orderId |
              | 10         | 20      |
        """.trimIndent())

        val pattern = behaviour.scenarios.single().patterns.getValue("(Data)") as TabularPattern

        assertThat(pattern.pattern.getValue("id1")).isEqualTo(LookupRowPattern(NumberPattern, "customerId"))
        assertThat(pattern.pattern.getValue("id2")).isEqualTo(LookupRowPattern(NumberPattern, "orderId"))
    }

    @Test
    fun `should parse the WIP tag`() {
        val feature = parseGherkinStringToFeature("""
            Feature: Test feature
            
            @WIP
            Scenario: Test scenario
              When GET /
              Then status 200
        """.trimIndent())

        assertThat(feature.scenarios.single().ignoreFailure).isTrue()
    }

    @Test
    fun `a single scenario with 2 examples should be generated out of 2 stubs with the same structure`() {
        val stub1 = NamedStub("stub", ScenarioStub(HttpRequest("GET", "/", queryParams = mapOf("hello" to "world")), HttpResponse.OK))
        val stub2 = NamedStub("stub", ScenarioStub(HttpRequest("GET", "/", queryParams = mapOf("hello" to "hello")), HttpResponse.OK))

        val generatedGherkin = toGherkinFeature("new feature", listOf(stub1, stub2)).trim()

        val expectedGherkin = """Feature: new feature
  Scenario: stub
    When GET /?hello=(string)
    Then status 200
  
    Examples:
    | hello |
    | world |
    | hello |""".trim()

        assertThat(generatedGherkin).isEqualTo(expectedGherkin)
    }

    @Test
    fun `a single scenario with 2 examples of a multipart file should be generated out of 2 stubs with the same structure`() {
        val stub1 = NamedStub("stub", ScenarioStub(HttpRequest("GET", "/", multiPartFormData = listOf(MultiPartFileValue("employees", "employees1.csv", content=MultiPartContent("1,2,3")))), HttpResponse.OK))
        val stub2 = NamedStub("stub", ScenarioStub(HttpRequest("GET", "/", multiPartFormData = listOf(MultiPartFileValue("employees", "employees2.csv", content=MultiPartContent("1,2,3")))), HttpResponse.OK))

        val generatedGherkin = toGherkinFeature("new feature", listOf(stub1, stub2)).trim()

        val expectedGherkin = """Feature: new feature
  Scenario: stub
    When GET /
    And request-part employees @(string)
    Then status 200
  
    Examples:
    | employees_filename |
    | employees1.csv |
    | employees2.csv |""".trim()

        assertThat(generatedGherkin).isEqualTo(expectedGherkin)
    }

    @Test
    fun `an example should have the response Date headers value at the end as a comment`() {
        val stub = NamedStub("stub", ScenarioStub(HttpRequest("POST", "/", body = StringValue("hello world")), HttpResponse.OK.copy(headers = mapOf("Date" to "Tuesday 1st Jan 2020"))))

        val generatedGherkin = toGherkinFeature("new feature", listOf(stub)).trim()

        val expectedGherkin = """Feature: new feature
  Scenario: stub
    When POST /
    And request-body (RequestBody: string)
    Then status 200
    And response-header Date (string)
  
    Examples:
    | RequestBody | __comment__ |
    | hello world | Tuesday 1st Jan 2020 |""".trim()

        assertThat(generatedGherkin).isEqualTo(expectedGherkin)
    }

    private fun deferredToJsonPatternData(pattern: Pattern, resolver: Resolver): Map<String, Pattern> =
            ((pattern as DeferredPattern).resolvePattern(resolver) as TabularPattern).pattern

    private fun deferredToNumberPattern(pattern: Pattern, resolver: Resolver): NumberPattern =
            (pattern as DeferredPattern).resolvePattern(resolver) as NumberPattern

    private fun resolveDeferred(pattern: Pattern, resolver: Resolver): Pattern =
            (pattern as DeferredPattern).resolvePattern(resolver)

    @Test
    fun `should handle multiple types in the response at different levels with the same key and hence the same name`() {
        val stubJSON = """
            {
            	"http-request": {
            		"method": "POST",
            		"path": "/data"
            	},
            	"http-response": {
            		"status": 200,
            		"body": {
            			"entries": [
            				{
            					"name": "James"
            				}
            			],
            			"data": {
            				"entries": [
            					{
            						"id": 10
            					}
            				]
            			}
            		}
            	}
            }
        """.trimIndent()

        val gherkinString = stubJSON.toFeatureString().trim()

        assertThat(gherkinString).isEqualTo("""Feature: New Feature
  Scenario: Test Feature
    Given type Entries
      | name | (string) |
    And type Entries_
      | id | (number) |
    And type Data
      | entries | (Entries_*) |
    And type ResponseBody
      | entries | (Entries*) |
      | data | (Data) |
    When POST /data
    Then status 200
    And response-body (ResponseBody)""")
    }

    @Test
    fun `should handle multiple types in the request at different levels with the same key and hence the same name`() {
        val stubJSON = """
            {
                "http-request": {
                    "method": "POST",
                    "path": "/data",
                    "body": {
                        "entries": [
                            {
                                "name": "James"
                            }
                        ],
                        "data": {
                            "entries": [
                                {
                                    "id": 10
                                }
                            ]
                        }
                    }
                },
                "http-response": {
                    "status": 200,
                    "body": {
                        "operationid": 10
                    }
                }
            }
        """.trimIndent()

        val gherkinString = stubJSON.toFeatureString().trim()

        println(gherkinString)
        assertThat(gherkinString).isEqualTo("""Feature: New Feature
  Scenario: Test Feature
    Given type Entries
      | name | (string) |
    And type Entries_
      | id | (number) |
    And type Data
      | entries | (Entries_*) |
    And type RequestBody
      | entries | (Entries*) |
      | data | (Data) |
    And type ResponseBody
      | operationid | (number) |
    When POST /data
    And request-body (RequestBody)
    Then status 200
    And response-body (ResponseBody)
  
    Examples:
    | name | id |
    | James | 10 |""")
    }

    @Test
    fun `bindings should get generated when a feature contains the export statement`() {
        val contractGherkin = """
            Feature: Pet API
            
            Scenario: Get details
              When GET /pets/(id:number)
              Then status 200
              And response-header X-Data (string)
              And export data = response-header.X-Data
        """.trimIndent()

        val feature = parseGherkinStringToFeature(contractGherkin)

        feature.scenarios.first().let {
            assertThat(it.bindings).containsKey("data")
            assertThat(it.bindings["data"]).isEqualTo("response-header.X-Data")
        }
    }

    @Test
    fun `references should get generated when a feature contains the value statement`() {
        val contractGherkin = """
            Feature: Pet API
            
            Background:
              Given value data from data.$CONTRACT_EXTENSION
            
            Scenario: Get details
              When GET /pets/(id:number)
              Then status 200
              And response-header X-Data (string)
        """.trimIndent()

        val feature = parseGherkinStringToFeature(contractGherkin, "original.$CONTRACT_EXTENSION")

        feature.scenarios.first().let {
            assertThat(it.references).containsKey("data")
            assertThat(it.references["data"]).isInstanceOf(References::class.java)
            assertThat(it.references["data"]?.valueName).isEqualTo("data")
            assertThat(it.references["data"]?.qontractFilePath?.path).isEqualTo("data.$CONTRACT_EXTENSION")
            assertThat(it.references["data"]?.qontractFilePath?.relativeTo).isEqualTo("original.$CONTRACT_EXTENSION")
        }
    }

    fun String.toFeatureString(): String {
        val parsedJSONValue = parsedJSON(this) as JSONObjectValue
        return toGherkinFeature(NamedStub("Test Feature", mockFromJSON(parsedJSONValue.jsonObject)))
    }
}
