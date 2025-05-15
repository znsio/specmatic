package io.specmatic.core.filters

import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.Scenario
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class HttpFilterContextTest {
    @ParameterizedTest
    @MethodSource("provideTestParameters")
    fun `test includes method with various parameters`(
        key: String,
        value: String,
        expected: Boolean,
        scenario: Scenario
    ) {
        val httpFilterContext = HttpFilterContext(scenario)
        assertThat(httpFilterContext.includes(key, listOf(value))).isEqualTo(expected)
    }

    @ParameterizedTest
    @MethodSource("provideTestParametersForCompare")
    fun `test compare method with various parameters`(
        key: String,
        operator: String,
        value: String,
        expected: Boolean,
        scenario: Scenario
    ) {
        val httpFilterContext = HttpFilterContext(scenario)
        assertThat(httpFilterContext.compare(key, operator, value)).isEqualTo(expected)
    }

    companion object {

        private val simpleSpecWithOneGetUrl = OpenApiSpecification.fromYAML(
            """
openapi: "3.0.1"
info:
  version: "1"
paths:
  /foo:
    get:
      parameters:
        - name: type
          schema:
            type: string
          in: query
          required: true
        - name: authentication
          schema:
            type: string
          in: header
          required: false
      responses:
        "200":
          content:
            application/json:
              schema:
                type: array
                items:
                  type:
                    string
              examples:
                QUERY_SUCCESS:
                  value: ["one", "two"]
            application/vnd.acme+foo:
              schema:
                type: array
                vendor_items:
                  type:
                    string
              examples:
                QUERY_SUCCESS:
                  value: ["one", "two"]
        """.trim(), ""
        ).toFeature()


        private val usersGetAPI = OpenApiSpecification.fromYAML(
            """
openapi: "3.0.1"
info:
  version: "1"
paths:
  /users/{id}:
    get:
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
      responses:
        "200":
          content:
            application/json:
              schema:
                type: object
                properties:
                  id:
                    type: integer
                  name:
                    type: string
        "400":
          content:
            application/json:
              schema:
                type: object
                properties:
                  error:
                    type: string
                """.trim(), ""
        ).toFeature()

        @JvmStatic
        fun provideTestParameters() = listOf(
            // paths are case-sensitive
            Arguments.of("PATH", "/foo", true, simpleSpecWithOneGetUrl.scenarios[0]),
            Arguments.of("PATH", "/FOO", false, simpleSpecWithOneGetUrl.scenarios[0]),
            Arguments.of("PATH", "/non-existant", false, simpleSpecWithOneGetUrl.scenarios[0]),

            // path globs
            Arguments.of("PATH", "/*", true, simpleSpecWithOneGetUrl.scenarios[0]),
            Arguments.of("PATH", "/users/*", true, usersGetAPI.scenarios[0]),
            // TODO - validate with Naresh?
            Arguments.of("PATH", "/users/{id}", false, usersGetAPI.scenarios[0]),
            // TODO - validate with Naresh? type in spec is integer, so this should work?
            Arguments.of("PATH", "/users/(id:integer)", false, usersGetAPI.scenarios[0]),
            Arguments.of("PATH", "/users/(id:number)", true, usersGetAPI.scenarios[0]),
            // params are case-sensitive
            Arguments.of("PATH", "/users/(ID:integer)", false, usersGetAPI.scenarios[0]),

            // TODO - validate with Naresh?
            Arguments.of("PATH", "/users/(id: integer)", false, usersGetAPI.scenarios[0]),
            Arguments.of("PATH", "/users/bob", false, usersGetAPI.scenarios[0]),

            // TODO - revisit this?
            Arguments.of("PATH", "/foo?bar", false, simpleSpecWithOneGetUrl.scenarios[0]),

            // header keys are case-insensitive
            Arguments.of("PARAMETERS.HEADER", "AUTHenTICATION", true, simpleSpecWithOneGetUrl.scenarios[0]),

            Arguments.of("PARAMETERS.HEADER", "content-type", false, simpleSpecWithOneGetUrl.scenarios[0]),
            Arguments.of("PARAMETERS.HEADER", "non-existent-header", false, simpleSpecWithOneGetUrl.scenarios[0]),

            // query param keys are case-sensitive
            Arguments.of("PARAMETERS.QUERY", "type", true, simpleSpecWithOneGetUrl.scenarios[0]),
            Arguments.of("PARAMETERS.QUERY", "TYPE", false, simpleSpecWithOneGetUrl.scenarios[0]),

            // methods are case-insensitive
            Arguments.of("METHOD", "GET", true, simpleSpecWithOneGetUrl.scenarios[0]),
            Arguments.of("METHOD", "Get", true, simpleSpecWithOneGetUrl.scenarios[0]),

            // status
            Arguments.of("STATUS", "200", true, simpleSpecWithOneGetUrl.scenarios[0]),
            Arguments.of("STATUS", "201", false, simpleSpecWithOneGetUrl.scenarios[0]),

            // example name
            Arguments.of("EXAMPLE-NAME", "QUERY_SUCCESS", true, simpleSpecWithOneGetUrl.scenarios[0]),
            Arguments.of("EXAMPLE-NAME", "NON_EXISTENT_EXAMPLE", false, simpleSpecWithOneGetUrl.scenarios[0]),

            // response content type for get requests
            Arguments.of("RESPONSE.CONTENT-TYPE", "application/*", true, simpleSpecWithOneGetUrl.scenarios[0]),
            Arguments.of("RESPONSE.CONTENT-TYPE", "application/json", true, simpleSpecWithOneGetUrl.scenarios[0]),
            Arguments.of("RESPONSE.CONTENT-TYPE", "application/j*", false, simpleSpecWithOneGetUrl.scenarios[0]),
            Arguments.of("RESPONSE.CONTENT-TYPE", "application/json*", false, simpleSpecWithOneGetUrl.scenarios[0]),
            Arguments.of("RESPONSE.CONTENT-TYPE", "application/*+json", true, simpleSpecWithOneGetUrl.scenarios[0]),
            Arguments.of("RESPONSE.CONTENT-TYPE", "json", true, simpleSpecWithOneGetUrl.scenarios[0]),
            Arguments.of("RESPONSE.CONTENT-TYPE", "application/*json", true, simpleSpecWithOneGetUrl.scenarios[0]),
            Arguments.of("RESPONSE.CONTENT-TYPE", "application/foo", false, simpleSpecWithOneGetUrl.scenarios[0]),
        )

        @JvmStatic
        fun provideTestParametersForCompare() = listOf(
            Arguments.of("STATUS", ">", "200", false, simpleSpecWithOneGetUrl.scenarios[0]),
            Arguments.of("STATUS", "<", "200", false, simpleSpecWithOneGetUrl.scenarios[0]),
            Arguments.of("STATUS", ">=", "200", true, simpleSpecWithOneGetUrl.scenarios[0]),
            Arguments.of("STATUS", "<=", "200", true, simpleSpecWithOneGetUrl.scenarios[0]),

            // 200 status code for /users/{id} endpoint
            Arguments.of("STATUS", ">", "200", false, usersGetAPI.scenarios[0]),
            Arguments.of("STATUS", "<", "200", false, usersGetAPI.scenarios[0]),
            Arguments.of("STATUS", ">=", "200", true, usersGetAPI.scenarios[0]),
            Arguments.of("STATUS", "<=", "200", true, usersGetAPI.scenarios[0]),

            // 400 status code for /users/{id} path
            Arguments.of("STATUS", ">", "200", true, usersGetAPI.scenarios[1]),
            Arguments.of("STATUS", "<", "200", false, usersGetAPI.scenarios[1]),
            Arguments.of("STATUS", ">=", "200", true, usersGetAPI.scenarios[1]),
            Arguments.of("STATUS", "<=", "200", false, usersGetAPI.scenarios[1]),
        )
    }
}
