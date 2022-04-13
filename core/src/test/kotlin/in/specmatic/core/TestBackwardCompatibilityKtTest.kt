package `in`.specmatic.core

import `in`.specmatic.conversions.OpenApiSpecification
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested

internal class TestBackwardCompatibilityKtTest {
    @Test
    fun `contract backward compatibility should break when optional key is made mandatory in request`() {
        val gherkin1 = """
Feature: Older contract API

Scenario: api call
Given json Value
| value     | (number) |
| optional? | (number) |
When POST /value
And request-body (Value)
Then status 200
    """.trim()

        val gherkin2 = """
Feature: Newer contract API

Scenario: api call
Given json Value
| value    | (number) |
| optional | (number) |
When POST /value
And request-body (Value)
Then status 200
    """.trim()

        val olderContract = parseGherkinStringToFeature(gherkin1)
        val newerContract = parseGherkinStringToFeature(gherkin2)

        val result: Results = testBackwardCompatibility(olderContract, newerContract)

        println(result.report())

        assertEquals(1, result.failureCount)
        assertEquals(1, result.successCount)
    }

    @Test
    fun `contract backward compatibility should break when mandatory key is made optional in response`() {
        val gherkin1 = """
Feature: Older contract API

Scenario: api call
Given json Value
| value     | (number) |
| mandatory | (number) |
When POST /value
And request-body "test"
Then status 200
And response-body (Value)
    """.trim()

        val gherkin2 = """
Feature: Newer contract API

Scenario: api call
Given json Value
| value      | (number) |
| mandatory? | (number) |
When POST /value
And request-body "test"
Then status 200
And response-body (Value)
    """.trim()

        val olderContract = parseGherkinStringToFeature(gherkin1)
        val newerContract = parseGherkinStringToFeature(gherkin2)

        val result: Results = testBackwardCompatibility(olderContract, newerContract)

        println(result.report())

        assertEquals(1, result.failureCount)
        assertEquals(0, result.successCount)
    }

    @Test
    fun `contract backward compatibility should break when there is value incompatibility one level down`() {
        val gherkin1 = """
Feature: Old contract
  Scenario: Test Scenario
    Given type RequestBody
    | address? | (Address?) |
    And type Address
    | street | (string) |
    When POST /
    And request-body (RequestBody)
    Then status 200
    """.trim()

        val gherkin2 = """
Feature: New contract
  Scenario: Test Scenario
    Given type RequestBody
    | address? | (Address?) |
    And type Address
    | street | (number) |
    When POST /
    And request-body (RequestBody)
    Then status 200
    """.trim()

        val olderContract = parseGherkinStringToFeature(gherkin1)
        val newerContract = parseGherkinStringToFeature(gherkin2)

        val result: Results = testBackwardCompatibility(olderContract, newerContract)

        println(result.report())

        assertEquals(1, result.failureCount)
    }

    @Test
    fun `contract backward compatibility should not break when there is optional key compatibility one level down`() {
        val gherkin1 = """
Feature: Old contract
  Scenario: Test Scenario
    Given type RequestBody
    | address | (Address) |
    And type Address
    | street? | (string) |
    When POST /
    And request-body (RequestBody)
    Then status 200
    """.trim()

        val gherkin2 = """
Feature: New contract
  Scenario: Test Scenario
    Given type RequestBody
    | address | (Address) |
    And type Address
    | street? | (string) |
    When POST /
    And request-body (RequestBody)
    Then status 200
    """.trim()

        val olderContract = parseGherkinStringToFeature(gherkin1)
        val newerContract = parseGherkinStringToFeature(gherkin2)

        val result: Results = testBackwardCompatibility(olderContract, newerContract)

        println(result.report())

        assertEquals(0, result.failureCount)
    }

    @Test
    fun `contract backward compatibility should break when optional key is made mandatory one level down in request`() {
        val gherkin1 = """
Feature: Old contract
  Scenario: Test Scenario
    Given type RequestBody
    | address | (Address) |
    And type Address
    | street? | (string) |
    When POST /
    And request-body (RequestBody)
    Then status 200
    """.trim()

        val gherkin2 = """
Feature: New contract
  Scenario: Test Scenario
    Given type RequestBody
    | address | (Address) |
    And type Address
    | street | (string) |
    When POST /
    And request-body (RequestBody)
    Then status 200
    """.trim()

        val olderContract = parseGherkinStringToFeature(gherkin1)
        val newerContract = parseGherkinStringToFeature(gherkin2)

        val result: Results = testBackwardCompatibility(olderContract, newerContract)

        println(result.report())

        assertEquals(1, result.failureCount)
    }

