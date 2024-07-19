package io.specmatic.core.wsdl.parser

interface SOAPParser {
    fun convertToGherkin(url: String): String
}