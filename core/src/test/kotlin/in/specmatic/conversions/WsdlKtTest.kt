package `in`.specmatic.conversions

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.parseGherkinStringToFeature
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.value.Value
import `in`.specmatic.stub.HttpStub
import `in`.specmatic.test.TestExecutor
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate
import java.io.File
import java.net.URI

class WsdlKtTest {

    @BeforeEach
    fun `setup`() {
        val wsdlContent = """
            <?xml version="1.0"?>
            <wsdl:definitions xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/"
                              xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
                              xmlns:qr="http://specmatic.in/SOAPService/"
                              targetNamespace="http://specmatic.in/SOAPService/">
                <wsdl:types>
                    <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                                targetNamespace="http://specmatic.in/SOAPService/">
                        <xsd:element name="SimpleRequest" type="xsd:string"/>
                        <xsd:element name="SimpleResponse" type="xsd:string"/>
                    </xsd:schema>
                </wsdl:types>

                <wsdl:message name="simpleInputMessage">
                    <wsdl:part name="simpleInputPart" element="qr:SimpleRequest"/>
                </wsdl:message>
                <wsdl:message name="simpleOutputMessage">
                    <wsdl:part name="simpleOutputPart" element="qr:SimpleResponse"/>
                </wsdl:message>

                <wsdl:portType name="simplePortType">
                    <wsdl:operation name="SimpleOperation">
                        <wsdl:input name="simpleInput"
                                    message="qr:simpleInputMessage"/>
                        <wsdl:output name="simpleOutput"
                                     message="qr:simpleOutputMessage"/>
                    </wsdl:operation>
                </wsdl:portType>

                <wsdl:binding name="simpleBinding" type="qr:simplePortType">
                    <soap:binding transport="http://schemas.xmlsoap.org/soap/http"/>
                    <wsdl:operation name="SimpleOperation">
                        <soap:operation
                                soapAction="http://specmatic.in/SOAPService/SimpleOperation"/>
                        <wsdl:input name="simpleInput">
                            <soap:body use="literal"/>
                        </wsdl:input>
                        <wsdl:output name="simpleOutput">
                            <soap:body use="literal"/>
                        </wsdl:output>
                    </wsdl:operation>
                </wsdl:binding>

                <wsdl:service name="simpleService">
                    <wsdl:port name="simplePort" binding="qr:simpleBinding">
                        <soap:address
                                location="http://specmatic.in/SOAPService/SimpleSOAP"/>
                    </wsdl:port>
                </wsdl:service>

            </wsdl:definitions>
        """.trimIndent()

        val wsdlFile = File("test.wsdl")
        wsdlFile.createNewFile()
        wsdlFile.writeText(wsdlContent)
    }

