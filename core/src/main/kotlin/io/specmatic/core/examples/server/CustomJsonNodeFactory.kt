
import com.fasterxml.jackson.core.JsonLocation
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.*
import com.fasterxml.jackson.databind.util.RawValue
import java.math.BigDecimal
import java.math.BigInteger
import java.util.AbstractMap.SimpleEntry

class CustomJsonNodeFactory(
    nodeFactory: JsonNodeFactory,
    private val parserFactory: CustomParserFactory
) : JsonNodeFactory() {
    private val delegate: JsonNodeFactory = nodeFactory

    private val locationMapping: MutableList<Pair<JsonNode, JsonLocation>> = mutableListOf()

    /**
     * Given a node, find its location, or null if it wasn't found
     *
     * @param jsonNode the node to search for
     * @return the location of the node or null if not found
     */
    fun getLocationForNode(jsonNode: JsonNode?): JsonLocation? {
        return locationMapping.filter { e: Pair<JsonNode, JsonLocation> -> e.first.equals(jsonNode) }
            .map { e: Pair<JsonNode, JsonLocation> -> e.second }.firstOrNull()
    }

    /**
     * Simple interceptor to mark the node in the lookup list and return it back
     *
     * @param <T>  the type of the JsonNode
     * @param node the node itself
     * @return the node itself, having marked its location
    </T> */
    private fun <T : JsonNode?> markNode(node: T?): T {
        val loc: JsonLocation = parserFactory.getParser()!!.currentLocation
        locationMapping.add(node!! to loc)
        return node
    }

    public override fun booleanNode(v: Boolean): BooleanNode {
        return markNode(delegate.booleanNode(v))
    }

    public override fun nullNode(): NullNode {
        return markNode(delegate.nullNode())
    }

    public override fun numberNode(value: Byte?): ValueNode {
        return markNode(delegate.numberNode(value))
    }

    public override fun missingNode(): JsonNode {
        return super.missingNode()
    }

    public override fun numberNode(value: Short?): ValueNode {
        return markNode(delegate.numberNode(value))
    }

    public override fun numberNode(v: Int): NumericNode {
        return markNode(delegate.numberNode(v))
    }

    public override fun numberNode(value: Long): NumericNode {
        return markNode(delegate.numberNode(value))
    }

    public override fun numberNode(v: BigInteger): ValueNode {
        return markNode(delegate.numberNode(v))
    }

    public override fun numberNode(value: Float): NumericNode {
        return markNode(delegate.numberNode(value))
    }

    public override fun numberNode(value: Double): NumericNode {
        return markNode(delegate.numberNode(value))
    }

    public override fun numberNode(v: BigDecimal): ValueNode {
        return markNode(delegate.numberNode(v))
    }

    public override fun textNode(text: String?): TextNode {
        return markNode(delegate.textNode(text))
    }

    public override fun binaryNode(data: ByteArray?): BinaryNode {
        return markNode(delegate.binaryNode(data))
    }

    public override fun binaryNode(data: ByteArray?, offset: Int, length: Int): BinaryNode {
        return markNode(delegate.binaryNode(data, offset, length))
    }

    public override fun pojoNode(pojo: Any?): ValueNode {
        return markNode(delegate.pojoNode(pojo))
    }

    public override fun rawValueNode(value: RawValue?): ValueNode {
        return markNode(delegate.rawValueNode(value))
    }

    public override fun arrayNode(): ArrayNode {
        return markNode(delegate.arrayNode())
    }

    public override fun arrayNode(capacity: Int): ArrayNode {
        return markNode(delegate.arrayNode(capacity))
    }

    public override fun objectNode(): ObjectNode {
        return markNode(delegate.objectNode())
    }

    companion object {
        private const val serialVersionUID = 8807395553661461181L
    }
}