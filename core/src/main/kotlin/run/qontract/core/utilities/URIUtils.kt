package run.qontract.core.utilities

import run.qontract.core.pattern.isPatternToken
import run.qontract.core.pattern.removePatternDelimiter
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.stream.Collectors

object URIUtils {
    @Throws(UnsupportedEncodingException::class)
    fun parseQuery(query: String?): HashMap<String, String> {
        if (query == null) {
            return HashMap()
        }
        val pairs = query.split("&".toRegex()).toTypedArray()
        val query_pairs = HashMap<String, String>()
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            query_pairs[URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.toString())] = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.toString())
        }
        return query_pairs
    }

    fun parsePathParams(rawPath: String): HashMap<String, String> {
        val pathParts = rawPath.split("/".toRegex()).toTypedArray()
        val pathParams = HashMap<String, String>()
        for (pathPart in java.util.Arrays.stream(pathParts).filter { isPatternToken(it) }.collect(Collectors.toList())) {
            val nameAndType = getNameAndType(pathPart)
            pathParams[nameAndType[0]] = "(" + nameAndType[1] + ")"
        }
        return pathParams
    }

    private fun getNameAndType(placeHolder: String): Array<String> {
        return removePatternDelimiter(placeHolder).split(":".toRegex()).toTypedArray()
    }
}