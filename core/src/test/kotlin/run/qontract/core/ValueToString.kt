package run.qontract.core

import run.qontract.core.value.EmptyString
import run.qontract.core.value.Value
import run.qontract.core.value.XMLValue
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.xml.sax.SAXException
import run.qontract.core.pattern.parsedJSONStructure
import java.io.IOException
import javax.xml.parsers.ParserConfigurationException

class ValueToString {
    @Test
    fun noMessageYieldsEmptyString() {
        val body: Value = EmptyString
        Assertions.assertEquals("", body.toString())
    }

    @Test
    fun jsonStringTest() {
        val jsonString = """{"a": 1, "b": 2}"""
        val jsonObject = JSONObject(jsonString)
        val body: Value = parsedJSONStructure(jsonString)
        val jsonObject2 = JSONObject(body.toString())
        Assertions.assertEquals(jsonObject.getInt("a"), jsonObject2.getInt("a"))
        Assertions.assertEquals(jsonObject.getInt("b"), jsonObject2.getInt("b"))
    }

    @Test
    @Throws(IOException::class, SAXException::class, ParserConfigurationException::class)
    fun xmlStringTest() {
        val xmlData = "<node>1</node>"
        val body: Value = XMLValue(xmlData)
        val xmlData2 = body.toString()
        val body2 = XMLValue(xmlData2)
        val root = body2.node
        Assertions.assertEquals("node", root.nodeName)
        Assertions.assertEquals("1", root.firstChild.nodeValue)
    }
}