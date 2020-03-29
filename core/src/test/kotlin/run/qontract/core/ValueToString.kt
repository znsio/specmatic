package run.qontract.core

import run.qontract.core.utilities.parseXML
import run.qontract.core.value.NoValue
import run.qontract.core.value.Value
import run.qontract.core.value.XMLValue
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.w3c.dom.Document
import org.xml.sax.SAXException
import run.qontract.core.pattern.parsedJSON
import java.io.IOException
import javax.xml.parsers.ParserConfigurationException

class ValueToString {
    @Test
    fun noMessageYieldsEmptyString() {
        val body: Value = NoValue()
        Assertions.assertEquals("", body.toString())
    }

    @Test
    fun jsonStringTest() {
        val jsonString = """{"a": 1, "b": 2}"""
        val jsonObject = JSONObject(jsonString)
        val body: Value = parsedJSON(jsonString) ?: NoValue()
        val jsonObject2 = JSONObject(body.toString())
        Assertions.assertEquals(jsonObject.getInt("a"), jsonObject2.getInt("a"))
        Assertions.assertEquals(jsonObject.getInt("b"), jsonObject2.getInt("b"))
    }

    @Test
    @Throws(IOException::class, SAXException::class, ParserConfigurationException::class)
    fun xmlStringTest() {
        val xmlData = "<node>1</node>"
        val body: Value = XMLValue(parseXML(xmlData))
        val xmlData2 = body.toString()
        val body2 = XMLValue(parseXML(xmlData2))
        val document = body2.value as Document
        val root = document.documentElement
        Assertions.assertEquals("node", root.nodeName)
        Assertions.assertEquals("1", root.firstChild.nodeValue)
    }
}