    @Test
    fun `contract backward compatibility should break when optional key is made mandatory inside an optional parent`() {
        val gherkin1 = """
Feature: Old contract
  Scenario: Test Scenario
    Given type RequestBody
    | address? | (Address) |
    And type Address
    | street? | (string) |
    When POST /
    And request-body (RequestBody)
    Then status 200
    """.trim()

        val gherkin2 = """
Feature: New contract
  Scenario: Test Scenario
    Given type RequestBody
    | address? | (Address) |
    And type Address
    | street | (string) |
    When POST /
    And request-body (RequestBody)
    Then status 200
    """.trim()

        val olderContract = parseGherkinStringToFeature(gherkin1)
        val newerContract = parseGherkinStringToFeature(gherkin2)

        val result: Results = testBackwardCompatibility(olderContract, newerContract)

        println(result.report())

        assertEquals(2, result.successCount)
        assertEquals(1, result.failureCount)
    }

    @Test
    fun `contract backward compatibility should break when optional key is made mandatory inside an optional parent two levels down`() {
        val gherkin1 = """
Feature: Old contract
  Scenario: Test Scenario
    Given type RequestBody
    | person | (Person) |
    And type Person
    | address? | (Address) |
    And type Address
    | street? | (string) |
    When POST /
    And request-body (RequestBody)
    Then status 200
    """.trim()

        val gherkin2 = """
Feature: Old contract
  Scenario: Test Scenario
    Given type RequestBody
    | person | (Person) |
    And type Person
    | address? | (Address) |
    And type Address
    | street | (string) |
    When POST /
    And request-body (RequestBody)
    Then status 200
    """.trim()

        val olderContract = parseGherkinStringToFeature(gherkin1)
        val newerContract = parseGherkinStringToFeature(gherkin2)

        val result: Results = testBackwardCompatibility(olderContract, newerContract)

        println(result.report())

        assertEquals(2, result.successCount)
        assertEquals(1, result.failureCount)
    }

    @Test
    fun `contract backward compatibility should break when optional key data type is changed inside an optional parent two levels down`() {
        val gherkin1 = """
Feature: Old contract
  Scenario: Test Scenario
    Given type RequestBody
    | person | (Person) |
    And type Person
    | address? | (Address) |
    And type Address
    | street? | (string) |
    When POST /
    And request-body (RequestBody)
    Then status 200
    """.trim()

        val gherkin2 = """
Feature: Old contract
  Scenario: Test Scenario
    Given type RequestBody
    | person | (Person) |
    And type Person
    | address? | (Address) |
    And type Address
    | street | (number) |
    When POST /
    And request-body (RequestBody)
    Then status 200
    """.trim()

        val olderContract = parseGherkinStringToFeature(gherkin1)
        val newerContract = parseGherkinStringToFeature(gherkin2)

        val result: Results = testBackwardCompatibility(olderContract, newerContract)

        println(result.report())

        assertEquals(1, result.successCount)
        assertEquals(2, result.failureCount)
    }

    @Test
    fun `contract backward compatibility should break when optional value is made mandatory inside an optional parent two levels down`() {
        val gherkin1 = """
Feature: Old contract
  Scenario: Test Scenario
    Given type RequestBody
    | person | (Person) |
    And type Person
    | address? | (Address) |
    And type Address
    | street? | (string?) |
    When POST /
    And request-body (RequestBody)
    Then status 200
    """.trim()

        val gherkin2 = """
Feature: Old contract
  Scenario: Test Scenario
    Given type RequestBody
    | person | (Person) |
    And type Person
    | address? | (Address) |
    And type Address
    | street? | (string) |
    When POST /
    And request-body (RequestBody)
    Then status 200
    """.trim()

        val olderContract = parseGherkinStringToFeature(gherkin1)
        val newerContract = parseGherkinStringToFeature(gherkin2)

        val result: Results = testBackwardCompatibility(olderContract, newerContract)

        println(result.report())

        assertEquals(3, result.successCount)
        assertEquals(1, result.failureCount)
    }

    @Test
    fun `contract backward compatibility should break when mandatory key is made optional one level down in response`() {
        val gherkin1 = """
Feature: Old contract
  Scenario: Test Scenario
    Given type ResponseBody
    | address | (Address) |
    And type Address
    | street | (string) |
    When POST /
    And request-body "test"
    Then status 200
    And response-body (ResponseBody)
    """.trim()

        val gherkin2 = """
Feature: New contract
  Scenario: Test Scenario
    Given type ResponseBody
    | address | (Address) |
    And type Address
    | street? | (string) |
    When POST /
    And request-body "test"
    Then status 200
    And response-body (ResponseBody) 
    """.trim()

        val olderContract = parseGherkinStringToFeature(gherkin1)
        val newerContract = parseGherkinStringToFeature(gherkin2)

        val result: Results = testBackwardCompatibility(olderContract, newerContract)

        println(result.report())

        assertEquals(1, result.failureCount)
    }

