package `in`.specmatic.core.wsdl.parser.message

import `in`.specmatic.core.value.XMLNode
import `in`.specmatic.core.value.namespacePrefix
import `in`.specmatic.core.value.localName
import `in`.specmatic.core.wsdl.parser.WSDL

class QualifiedNamespace(val element: XMLNode, val schema: XMLNode, private val wsdlTypeReference: String, val wsdl: WSDL) :
    NamespaceQualification {
    override val namespacePrefix: List<String>
        get() {
            return if(wsdlTypeReference.namespacePrefix().isNotBlank())
                listOf(wsdl.mapToNamespacePrefixInDefinitions(wsdlTypeReference.namespacePrefix(), element))
            else {
                val targetNamespace = schema.getAttributeValue("targetNamespace")
                listOf(wsdl.mapNamespaceToPrefix(targetNamespace))
            }
        }

    override val nodeName: String
        get() {
            val nodeName = element.getAttributeValue("name")
            return "${wsdlTypeReference.namespacePrefix()}:${nodeName.localName()}"
        }
}