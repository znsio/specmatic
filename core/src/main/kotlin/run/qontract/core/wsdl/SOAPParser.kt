package run.qontract.core.wsdl

interface SOAPParser {
    fun convertToGherkin(wsdl: WSDL, url: String): String
}