    @Test
    fun `contract backward compatibility should break when mandatory key is made optional two levels down in response`() {
        val gherkin1 = """
Feature: Old contract
  Scenario: Test Scenario
    Given type ResponseBody
    | person | (Person) |
    And type Person
    | address? | (Address) |
    And type Address
    | street | (string) |
    When POST /
    And request-body "test"
    Then status 200
    And response-body (ResponseBody)
    """.trim()

        val gherkin2 = """
Feature: New contract
  Scenario: Test Scenario
    Given type ResponseBody
    | person | (Person) |
    And type Person
    | address? | (Address) |
    And type Address
    | street? | (string) |
    When POST /
    And request-body "test"
    Then status 200
    And response-body (ResponseBody) 
    """.trim()

        val olderContract = parseGherkinStringToFeature(gherkin1)
        val newerContract = parseGherkinStringToFeature(gherkin2)

        val result: Results = testBackwardCompatibility(olderContract, newerContract)

        println(result.report())

        assertEquals(1, result.failureCount)
    }

    @Test
    fun `contract backward compatibility should not break when both have an optional keys`() {
        val gherkin1 = """
Feature: API contract

Scenario: api call
Given json Value
| value     | (number) |
| optional? | (number) |
And fact id 10
When POST /value/(id:number)
And request-body (Value)
Then status 200
    """.trim()

        val gherkin2 = """
Feature: API contract

Scenario: api call
Given json Value
| value    | (number) |
| optional? | (number) |
And fact id 10
When POST /value/(id:number)
And request-body (Value)
Then status 200
    """.trim()

        val olderContract = parseGherkinStringToFeature(gherkin1)
        val newerContract = parseGherkinStringToFeature(gherkin2)

        val result: Results = testBackwardCompatibility(olderContract, newerContract)

        println(result.report())

        assertEquals(2, result.successCount)
        assertEquals(0, result.failureCount)
    }

    @Test
    fun `contract backward compatibility should break when a new fact is added`() {
        val gherkin1 = """
Feature: Older contract API

Scenario: api call
Given json Value
| value     | (number) |
| optional? | (number) |
When POST /value/(id:number)
And request-body (Value)
Then status 200
    """.trim()

        val gherkin2 = """
Feature: Older contract API

Scenario: api call
Given json Value
| value    | (number) |
| optional? | (number) |
And fact id 10
When POST /value/(id:number)
And request-body (Value)
Then status 200
    """.trim()

        val olderContract = parseGherkinStringToFeature(gherkin1)
        val newerContract = parseGherkinStringToFeature(gherkin2)

        val results: Results = testBackwardCompatibility(olderContract, newerContract)

        println(results.report())

        assertEquals(0, results.successCount)
        assertEquals(1, results.failureCount)
    }

    @Test
    fun `contract should test successfully against itself when fact name is specified without a value in the URL path`() {
        val gherkin = """
Feature: Contract API

Scenario: api call
Given fact id
When POST /value/(id:number)
Then status 200
    """.trim()

        val contract = parseGherkinStringToFeature(gherkin)

        val results: Results = testBackwardCompatibility(contract, contract)

        if (results.failureCount > 0)
            println(results.report())

        assertEquals(1, results.successCount)
        assertEquals(0, results.failureCount)
    }

    @Test
    fun `contract should test successfully against itself when fact name is specified without a value in the query`() {
        val gherkin = """
Feature: Contract API

Scenario: Test Contract
Given fact id
When GET /value?id=(number)
Then status 200
    """.trim()

        val contract = parseGherkinStringToFeature(gherkin)

        val results: Results = testBackwardCompatibility(contract, contract)

        if (results.failureCount > 0)
            println(results.report())

        assertEquals(2, results.successCount)
        assertEquals(0, results.failureCount)
    }

    @Test
    fun `should be able to validate new contract compatibility with optional request body`() {
        val gherkin = """
Feature: Contract API

Scenario: api call
When POST /number
And request-body (number?)
Then status 200
    """.trim()

        val contract = parseGherkinStringToFeature(gherkin)

        val results: Results = testBackwardCompatibility(contract, contract)

        if (results.failureCount > 0)
            println(results.report())

        assertEquals(2, results.successCount)
        assertEquals(0, results.failureCount)
    }

