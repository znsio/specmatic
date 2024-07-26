package io.specmatic.core.wsdl.parser.message

interface NamespaceQualification {
    val namespacePrefix: List<String>
    val nodeName: String
}