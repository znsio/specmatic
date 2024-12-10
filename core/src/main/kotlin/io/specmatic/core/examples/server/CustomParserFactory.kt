
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import java.io.IOException
import java.io.Reader

class CustomParserFactory : JsonFactory() {
    private var parser: JsonParser? = null

    fun getParser(): JsonParser? {
        return this.parser
    }

    @Throws(IOException::class, JsonParseException::class)
    public override fun createParser(r: Reader?): JsonParser? {
        parser = super.createParser(r)
        return parser
    }

    @Throws(IOException::class, JsonParseException::class)
    public override fun createParser(content: String?): JsonParser? {
        parser = super.createParser(content)
        return parser
    }

    companion object {
        private val serialVersionUID = -7523974986510864179L
    }
}