    @Test
    fun `should be able to validate new contract compatibility with optional key in request body`() {
        val gherkin = """
Feature: Contract API

Scenario: api call
Given json Number
| number | (number?) |
When POST /number
And request-body (Number)
Then status 200
    """.trim()

        val contract = parseGherkinStringToFeature(gherkin)

        val results: Results = testBackwardCompatibility(contract, contract)

        if (results.failureCount > 0)
            println(results.report())

        assertEquals(2, results.successCount)
        assertEquals(0, results.failureCount)
    }

    @Test
    fun `should be able to validate new contract compatibility with optional response body`() {
        val gherkin = """
Feature: Contract API

Scenario: api call
When POST /number
Then status 200
And response-body (number?)
    """.trim()

        val contract = parseGherkinStringToFeature(gherkin)

        val results: Results = testBackwardCompatibility(contract, contract)

        if (results.failureCount > 0)
            println(results.report())

        assertEquals(1, results.successCount)
        assertEquals(0, results.failureCount)
    }

    @Test
    fun `should be able to validate new contract compatibility with optional key in response body`() {
        val gherkin = """
Feature: Contract API

Scenario: api call
Given json Number
| number | (number?) |
When POST /number
Then status 200
And response-body (Number)
    """.trim()

        val contract = parseGherkinStringToFeature(gherkin)

        val results: Results = testBackwardCompatibility(contract, contract)

        if (results.failureCount > 0)
            println(results.report())

        assertEquals(1, results.successCount)
        assertEquals(0, results.failureCount)
    }

    @Test
    fun `contract with a required key should not match a contract with the same key made optional`() {
        val olderBehaviour = parseGherkinStringToFeature("""
Feature: Contract API

Scenario: api call
When POST /number
And request-body (number)
Then status 200
And response-body
| number      | (number) |
| description | (string) |
""".trim())

        val newerBehaviour = parseGherkinStringToFeature("""
Feature: Contract API

Scenario: api call
When POST /number
And request-body (number)
Then status 200
And response-body
| number       | (number) |
| description? | (string) |
""".trim())

        val results: Results = testBackwardCompatibility(olderBehaviour, newerBehaviour)

        println(results.report())

        assertEquals(0, results.successCount)
        assertEquals(1, results.failureCount)
    }

    @Test
    fun `contract with an optional key in the response should pass against itself`() {
        val behaviour = parseGherkinStringToFeature("""
Feature: Contract API

Scenario: api call
When POST /number
And request-body (number)
Then status 200
And response-body
| number       | (number) |
| description? | (string) |
""".trim())

        val results: Results = testBackwardCompatibility(behaviour, behaviour)

        println(results.report())
        assertEquals(1, results.successCount)
        assertEquals(0, results.failureCount)
    }

    @Test
    fun `should work with multipart content part`() {
        val behaviour = parseGherkinStringToFeature("""
Feature: Contract API

Scenario: api call
When POST /number
And request-part number (number)
Then status 200
And response-body
| number       | (number) |
| description? | (string) |
""".trim())

        val results: Results = testBackwardCompatibility(behaviour, behaviour)

        println(results.report())
        assertEquals(1, results.successCount)
        assertEquals(0, results.failureCount)
    }

    @Test
    fun `should work with multipart file part`() {
        val behaviour = parseGherkinStringToFeature("""
Feature: Contract API

Scenario: api call
When POST /number
And request-part number @number.txt text/plain
Then status 200
And response-body
| number       | (number) |
| description? | (string) |
""".trim())

        val results: Results = testBackwardCompatibility(behaviour, behaviour)

        println(results.report())
        assertEquals(1, results.successCount)
        assertEquals(0, results.failureCount)
    }

    @Test
    fun `should fail given a file part in one and a content part in the other`() {
        val older = parseGherkinStringToFeature("""
Feature: Contract API

Scenario: api call
When POST /number
And request-part number @number.txt text/plain
Then status 200
And response-body
| number       | (number) |
| description? | (string) |
""".trim())

        val newer = parseGherkinStringToFeature("""
Feature: Contract API

Scenario: api call
When POST /number
And request-part number (number))
Then status 200
And response-body
| number       | (number) |
| description? | (string) |
""".trim())

        val results: Results = testBackwardCompatibility(older, newer)

        println(results.report())
        assertThat(results.success()).isFalse()
    }

