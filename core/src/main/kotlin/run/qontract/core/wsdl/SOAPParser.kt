package run.qontract.core.wsdl

interface SOAPParser {
    fun convertToGherkin(url: String): String
}