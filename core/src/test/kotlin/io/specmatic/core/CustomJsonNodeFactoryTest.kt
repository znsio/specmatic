package io.specmatic.core

import CustomJsonNodeFactory
import CustomParserFactory
import com.fasterxml.jackson.core.JsonLocation
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.TextNode
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*


class CustomJsonNodeFactoryTest {

    private val customParserFactory = mockk<CustomParserFactory>()
    private val delegateFactory = JsonNodeFactory.instance
    private val customJsonNodeFactory = CustomJsonNodeFactory(delegateFactory, customParserFactory)

    @Test
    fun `should create boolean node and mark its location`() {
        val mockLocation = mockk<JsonLocation>()
        every { customParserFactory.getParser()?.currentLocation } returns mockLocation

        val node = customJsonNodeFactory.booleanNode(true)
        val location = customJsonNodeFactory.getLocationForNode(node)
        assertNotNull(location)
    }


    @Test
    fun `should create text node and mark its location`() {
        val mockLocation = mockk<JsonLocation>()
        every { customParserFactory.getParser()?.currentLocation } returns mockLocation

        val node = customJsonNodeFactory.textNode("test")
        val location = customJsonNodeFactory.getLocationForNode(node)
        assertNotNull(location)
    }

    @Test
    fun `should create array node and mark its location`() {
        val mockLocation = mockk<JsonLocation>()
        every { customParserFactory.getParser()?.currentLocation } returns mockLocation

        val node = customJsonNodeFactory.arrayNode()
        val location = customJsonNodeFactory.getLocationForNode(node)
        assertNotNull(location)
    }

    @Test
    fun `should retrieve correct location for marked node`() {
        val mockLocation = mockk<JsonLocation>()
        every { customParserFactory.getParser()?.currentLocation } returns mockLocation

        val textNode = customJsonNodeFactory.textNode("location")
        val location = customJsonNodeFactory.getLocationForNode(textNode)
        assertNotNull(location)
        assertEquals(mockLocation, location)
    }

    @Test
    fun `should return null for unmarked node`() {
        val unmarkedNode = TextNode("unmarked")
        val location = customJsonNodeFactory.getLocationForNode(unmarkedNode)
        assertNull(location)
    }

    @Test
    fun `should handle missing node gracefully`() {
        val missingNode = customJsonNodeFactory.missingNode()
        val location = customJsonNodeFactory.getLocationForNode(missingNode)
        assertNull(location)
    }
}