    @Test
    fun `a contract should be backward compatible with itself`() {
        val gherkin = """
Feature: Contract API

Scenario: api call
When POST /number
Then status 200
And response-body (number)
    """.trim()

        val contract = parseGherkinStringToFeature(gherkin)

        val results: Results = testBackwardCompatibility(contract, contract)

        if (results.failureCount > 0)
            println(results.report())

        assertEquals(1, results.successCount)
        assertEquals(0, results.failureCount)
    }

    @Test
    fun `a contract with named patterns should be backward compatible with itself`() {
        val gherkin = """
Feature: Contract API

Scenario: api call
Given json Payload
  | number | (number) |
When POST /number
  And request-body (Payload)
Then status 200
And response-body (number)
    """.trim()

        val contract = parseGherkinStringToFeature(gherkin)

        val results: Results = testBackwardCompatibility(contract, contract)

        if (results.failureCount > 0)
            println(results.report())

        assertEquals(1, results.successCount)
        assertEquals(0, results.failureCount)
    }

    @Test
    fun `a contract with named patterns should not be backward compatible with another contract with a different pattern against the same name`() {
        val gherkin1 = """
Feature: Contract API

Scenario: api call
Given json Payload
  | number | (number) |
When POST /number
  And request-body (Payload)
Then status 200
And response-body (number)
    """.trim()

        val gherkin2 = """
Feature: Contract API

Scenario: api call
Given json Payload
  | number | (string) |
When POST /number
  And request-body (Payload)
Then status 200
And response-body (number)
    """.trim()

        val results: Results = testBackwardCompatibility(parseGherkinStringToFeature(gherkin1), parseGherkinStringToFeature(gherkin2))

        if (results.failureCount > 0)
            println(results.report())

        assertEquals(0, results.successCount)
        assertEquals(1, results.failureCount)
    }

    @Test
    fun `a breaking WIP scenario should not break backward compatibility tests`() {
        val gherkin1 = """
Feature: Contract API

@WIP
Scenario: api call
When POST /data
  And request-body (number)
Then status 200
    """.trim()

        val gherkin2 = """
Feature: Contract API

@WIP
Scenario: api call
When POST /data
  And request-body (string)
Then status 200
    """.trim()

        val results: Results = testBackwardCompatibility(parseGherkinStringToFeature(gherkin1), parseGherkinStringToFeature(gherkin2))

        if (results.failureCount > 0)
            println(results.report())

        assertThat(results.successCount).isZero()
        assertThat(results.failureCount).isZero()
        assertThat(results.success()).isTrue()
    }

    @Test
    fun `two xml contracts should be backward compatibility when the only thing changing is namespace prefixes`() {
        val gherkin1 = """
Feature: Contract API

Scenario: api call
When POST /data
  And request-body <ns1:customer xmlns:ns1="http://example.com/customer"><name>(string)</name></ns1:customer>
Then status 200
    """.trim()

        val gherkin2 = """
Feature: Contract API

Scenario: api call
When POST /data
  And request-body <ns2:customer xmlns:ns2="http://example.com/customer"><name>(string)</name></ns2:customer>
Then status 200
    """.trim()

        val results: Results = testBackwardCompatibility(parseGherkinStringToFeature(gherkin1), parseGherkinStringToFeature(gherkin2))

        if (results.failureCount > 0)
            println(results.report())

        assertThat(results.successCount).isOne()
        assertThat(results.failureCount).isZero()
        assertThat(results.success()).isTrue()
    }

    @Test
    fun `two xml contracts should not be backward compatibility when optional key is made mandatory in request`() {
        val gherkin1 = """
Feature: Contract API

Scenario: api call
When POST /data
  And request-body <ns1:customer xmlns:ns1="http://example.com/customer"><name specmatic_occurs="optional">(string)</name></ns1:customer>
Then status 200
    """.trim()

        val gherkin2 = """
Feature: Contract API

Scenario: api call
When POST /data
  And request-body <ns2:customer xmlns:ns2="http://example.com/customer"><name>(string)</name></ns2:customer>
Then status 200
    """.trim()

        val results: Results = testBackwardCompatibility(parseGherkinStringToFeature(gherkin1), parseGherkinStringToFeature(gherkin2))

        if (results.failureCount > 0)
            println(results.report())

        assertThat(results.successCount).isOne()
        assertThat(results.failureCount).isOne()
        assertThat(results.success()).isFalse()
    }

