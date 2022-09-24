package `in`.specmatic.core.wsdl.parser.message

import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.XMLPattern
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.XMLNode
import `in`.specmatic.core.wsdl.parser.SOAPMessageType
import `in`.specmatic.core.wsdl.parser.WSDL
import `in`.specmatic.core.wsdl.parser.WSDLTypeInfo
import `in`.specmatic.core.wsdl.payload.SOAPPayload

data class ReferredType(val wsdlTypeReference: String, val element: XMLNode, val wsdl: WSDL, val namespaceQualification: NamespaceQualification? = null):
    WSDLElement {
    private val elementType: WSDLElement
      get() {
          val typeNode: XMLNode = element.attributes["type"]?.let {
              wsdl.getSimpleTypeXMLNode(element)
          } ?: element

          return fromRestriction(typeNode)?.let { type ->
              if(!isPrimitiveType(typeNode))
                  throw ContractException("Simple type $type in restriction not recognized")

              SimpleElement(wsdlTypeReference, element.copy(attributes = element.attributes.plus("type" to StringValue(type))), wsdl)
          } ?: ComplexElement(wsdlTypeReference, element, wsdl)
      }

    override fun deriveSpecmaticTypes(
        qontractTypeName: String,
        existingTypes: Map<String, XMLPattern>,
        typeStack: Set<String>
    ): WSDLTypeInfo {
        return elementType.deriveSpecmaticTypes(qontractTypeName, existingTypes, typeStack)
    }

    override fun getSOAPPayload(
        soapMessageType: SOAPMessageType,
        nodeNameForSOAPBody: String,
        qontractTypeName: String,
        namespaces: Map<String, String>,
        typeInfo: WSDLTypeInfo
    ): SOAPPayload {
        return elementType.getSOAPPayload(soapMessageType, nodeNameForSOAPBody, qontractTypeName, namespaces, typeInfo)
    }
}