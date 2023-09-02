package `in`.specmatic.conversions

import `in`.specmatic.Utils.readTextResource
import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.parseGherkinStringToFeature
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import `in`.specmatic.core.value.XMLNode
import `in`.specmatic.core.value.toXML
import `in`.specmatic.core.wsdl.parser.WSDL
import `in`.specmatic.mock.ScenarioStub
import `in`.specmatic.stub.HttpStub
import `in`.specmatic.test.TestExecutor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate
import java.io.File
import java.net.URI
import java.util.function.Consumer

class WsdlKtTest {

    @BeforeEach
    fun setup() {
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

        val wsdlContentWithOptionalAttributes = """
            <wsdl:definitions xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/"
                              xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
                              xmlns:qr="http://specmatic.in/SOAPService/"
                              targetNamespace="http://specmatic.in/SOAPService/">
                <wsdl:types>
                    <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema" targetNamespace="http://specmatic.in/SOAPService/">
                        <xsd:element name="SimpleResponse" type="xsd:string"/>
                        <xsd:element name="Person">
                            <xsd:complexType>
                                <xsd:sequence>
                                    <xsd:element name="Id" type="xsd:integer" />
                                    <xsd:element name="Name" type="xsd:string" />
                                </xsd:sequence>
                                <xs:attribute name="age" type="xs:integer"></xs:attribute>
                            </xsd:complexType>
                        </xsd:element>
                    </xsd:schema>
                </wsdl:types>

                <wsdl:message name="simpleInputMessage">
                    <wsdl:part name="simpleInputPart" element="qr:Person"/>
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
        val wsdlWithOptionalAttributesFile = File("test_with_optional_attributes.wsdl")
        wsdlWithOptionalAttributesFile.createNewFile()
        wsdlWithOptionalAttributesFile.writeText(wsdlContentWithOptionalAttributes)

        val wsdlContentWithMandatoryAttributes = """
            <wsdl:definitions xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/"
                              xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
                              xmlns:qr="http://specmatic.in/SOAPService/"
                              targetNamespace="http://specmatic.in/SOAPService/">
                <wsdl:types>
                    <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema" targetNamespace="http://specmatic.in/SOAPService/">
                        <xsd:element name="SimpleResponse" type="xsd:string"/>
                        <xsd:element name="Person">
                            <xsd:complexType>
                                <xsd:sequence>
                                    <xsd:element name="Id" type="xsd:integer" />
                                    <xsd:element name="Name" type="xsd:string" />
                                </xsd:sequence>
                                <xs:attribute name="age" type="xs:integer" use="required"></xs:attribute>
                            </xsd:complexType>
                        </xsd:element>
                    </xsd:schema>
                </wsdl:types>

                <wsdl:message name="simpleInputMessage">
                    <wsdl:part name="simpleInputPart" element="qr:Person"/>
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
        val wsdlWithMandatoryAttributesFile = File("test_with_mandatory_attributes.wsdl")
        wsdlWithMandatoryAttributesFile.createNewFile()
        wsdlWithMandatoryAttributesFile.writeText(wsdlContentWithMandatoryAttributes)
    }