    @Test
    fun `two xml contracts should not be backward compatibility when mandatory key is made optional in response`() {
        val gherkin1 = """
Feature: Contract API

Scenario: api call
When POST /data
  And request-body "test"
Then status 200
  And response-body <ns1:customer xmlns:ns1="http://example.com/customer"><name>(string)</name></ns1:customer>
    """.trim()

        val gherkin2 = """
Feature: Contract API

Scenario: api call
When POST /data
  And request-body "test"
Then status 200
  And response-body <ns1:customer xmlns:ns1="http://example.com/customer"><name specmatic_occurs="optional">(string)</name></ns1:customer>
    """.trim()

        val results: Results = testBackwardCompatibility(parseGherkinStringToFeature(gherkin1), parseGherkinStringToFeature(gherkin2))

        if (results.failureCount > 0)
            println(results.report())

        assertThat(results.successCount).isZero()
        assertThat(results.failureCount).isOne()
        assertThat(results.success()).isFalse()
    }

    @Test
    fun `a contract with an optional and a required node in that order should be backward compatible with itself`() {
        val docString = "\"\"\""
        val gherkin =
            """
                Feature: test xml
                    Scenario: Test xml
                    Given type RequestBody
                    $docString
                        <parent>
                            <optionalNode specmatic_occurs="optional" />
                            <requiredNode />
                        </parent>
                    $docString
                    When POST /
                    And request-body (RequestBody)
                    Then status 200
            """.trimIndent()

        val feature = parseGherkinStringToFeature(gherkin)
        val results = testBackwardCompatibility(feature, feature)

        if (results.failureCount > 0)
            println(results.report())

        assertThat(results.success()).isTrue()
    }

    @Test
    fun `backward compatibility error in optional key should not contain a question mark`() {
        val olderContract: Feature =
            """
        openapi: 3.0.0
        info:
          title: Sample API
          description: Sample
          version: 1
        servers:
          - url: http://api.example.com/v1
            description: Optional server description, e.g. Main (production) server
        paths:
          /data:
            get:
              summary: hello world
              description: Optional extended description in CommonMark or HTML.
              responses:
                '200':
                  description: Says hello
                  content:
                    application/json:
                      schema:
                        type: object
                        properties:
                          data:
                            type: string
            """.trimIndent().openAPIToContract()

        val newerContract: Feature =
            """
        openapi: 3.0.0
        info:
          title: Sample API
          description: Sample
          version: 1
        servers:
          - url: http://api.example.com/v1
            description: Optional server description, e.g. Main (production) server
        paths:
          /data:
            get:
              summary: hello world
              description: Optional extended description in CommonMark or HTML.
              responses:
                '200':
                  description: Says hello
                  content:
                    application/json:
                      schema:
                        type: object
                        properties:
                          data:
                            type: number
            """.trimIndent().openAPIToContract()

        val result: Results = testBackwardCompatibility(olderContract, newerContract)

        assertThat(result.report()).doesNotContain("data?")
    }

    @Nested
    inner class EnumStringIsBackardCompatibleWithString {
        val olderContract: Feature =
            """
        openapi: 3.0.0
        info:
          title: Sample API
          description: Sample
          version: 1
        servers:
          - url: http://api.example.com/v1
            description: Optional server description, e.g. Main (production) server
        paths:
          /data:
            get:
              summary: hello world
              description: Optional extended description in CommonMark or HTML.
              responses:
                '200':
                  description: Says hello
                  content:
                    application/json:
                      schema:
                        type: object
                        properties:
                          data:
                            type: string
            """.trimIndent().openAPIToContract()

        val newerContract: Feature =
            """
        openapi: 3.0.0
        info:
          title: Sample API
          description: Sample
          version: 1
        servers:
          - url: http://api.example.com/v1
            description: Optional server description, e.g. Main (production) server
        paths:
          /data:
            get:
              summary: hello world
              description: Optional extended description in CommonMark or HTML.
              responses:
                '200':
                  description: Says hello
                  content:
                    application/json:
                      schema:
                        type: object
                        properties:
                          data:
                            type: string
                            enum:
                              - 01
                              - 02
            """.trimIndent().openAPIToContract()


        @Test
        fun `new should be should be backward compatible with old`() {
            val results: Results = testBackwardCompatibility(olderContract, newerContract)

            assertThat(results.success()).isTrue
        }

        @Test
        fun `old should be backward incompatible with new`() {
            val results: Results = testBackwardCompatibility(newerContract, olderContract)

            assertThat(results.hasFailures()).isTrue
        }
    }