    @Test
    fun `should create stub from gherkin that includes wsdl`() {
        val wsdlSpec = """
Feature: Hello world

Background:
  Given wsdl test.wsdl           
  
Scenario: test request returns test response
  When POST /SOAPService/SimpleSOAP
  And request-header SOAPAction "http://specmatic.in/SOAPService/SimpleOperation"
  And request-body <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleRequest>test request</SimpleRequest></soapenv:Body></soapenv:Envelope>
  Then status 200
  And response-body <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleResponse>test response</SimpleResponse></soapenv:Body></soapenv:Envelope>
        """.trimIndent()

        val wsdlFeature = parseGherkinStringToFeature(wsdlSpec)
        HttpStub(wsdlFeature).use { stub ->
            val soapRequest =
                """<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleRequest>WKTGM</SimpleRequest></soapenv:Body></soapenv:Envelope>"""

            val headers = HttpHeaders()
            headers.add("SOAPAction", """"http://specmatic.in/SOAPService/SimpleOperation"""")
            val response = RestTemplate().exchange(
                URI.create("http://localhost:9000/SOAPService/SimpleSOAP"),
                HttpMethod.POST,
                HttpEntity(soapRequest, headers),
                String::class.java
            )
            Assertions.assertThat(response.statusCodeValue).isEqualTo(200)
            Assertions.assertThat(response.body)
                .matches("""<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleResponse>[A-Z]*</SimpleResponse></soapenv:Body></soapenv:Envelope>""")

            val testRequest =
                """<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleRequest>test request</SimpleRequest></soapenv:Body></soapenv:Envelope>"""
            val testResponse = RestTemplate().exchange(
                URI.create("http://localhost:9000/SOAPService/SimpleSOAP"),
                HttpMethod.POST,
                HttpEntity(testRequest, headers),
                String::class.java
            )
            Assertions.assertThat(testResponse.body)
                .matches("""<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleResponse>test response</SimpleResponse></soapenv:Body></soapenv:Envelope>""")
        }
    }

    @Test
    fun `should create test from gherkin that includes wsdl`() {
        val wsdlSpec = """
Feature: Hello world

Background:
  Given wsdl test.wsdl           
  
Scenario: test request returns test response
  When POST /SOAPService/SimpleSOAP
  And request-header SOAPAction "http://specmatic.in/SOAPService/SimpleOperation"
  And request-body <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleRequest>test request</SimpleRequest></soapenv:Body></soapenv:Envelope>
  Then status 200
  And response-body <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleResponse>test response</SimpleResponse></soapenv:Body></soapenv:Envelope>
        """.trimIndent()

        val wsdlFeature = parseGherkinStringToFeature(wsdlSpec)
        val results = wsdlFeature.executeTests(
            object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    Assertions.assertThat(request.path).matches("""/SOAPService/SimpleSOAP""")
                    Assertions.assertThat(request.headers["SOAPAction"]).isEqualTo(""""http://specmatic.in/SOAPService/SimpleOperation"""")
                    val responseBody = when {
                        request.bodyString.contains("test request") -> """<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleResponse>test response</SimpleResponse></soapenv:Body></soapenv:Envelope>"""
                        else -> """<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleResponse>WSDL</SimpleResponse></soapenv:Body></soapenv:Envelope>"""
                    }
                    return HttpResponse(200, responseBody, mapOf())
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            }
        )

        assertTrue(results.success(), results.report())
    }

    @Test
    fun `should report error in test with both OpenAPI and Gherkin scenario names`() {
        val wsdlSpec = """
Feature: Hello world

Background:
  Given wsdl test.wsdl           
  
Scenario: test request returns test response
  When POST /SOAPService/SimpleSOAP
  And request-header SOAPAction "http://specmatic.in/SOAPService/SimpleOperation"
  And request-body <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleRequest>test request</SimpleRequest></soapenv:Body></soapenv:Envelope>
  Then status 200
  And response-body <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleResponse>test response</SimpleResponse></soapenv:Body></soapenv:Envelope>
        """.trimIndent()

        val wsdlFeature = parseGherkinStringToFeature(wsdlSpec)
        val results = wsdlFeature.executeTests(
            object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    return HttpResponse(200, "", mapOf())
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            }
        )

        assertFalse(results.success(), results.report())
        Assertions.assertThat(results.report()).isEqualTo("""
            In scenario "test request returns test response"
            >> RESPONSE.BODY.Envelope

            Expected xml, got string

            In scenario "SimpleOperation"
            >> RESPONSE.BODY.Envelope

            Expected xml, got string

            In scenario "SimpleOperation"
            >> RESPONSE.BODY.Envelope

            Expected xml, got string
        """.trimIndent())
    }

    @Test
    fun `should throw error when request in Gherkin scenario does not match included wsdl spec`() {
        val wsdlSpec = """
Feature: Hello world

Background:
  Given wsdl test.wsdl           
  
Scenario: request not matching wsdl
  When POST /SOAPService/SimpleSOAP
  And request-header SOAPAction "http://specmatic.in/SOAPService/AnotherOperation"
  And request-body <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleRequest>test request</SimpleRequest></soapenv:Body></soapenv:Envelope>
  Then status 200
        """.trimIndent()

        assertThatThrownBy {
            parseGherkinStringToFeature(wsdlSpec)
        }.satisfies {
            assertThat(it.message).isEqualTo("""Scenario: "request not matching wsdl" request is not as per included wsdl / OpenApi spec""")
        }
    }

    @AfterEach
    fun `teardown`() {
        File("test.wsdl").delete()
    }
}