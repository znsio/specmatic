package io.specmatic.conversions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class WsdlExampleLoadingTest {
    @Test
    fun `should detect with_examples directory and load XML examples`() {
        val wsdlPath = "src/test/resources/wsdl/with_examples/order_api.wsdl"
        val wsdlFile = File(wsdlPath)
        
        // Verify the setup
        assertThat(wsdlFile.exists()).isTrue()
        assertThat(wsdlFile.parentFile.name).isEqualTo("with_examples")
        
        // Verify createOrder example files exist
        val responseFile = File(wsdlFile.parentFile, "createOrder.xml")
        val requestFile = File(wsdlFile.parentFile, "createOrder_request.xml")
        assertThat(responseFile.exists()).isTrue()
        assertThat(requestFile.exists()).isTrue()
        
        // Verify request contains expected productid
        val requestContent = requestFile.readText()
        assertThat(requestContent).contains("<productid>123</productid>")
    }
    
    @Test
    fun `should create feature with examples when WSDL is in with_examples directory`() {
        val wsdlPath = "src/test/resources/wsdl/with_examples/order_api.wsdl"
        val wsdlContent = File(wsdlPath).readText()
        
        val feature = wsdlContentToFeature(wsdlContent, wsdlPath)
        
        // Verify that the feature has examples
        assertThat(feature.exampleStore.examples).isNotEmpty()
        
        // Verify that createOrder operation has examples
        val stubsFromExamples = feature.stubsFromExamples
        assertThat(stubsFromExamples).containsKey("createOrder")
        
        val createOrderExamples = stubsFromExamples["createOrder"]
        assertThat(createOrderExamples).isNotNull()
        assertThat(createOrderExamples).isNotEmpty()
        
        // Verify the request contains the expected productid
        val (request, response) = createOrderExamples!!.first()
        val requestBodyString = request.body.toString()
        val responseBodyString = response.body.toString()
        
        assertThat(requestBodyString).contains("<productid>123</productid>")
        assertThat(responseBodyString).contains("<id>10</id>")
    }
}