    fun `backward compatibility error in request shows contextual error message`() {
        val oldContract = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.0
info:
  title: Sample API
  version: 0.1.9
paths:
  /data:
    post:
      summary: hello world
      description: test
      requestBody:
        content:
          application/json:
            examples:
              200_OK:
                value:
                  data: 10
            schema:
              type: object
              properties:
                data:
                  type: number
              required:
                - data
      responses:
        '200':
          description: Says hello
          content:
            text/plain:
              examples:
                200_OK:
                  value: 10
              schema:
                type: number
""".trimIndent(), ""
        ).toFeature()

        val newContract = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.0
info:
  title: Sample API
  version: 0.1.9
paths:
  /data:
    post:
      summary: hello world
      description: test
      requestBody:
        content:
          application/json:
            examples:
              200_OK:
                value:
                  data: abc
            schema:
              type: object
              properties:
                data:
                  type: string
              required:
                - data
      responses:
        '200':
          description: Says hello
          content:
            text/plain:
              examples:
                200_OK:
                  value: 10
              schema:
                type: number
""".trimIndent(), ""
        ).toFeature()

        val result: Results = testBackwardCompatibility(oldContract, newContract)

        assertThat(result.report()).contains("New contract expected")
        assertThat(result.report()).contains("old contract sent")
    }

    @Test
    fun `backward compatibility error in response shows contextual error message`() {
        val oldContract = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.0
info:
  title: Sample API
  version: 0.1.9
paths:
  /data:
    post:
      summary: hello world
      description: test
      requestBody:
        content:
          application/json:
            examples:
              200_OK:
                value:
                  data: 10
            schema:
              type: object
              properties:
                data:
                  type: number
              required:
                - data
      responses:
        '200':
          description: Says hello
          content:
            text/plain:
              examples:
                200_OK:
                  value: 10
              schema:
                type: string
""".trimIndent(), ""
        ).toFeature()

        val newContract = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.0
info:
  title: Sample API
  version: 0.1.9
paths:
  /data:
    post:
      summary: hello world
      description: test
      requestBody:
        content:
          application/json:
            examples:
              200_OK:
                value:
                  data: 10
            schema:
              type: object
              properties:
                data:
                  type: number
              required:
                - data
      responses:
        '200':
          description: Says hello
          content:
            text/plain:
              examples:
                200_OK:
                  value: 10
              schema:
                type: number
""".trimIndent(), ""
        ).toFeature()

        val result: Results = testBackwardCompatibility(oldContract, newContract)

        assertThat(result.report()).contains("new contract response")
        assertThat(result.report()).contains("old contract")
    }

    @Test
    fun `backward compatibility errors are deduplicated`() {
        val oldContract = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.0
info:
  title: Sample API
  version: 0.1.9
paths:
  /data:
    post:
      summary: hello world
      description: test
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                data:
                  type: number
      responses:
        '200':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
""".trimIndent(), ""
        ).toFeature()

        val newContract = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.0
info:
  title: Sample API
  version: 0.1.9
paths:
  /data:
    post:
      summary: hello world
      description: test
      requestBody:
        content:
          application/json:
            schema:
              type: object
              required:
                - data2
              properties:
                data:
                  type: number
                data2:
                  type: number
      responses:
        '200':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
""".trimIndent(), ""
        ).toFeature()

        val result: Results = testBackwardCompatibility(oldContract, newContract)

        val report: String = result.report()
        println(report)

        assertThat(report.indexOf("REQUEST.BODY.data2")).`as`("There should only be one instance of any error report that occurs in multiple contract-vs-contract requests").isEqualTo(report.lastIndexOf("REQUEST.BODY.data2"))
    }

    @Test
    fun `backward compatibility errors in request and response are returned together`() {
        val oldContract = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.0
info:
  title: Sample API
  version: 0.1.9
paths:
  /data:
    post:
      summary: hello world
      description: test
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                data:
                  type: number
      responses:
        '200':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
""".trimIndent(), ""
        ).toFeature()

        val newContract = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.0
info:
  title: Sample API
  version: 0.1.9
paths:
  /data:
    post:
      summary: hello world
      description: test
      requestBody:
        content:
          application/json:
            schema:
              type: object
              required:
                - data2
              properties:
                data:
                  type: number
                data2:
                  type: number
      responses:
        '200':
          description: Says hello
          content:
            text/plain:
              schema:
                type: number
""".trimIndent(), ""
        ).toFeature()

        val result: Results = testBackwardCompatibility(oldContract, newContract)

        val reportText: String = result.report()
        println(reportText)

        assertThat(reportText).contains("REQUEST.BODY.data2")
        assertThat(reportText).contains("RESPONSE.BODY")
    }

    @Test
    fun `contract with multiple responses statuses should be backward compatible with itself`() {
        val contract = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.0
info:
  title: Sample API
  version: 0.1.9
paths:
  /data:
    post:
      summary: hello world
      description: test
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                data:
                  type: number
      responses:
        '200':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
        '400':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
""".trimIndent(), ""
        ).toFeature()

        val result: Results = testBackwardCompatibility(contract, contract)

        assertThat(result.successCount).isNotZero()
        assertThat(result.failureCount).isZero()
    }

    @Nested
    inner class FluffyBackwardCompatibilityErrors {
        val oldContract = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.0
info:
  title: Sample API
  version: 0.1.9
paths:
  /data:
    post:
      summary: hello world
      description: test
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                data:
                  type: number
      responses:
        '200':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
        '400':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
""".trimIndent(), ""
        ).toFeature()

        val newContract = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.0