    @Disabled
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
        HttpStub(wsdlFeature).use {
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
            assertThat(response.statusCodeValue).isEqualTo(200)
            assertThat(response.body)
                .matches("""<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleResponse>[A-Z]*</SimpleResponse></soapenv:Body></soapenv:Envelope>""")

            val testRequest =
                """<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleRequest>test request</SimpleRequest></soapenv:Body></soapenv:Envelope>"""
            val testResponse = RestTemplate().exchange(
                URI.create("http://localhost:9000/SOAPService/SimpleSOAP"),
                HttpMethod.POST,
                HttpEntity(testRequest, headers),
                String::class.java
            )
            assertThat(testResponse.body)
                .matches("""<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleResponse>test response</SimpleResponse></soapenv:Body></soapenv:Envelope>""")
        }
    }

    @Test
    fun `should be able to stub a fault`() {
        val wsdlSpec = """
Feature: Hello world

Background:
  Given wsdl test.wsdl           
  
        """.trimIndent()

        val wsdlFeature = parseGherkinStringToFeature(wsdlSpec)

        HttpStub(wsdlFeature).use { stub ->
            val request = HttpRequest(
                "POST",
                "/SOAPService/SimpleSOAP",
                headers = mapOf(
                    "Content-Type" to "text/xml",
                    "SOAPAction" to """http://specmatic.in/SOAPService/SimpleOperation"""
                ),
                body = StringValue("<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"><soapenv:Header/><soapenv:Body><SimpleRequest>test request</SimpleRequest></soapenv:Body></soapenv:Envelope>")
            )

            val response = HttpResponse(
                status = 200,
                body = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"><soapenv:Header/><soapenv:Body><soapenv:Fault></soapenv:Fault></soapenv:Body></soapenv:Envelope>"
            )

            stub.setExpectation(ScenarioStub(request, response))
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
                    assertThat(request.path).matches("""/SOAPService/SimpleSOAP""")
                    assertThat(listOf(
                        """http://specmatic.in/SOAPService/SimpleOperation""",
                        """"http://specmatic.in/SOAPService/SimpleOperation""""))
                        .contains(request.headers["SOAPAction"])
                    val responseBody = when {
                        request.bodyString.contains("test request") ->
                            """<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleResponse>test response</SimpleResponse></soapenv:Body></soapenv:Envelope>"""
                        else ->
                            """<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleResponse>WSDL</SimpleResponse></soapenv:Body></soapenv:Envelope>"""
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
    fun `should create test for mandatory attribute in complex elements with examples`() {
        val id = 3
        val name = "John Doe"
        val age = 33
        val wsdlSpec = """
Feature: WSDL Attribute Test

Background:
  Given wsdl test_with_mandatory_attributes.wsdl           
  
Scenario: test spec with mandatory attributes with examples
  When POST /SOAPService/SimpleSOAP
  And request-header SOAPAction "http://specmatic.in/SOAPService/SimpleOperation"
  And request-body <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><qr:Person age="11"><qr:Id>1</qr:Id><qr:Name>James Smith</qr:Name></qr:Person></soapenv:Body></soapenv:Envelope>
  Then status 200
  And response-body <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleResponse>test response</SimpleResponse></soapenv:Body></soapenv:Envelope>
  Examples:
      | (REQUEST-BODY) |
      | <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><qr:Person age="$age"><qr:Id>$id</qr:Id><qr:Name>$name</qr:Name></qr:Person></soapenv:Body></soapenv:Envelope> |
        """.trimIndent()
        val wsdlFeature = parseGherkinStringToFeature(wsdlSpec)
        var countOfTestsWithAgeAttributeSetFromExamples = 0
        val results = wsdlFeature.executeTests(
            object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    assertThat(request.path).matches("""/SOAPService/SimpleSOAP""")
                    assertThat(listOf(
                        """http://specmatic.in/SOAPService/SimpleOperation""",
                        """"http://specmatic.in/SOAPService/SimpleOperation""""))
                        .contains(request.headers["SOAPAction"])
                    if (requestContainsPersonNodeWithAge(request, age)) countOfTestsWithAgeAttributeSetFromExamples++
                    val responseBody = """<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleResponse>WSDL</SimpleResponse></soapenv:Body></soapenv:Envelope>"""
                    return HttpResponse(200, responseBody, mapOf())
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            }
        )
        assertTrue(results.success(), results.report())
        assertThat(countOfTestsWithAgeAttributeSetFromExamples).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `should create test for mandatory attribute in complex elements without examples`() {
        val wsdlSpec = """
Feature: WSDL Attribute Test

Background:
  Given wsdl test_with_mandatory_attributes.wsdl           
  
Scenario: test spec with mandatory attributes without examples
  When POST /SOAPService/SimpleSOAP
  And request-header SOAPAction "http://specmatic.in/SOAPService/SimpleOperation"
  And request-body <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><qr:Person age="11"><qr:Id>1</qr:Id><qr:Name>James Smith</qr:Name></qr:Person></soapenv:Body></soapenv:Envelope>
  Then status 200
  And response-body <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleResponse>test response</SimpleResponse></soapenv:Body></soapenv:Envelope>
        """.trimIndent()

        val wsdlFeature = parseGherkinStringToFeature(wsdlSpec)
        var countOfTestsWithAgeAttributeSetToRandomValue = 0
        val results = wsdlFeature.executeTests(
            object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    assertThat(request.path).matches("""/SOAPService/SimpleSOAP""")
                    assertThat(listOf(
                        """http://specmatic.in/SOAPService/SimpleOperation""",
                        """"http://specmatic.in/SOAPService/SimpleOperation""""))
                        .contains(request.headers["SOAPAction"])
                    val responseBody = """<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleResponse>WSDL</SimpleResponse></soapenv:Body></soapenv:Envelope>"""
                    if (requestContainsPersonNodeWithRandomAge(request)) countOfTestsWithAgeAttributeSetToRandomValue++
                    return HttpResponse(200, responseBody, mapOf())
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            }
        )
        assertTrue(results.success(), results.report())
        assertThat(countOfTestsWithAgeAttributeSetToRandomValue).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `should create test for optional attribute in complex elements with examples`() {
        val id = 4
        val name = "Jane Doe"
        val age = 44
        val wsdlSpec = """
Feature: WSDL Attribute Test

Background:
  Given wsdl test_with_optional_attributes.wsdl           
  
Scenario: test spec with optional attributes without examples
  When POST /SOAPService/SimpleSOAP
  And request-header SOAPAction "http://specmatic.in/SOAPService/SimpleOperation"
  And request-body <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><qr:Person age="11"><qr:Id>1</qr:Id><qr:Name>James Smith</qr:Name></qr:Person></soapenv:Body></soapenv:Envelope>
  Then status 200
  And response-body <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleResponse>test response</SimpleResponse></soapenv:Body></soapenv:Envelope>
  Examples:
      | (REQUEST-BODY) |
      | <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><qr:Person age="$age"><qr:Id>$id</qr:Id><qr:Name>$name</qr:Name></qr:Person></soapenv:Body></soapenv:Envelope> |
      | <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><qr:Person><qr:Id>$id</qr:Id><qr:Name>$name</qr:Name></qr:Person></soapenv:Body></soapenv:Envelope> |
        """.trimIndent()

        val wsdlFeature = parseGherkinStringToFeature(wsdlSpec)
        var countOfTestsWithAgeAttributeSetFromExamples = 0
        val results = wsdlFeature.executeTests(
            object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    assertThat(request.path).matches("""/SOAPService/SimpleSOAP""")
                    assertThat(listOf(
                        """http://specmatic.in/SOAPService/SimpleOperation""",
                        """"http://specmatic.in/SOAPService/SimpleOperation""""))
                        .contains(request.headers["SOAPAction"])
                    val responseBody = """<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleResponse>WSDL</SimpleResponse></soapenv:Body></soapenv:Envelope>"""
                    if (requestContainsPersonNodeWithAge(request, age)) countOfTestsWithAgeAttributeSetFromExamples++
                    return HttpResponse(200, responseBody, mapOf())
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            }
        )
        assertTrue(results.success(), results.report())
        assertThat(countOfTestsWithAgeAttributeSetFromExamples).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `should create test for optional attribute in complex elements without examples`() {
        val wsdlSpec = """
Feature: Hello world

Background:
  Given wsdl test_with_optional_attributes.wsdl           
  
Scenario: test request returns test response
  When POST /SOAPService/SimpleSOAP
  And request-header SOAPAction "http://specmatic.in/SOAPService/SimpleOperation"
  And request-body <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><qr:Person age="11"><qr:Id>1</qr:Id><qr:Name>James Smith</qr:Name></qr:Person></soapenv:Body></soapenv:Envelope>
  Then status 200
  And response-body <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleResponse>test response</SimpleResponse></soapenv:Body></soapenv:Envelope>
        """.trimIndent()

        val wsdlFeature = parseGherkinStringToFeature(wsdlSpec)
        var countOfTestsWithAgeAttributeSetToRandomValue = 0
        val results = wsdlFeature.executeTests(
            object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    assertThat(request.path).matches("""/SOAPService/SimpleSOAP""")
                    assertThat(listOf(
                        """http://specmatic.in/SOAPService/SimpleOperation""",
                        """"http://specmatic.in/SOAPService/SimpleOperation""""))
                        .contains(request.headers["SOAPAction"])
                    val responseBody = """<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleResponse>WSDL</SimpleResponse></soapenv:Body></soapenv:Envelope>"""
                    if (requestContainsPersonNodeWithRandomAge(request)) countOfTestsWithAgeAttributeSetToRandomValue++
                    return HttpResponse(200, responseBody, mapOf())
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            }
        )
        assertTrue(results.success(), results.report())
        assertThat(countOfTestsWithAgeAttributeSetToRandomValue).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `should throw exception when mandatory attribute is not set`() {
        val wsdlSpec = """
Feature: Hello world

Background:
  Given wsdl test_with_mandatory_attributes.wsdl           
  
Scenario: test request returns test response
  When POST /SOAPService/SimpleSOAP
  And request-header SOAPAction "http://specmatic.in/SOAPService/SimpleOperation"
  And request-body <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><qr:Person><qr:Id>1</qr:Id><qr:Name>John Doe</qr:Name></qr:Person></soapenv:Body></soapenv:Envelope>
  Then status 200
  And response-body <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleResponse>test response</SimpleResponse></soapenv:Body></soapenv:Envelope>
        """.trimIndent()
        val exception = assertThrows<ContractException> {
            parseGherkinStringToFeature(wsdlSpec)
        }
        assertThat(exception.message == "test request returns test response\" request is not as per included wsdl / OpenApi spec")
    }

    @Disabled
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
        assertThat(results.report()).isEqualTo("""
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
  When POST /SOAPService/SimpleSOAP2
  And request-header SOAPAction "http://specmatic.in/SOAPService/AnotherOperation"
  And request-body <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><SimpleRequest>test request</SimpleRequest></soapenv:Body></soapenv:Envelope>
  Then status 200
        """.trimIndent()

        assertThatThrownBy {
            parseGherkinStringToFeature(wsdlSpec)
        }.satisfies(Consumer {
            assertThat(it.message).isEqualTo("""Scenario: "request not matching wsdl" request is not as per included wsdl / OpenApi spec""")
        })
    }

    @Test
    fun `should load child wsdl and schema imports`() {
        val wsdlXML = readTextResource("wsdl/parent.wsdl").toXML()
        val wsdl = WSDL(wsdlXML, "src/test/resources/wsdl/parent.wsdl")

        println(wsdl)
    }

    @AfterEach
    fun teardown() {
        File("test.wsdl").delete()
        File("test_with_optional_attributes.wsdl").delete()
        File("test_with_mandatory_attributes.wsdl").delete()
    }


    fun requestContainsPersonNodeWithAge(request:HttpRequest, age:Int): Boolean {
        val personAge = getPersonAge(request) ?: return false
        return age == personAge
    }

    fun requestContainsPersonNodeWithRandomAge(request:HttpRequest): Boolean {
        val personAge = getPersonAge(request) ?: return false
        return personAge > 0
    }

    private fun getPersonAge(request: HttpRequest): Int? {
        val personNode = (((request.body as XMLNode).childNodes.filterIsInstance<XMLNode>()
            .first { it.name == "Body"}).childNodes.filterIsInstance<XMLNode>().first())
        return personNode.attributes["age"]?.toStringLiteral()?.toInt()
    }
}