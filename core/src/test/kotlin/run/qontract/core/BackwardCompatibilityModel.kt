package run.qontract.core

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import run.qontract.backwardCompatibleWith
import run.qontract.core.pattern.parsedJSONStructure
import run.qontract.notBackwardCompatibleWith
import run.qontract.stubShouldBreak
import run.qontract.stubShouldNotBreak

class BackwardCompatibilityModel {
    val oldContract = """
Feature: User API
  Scenario: Add user
    Given json User
    | name | (string) |
    And json Status
    | status | (string) |
    When POST /user
    And request-body (User)
    Then status 200
    And response-body (Status)
""".trimIndent()

    @Test
    fun `adding non-optional key to the request body is backward incompatible`() {
        val newContract = """
Feature: User API
  Scenario: Add user
    Given json User
    | name    | (string) |
    | address | (string) |
    And json Status
    | status | (string) |
    When POST /user
    And request-body (User)
    Then status 200
    And response-body success
    And response-body (Status)
""".trimIndent()

        newContract notBackwardCompatibleWith oldContract

        val stub = TestHttpStubData(
                stubRequest = HttpRequest("POST", "/user", body = parsedJSONStructure("""{"name": "John Doe"}""")),
                stubResponse = HttpResponse.OK(parsedJSONStructure("""{"status": "success"}""")))
        stub.shouldWorkWith(oldContract)
        stub.shouldBreakWith(newContract)
    }

    @Test
    fun `adding optional key in the request body is backward compatible`() {
        val newContract = """
Feature: User API
  Scenario: Add user
    Given json User
    | name     | (string) |
    | address? | (string) |
    And json Status
    | status | (string) |
    When POST /user
    And request-body (User)
    Then status 200
    And response-body success
    And response-body (Status)
""".trimIndent()

        newContract backwardCompatibleWith oldContract

        val request = HttpRequest("POST","/user", body = parsedJSONStructure("""{"name": "John Doe"}"""))
        val response = HttpResponse.OK(parsedJSONStructure("""{"status": "success"}"""))

        stubShouldNotBreak(request, response, oldContract, newContract)
    }

    @Test
    fun `make type optional in request body is backward compatible`() {
        val newContract = """
Feature: User API
  Scenario: Add user
    Given json User
    | name     | (string?) |
    And json Status
    | status | (string) |
    When POST /user
    And request-body (User)
    Then status 200
    And response-body success
    And response-body (Status)
""".trimIndent()

        newContract backwardCompatibleWith oldContract

        val request = HttpRequest("POST","/user", body = parsedJSONStructure("""{"name": "John Doe"}"""))
        val response = HttpResponse.OK(parsedJSONStructure("""{"status": "success"}"""))
        stubShouldNotBreak(request, response, oldContract, newContract)
    }

    @Test
    fun `change number in string to string in request body is backward compatible`() {
        val oldContract = """
Feature: User API
  Scenario: Add user
    Given json User
    | id | (number in string) |
    And json Status
    | status | (string) |
    When POST /user
    And request-body (User)
    Then status 200
    And response-body success
    And response-body (Status)
        """.trimIndent()

        val newContract = """
Feature: User API
  Scenario: Add user
    Given json User
    | id | (string) |
    And json Status
    | status | (string) |
    When POST /user
    And request-body (User)
    Then status 200
    And response-body success
    And response-body (Status)
""".trimIndent()

        newContract backwardCompatibleWith oldContract

        val request = HttpRequest("POST","/user", body = parsedJSONStructure("""{"id": "10"}"""))
        val response = HttpResponse.OK(parsedJSONStructure("""{"status": "success"}"""))
        stubShouldNotBreak(request, response, oldContract, newContract)
    }

    @Test
    fun `change from string to number in string in request body is backward incompatible`() {
        val oldContract = """
Feature: User API
  Scenario: Add user
    Given json User
    | id | (string) |
    And json Status
    | status | (string) |
    When POST /user
    And request-body (User)
    Then status 200
    And response-body success
    And response-body (Status)
        """.trimIndent()

        val newContract = """
Feature: User API
  Scenario: Add user
    Given json User
    | id | (number in string) |
    And json Status
    | status | (string) |
    When POST /user
    And request-body (User)
    Then status 200
    And response-body success
    And response-body (Status)
""".trimIndent()

        newContract notBackwardCompatibleWith oldContract

        val request = HttpRequest("POST","/user", body = parsedJSONStructure("""{"id": "abc"}"""))
        val response = HttpResponse.OK(parsedJSONStructure("""{"status": "success"}"""))
        stubShouldBreak(request, response, oldContract, newContract)
    }

    @Disabled
    @Test
    fun `adding a key to the response body is backward compatible`() {
        val newContract = """
Feature: User API
  Scenario: Add user
    Given json User
    | name | (string) |
    And json Status
    | status | (string) |
    | data   | (string) |
    When POST /user
    And request-body (User)
    Then status 200
    And response-body (Status)
""".trimIndent()

        newContract backwardCompatibleWith oldContract

        val request = HttpRequest("POST","/user", body = parsedJSONStructure("""{"name": "John Doe"}"""))
        val response = HttpResponse.OK(parsedJSONStructure("""{"status": "success"}"""))
        stubShouldNotBreak(request, response, oldContract, newContract)
    }

    @Test
    fun `adding an optional key to the response body is backward compatible`() {
        val newContract = """
Feature: User API
  Scenario: Add user
    Given json User
    | name | (string) |
    And json Status
    | status | (string) |
    | data?  | (string) |
    When POST /user
    And request-body (User)
    Then status 200
    And response-body (Status)
""".trimIndent()

        newContract backwardCompatibleWith oldContract

        val request = HttpRequest("POST","/user", body = parsedJSONStructure("""{"name": "John Doe"}"""))
        val response = HttpResponse.OK(parsedJSONStructure("""{"status": "success"}"""))
        stubShouldNotBreak(request, response, oldContract, newContract)
    }
}
