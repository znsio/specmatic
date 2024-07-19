package io.specmatic.core.wsdl.parser.message

import io.specmatic.core.pattern.ContractException
import io.specmatic.core.value.XMLNode
import io.specmatic.core.value.localName
import io.specmatic.core.value.namespacePrefix
import io.specmatic.core.wsdl.parser.WSDL
import io.specmatic.core.wsdl.parser.namespaceOrSchemaNamespace

class QualifiedNamespace(
    val element: XMLNode,
    val schema: XMLNode,
    private val wsdlTypeReference: String,
    val wsdl: WSDL
) :
    NamespaceQualification {

    override val namespacePrefix: List<String>
        get() {
            return if(wsdlTypeReference.namespacePrefix().isNotBlank()) {
                val fullyQualifiedName = element.fullyQualifiedNameFromQName(wsdlTypeReference)

                listOf(wsdl.mapNamespaceToPrefix(fullyQualifiedName.namespace))
            }
            else {
                val targetNamespace = schema.getAttributeValue("targetNamespace")
                listOf(wsdl.mapNamespaceToPrefix(targetNamespace))
            }
        }

    override val nodeName: String
        get() {
            val nodeName = element.getAttributeValue("name")

            val fullyQualifiedName = if(wsdlTypeReference.isNotBlank()) {
                element.fullyQualifiedNameFromQName(wsdlTypeReference)
            }
            else {
                element.fullyQualifiedName(wsdl)
            }

            val mappedPrefix = wsdl.mapNamespaceToPrefix(namespaceOrSchemaNamespace(fullyQualifiedName.namespace, schema) ?: throw ContractException("Could not find namespace prefix for $nodeName"))

            return "${mappedPrefix}:${nodeName.localName()}"
        }
}