info:
  title: Sample API
  version: 0.1.9
paths:
  /data2:
    post:
      summary: hello world
      description: test
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                data:
                  type: number
      responses:
        '200':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
        '400':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
""".trimIndent(), ""
        ).toFeature()

        val result: Results = testBackwardCompatibility(oldContract, newContract)

        val reportText: String = result.report().also { println(it) }

        @Test
        fun `fluffy backward compatibility errors should be eliminated`() {
            assertThat(reportText).doesNotContain(">> STATUS")
        }

        @Test
        fun `backward compatibility errors with fluff removed should have at least one error`() {
            assertThat(result.failureCount).isNotZero
        }
    }

    @Test
    fun `errors for a missing URL should show up`() {
        val oldContract = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.0
info:
  title: Sample API
  version: 0.1.9
paths:
  /data:
    post:
      summary: hello world
      description: test
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                data:
                  type: number
      responses:
        '200':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
        '400':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
""".trimIndent(), ""
        ).toFeature()

        val newContract = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.0
info:
  title: Sample API
  version: 0.1.9
paths:
  /data2:
    post:
      summary: hello world
      description: test
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                data:
                  type: string
      responses:
        '200':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
        '400':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
""".trimIndent(), ""
        ).toFeature()

        val result = testBackwardCompatibility(oldContract, newContract)
        val reportText = result.report()

        assertThat(reportText).contains("POST /data")
    }

    @Test
    fun `errors for a missing URL should show up even when there are deep matches for other URLs`() {
        val oldContract = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.0
info:
  title: Sample API
  version: 0.1.9
paths:
  /data:
    post:
      summary: hello world
      description: test
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                data:
                  type: number
      responses:
        '200':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
        '400':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
  /data2:
    post:
      summary: hello world
      description: test
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                data:
                  type: number
      responses:
        '200':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
""".trimIndent(), ""
        ).toFeature()

        val newContract = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.0
info:
  title: Sample API
  version: 0.1.9
paths:
  /data:
    post:
      summary: hello world
      description: test
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                data:
                  type: string
      responses:
        '200':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
        '400':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
""".trimIndent(), ""
        ).toFeature()

        val result = testBackwardCompatibility(oldContract, newContract)
        val reportText = result.report()

        println(reportText)

        assertThat(reportText).contains("POST /data")
        assertThat(reportText).contains("POST /data2")
    }

    @Test
    fun `backward compatibility errors with fluff removed should show deep mismatch errors`() {
        val oldContract = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.0
info:
  title: Sample API
  version: 0.1.9
paths:
  /data:
    post:
      summary: hello world
      description: test
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                data:
                  type: number
      responses:
        '200':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
        '400':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
  /data2:
    post:
      summary: hello world
      description: test
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                data:
                  type: number
  /data3:
    post:
      summary: hello world
      description: test
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                data:
                  type: number
      responses:
        '200':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
""".trimIndent(), ""
        ).toFeature()

        val newContract = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.0
info:
  title: Sample API
  version: 0.1.9
paths:
  /data:
    post:
      summary: hello world
      description: test
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                data:
                  type: string
      responses:
        '200':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
        '400':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
  /data2:
    post:
      summary: hello world
      description: test
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                data:
                  type: number
      responses:
        '200':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
""".trimIndent(), ""
        ).toFeature()

        val result = testBackwardCompatibility(oldContract, newContract)
        val reportText = result.report().also { println(it) }

        assertThat(reportText).contains("API: POST /data -> 200")
        assertThat(reportText).contains("API: POST /data -> 400")
        assertThat(reportText).doesNotContain("API: POST /data2 -> 200")
        assertThat(reportText).contains("API: POST /data3 -> 200")
    }
}

private fun String.openAPIToContract(): Feature {
    return OpenApiSpecification.fromYAML(this, "").toFeature()
}
