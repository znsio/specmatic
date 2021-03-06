package run.qontract.core.wsdl.parser

interface SOAPParser {
    fun convertToGherkin(url: String): String
}