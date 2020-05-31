package run.qontract.core

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import run.qontract.conversions.postmanCollectionToGherkin
import run.qontract.stub.HttpStub

class PostmanKtTests {
    @Test
    fun `Postman to Qontract with raw format request body`() {
        val postmanContent = """{
	"info": {
		"_postman_id": "8bd0c42a-983a-492e-99bd-a2f1936bc02e",
		"name": "Zuul",
		"schema": "https://schema.getpostman.com/json/collection/v2.0.0/collection.json"
	},
	"item": [
		{
			"name": "With no body or params",
			"request": {
				"method": "GET",
				"header": [],
				"url": {
					"raw": "http://localhost:9000/stuff",
					"protocol": "http",
					"host": [
						"localhost"
					],
					"port": "9000",
					"path": [
						"stuff"
					]
				}
			},
			"response": []
		},
		{
			"name": "With JSON body",
			"request": {
				"method": "POST",
				"header": [],
				"body": {
					"mode": "raw",
					"raw": "{\"data\": \"value\"}",
					"options": {
						"raw": {
							"language": "json"
						}
					}
				},
				"url": {
					"raw": "http://localhost:9000/stuff",
					"protocol": "http",
					"host": [
						"localhost"
					],
					"port": "9000",
					"path": [
						"stuff"
					]
				}
			},
			"response": []
		},
		{
			"name": "With query",
			"request": {
				"method": "GET",
				"header": [],
				"url": {
					"raw": "http://localhost:9000/stuff?one=1",
					"protocol": "http",
					"host": [
						"localhost"
					],
					"port": "9000",
					"path": [
						"stuff"
					],
					"query": [
						{
							"key": "one",
							"value": "1"
						}
					]
				}
			},
            "response": [
				{
					"name": "Square Of A Number 2",
					"originalRequest": {
						"method": "POST",
						"header": [],
						"body": {
							"mode": "raw",
							"raw": "10",
							"options": {
								"raw": {
									"language": "text"
								}
							}
						},
						"url": {
							"raw": "http://localhost:9000/square",
							"protocol": "http",
							"host": [
								"localhost"
							],
							"port": "9000",
							"path": [
								"square"
							]
						}
					},
					"status": "OK",
					"code": 200,
					"_postman_previewlanguage": "plain",
					"header": [
						{
							"key": "Vary",
							"value": "Origin"
						},
						{
							"key": "X-Qontract-Result",
							"value": "success"
						},
						{
							"key": "Content-Length",
							"value": "3"
						},
						{
							"key": "Content-Type",
							"value": "text/plain"
						},
						{
							"key": "Connection",
							"value": "1000"
						}
					],
					"cookie": [],
					"body": "100"
				}
            ]
        },
		{
			"name": "With form fields",
			"request": {
				"method": "POST",
				"header": [],
				"body": {
					"mode": "urlencoded",
					"urlencoded": [
						{
							"key": "field1",
							"value": "10",
							"type": "text"
						}
					]
				},
				"url": {
					"raw": "http://localhost:9000/stuff",
					"protocol": "http",
					"host": [
						"localhost"
					],
					"port": "9000",
					"path": [
						"stuff"
					]
				}
			},
			"response": []
		},
		{
			"name": "With form data",
			"request": {
				"method": "POST",
				"header": [],
				"body": {
					"mode": "formdata",
					"formdata": [
						{
							"key": "part1",
							"value": "10",
							"type": "text"
						}
					]
				},
				"url": {
					"raw": "http://localhost:9000/stuff",
					"protocol": "http",
					"host": [
						"localhost"
					],
					"port": "9000",
					"path": [
						"stuff"
					]
				}
			},
			"response": []
		}
	],
	"protocolProfileBehavior": {}
}"""

        val (gherkinString, stubs) = postmanCollectionToGherkin(postmanContent)

        println(gherkinString)

        validate(gherkinString, stubs)
    }

    private fun validate(gherkinString: String, stubs: List<NamedStub>) {
        val behaviour = Feature(gherkinString)
        val cleanedUpStubs = stubs.map { it.stub }.map { it.copy(response = dropContentAndCORSResponseHeaders(it.response) ) }
        for(stub in cleanedUpStubs) {
            behaviour.matchingStubResponse(stub)
        }
    }

    companion object {
        var stub: HttpStub? = null

        @BeforeAll
        @JvmStatic
        fun setup() {
            val gherkin = """Feature: Number API

Scenario: With no body or params
  When GET /stuff
  Then status 200
  And response-body (number)

Scenario: With query
  When GET /stuff?one=(string)
  Then status 200
  And response-body (number)

Scenario: With JSON body
  Given type Request
    | data | (string) |
  When POST /stuff
  And request-body (Request)
  Then status 200
  And response-body (number)

Scenario: With form fields
  When POST /stuff
  And form-field field1 (string)
  Then status 200
  And response-body (number)

Scenario: With form data
  When POST /stuff
  And request-part part1 (string)
  Then status 200
  And response-body (number)
"""

            val contractBehaviour = Feature(gherkin)
            stub = HttpStub(contractBehaviour)
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            stub?.close()
        }
    }
}
