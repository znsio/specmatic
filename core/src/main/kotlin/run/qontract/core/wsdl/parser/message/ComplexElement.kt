package run.qontract.core.wsdl.parser.message

import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.XMLPattern
import run.qontract.core.value.XMLNode
import run.qontract.core.value.toXMLNode
import run.qontract.core.wsdl.parser.SOAPMessageType
import run.qontract.core.wsdl.parser.WSDL
import run.qontract.core.wsdl.parser.WSDLTypeInfo
import run.qontract.core.wsdl.payload.ComplexTypedSOAPPayload
import run.qontract.core.wsdl.payload.SOAPPayload

data class ComplexElement(val wsdlTypeReference: String, val element: XMLNode, val wsdl: WSDL): WSDLElement {
    override fun getQontractTypes(qontractTypeName: String, existingTypes: Map<String, XMLPattern>, typeStack: Set<String>): WSDLTypeInfo {
        if(qontractTypeName in typeStack)
            return WSDLTypeInfo(types = existingTypes)

        val complexType = wsdl.getComplexTypeNode(element)

        val childTypeInfo = generateChildren(
            qontractTypeName,
            complexType,
            existingTypes,
            typeStack.plus(qontractTypeName)
        )

        val qualification = wsdl.getQualification(element, wsdlTypeReference)

        val inPlaceNode = toXMLNode("<${qualification.nodeName} qontract_type=\"$qontractTypeName\"/>").let {
            it.copy(attributes = it.attributes.plus(getQontractAttributes(element)))
        }

        val types = existingTypes
                        .plus(childTypeInfo.types)
                        .plus(qontractTypeName to XMLPattern(childTypeInfo.nodeTypeInfo))

        val namespaces = childTypeInfo.namespacePrefixes.plus(qualification.namespacePrefix)

        return WSDLTypeInfo(
            listOf(inPlaceNode),
            types,
            namespaces
        )
    }

    internal fun generateChildren(parentTypeName: String, complexType: XMLNode, existingTypes: Map<String, XMLPattern>, typeStack: Set<String>): WSDLTypeInfo {
        return eliminateAnnotations(complexType).map {
            complexTypeChildNode(it, wsdl, parentTypeName)
        }.fold(WSDLTypeInfo()) { wsdlTypeInfo, child ->
            child.process(wsdlTypeInfo, existingTypes, typeStack)
        }
    }

    private fun eliminateAnnotations(complexType: XMLNode) =
        complexType.childNodes.filterIsInstance<XMLNode>().filterNot { it.name == "annotation" }

    private fun complexTypeChildNode(child: XMLNode, wsdl: WSDL, parentTypeName: String): ComplexTypeChild {
        return when(child.name) {
            "element" -> ElementInComplexType(child, wsdl, parentTypeName)
            "sequence", "all" -> CollectionOfChildrenInComplexType(this, child, wsdl, parentTypeName)
            "complexContent" -> ComplexTypeExtension(this, child, wsdl, parentTypeName)
            else -> throw ContractException("Couldn't recognize child node $child")
        }
    }

    override fun getSOAPPayload(
        soapMessageType: SOAPMessageType,
        nodeNameForSOAPBody: String,
        qontractTypeName: String,
        namespaces: Map<String, String>,
        typeInfo: WSDLTypeInfo
    ): SOAPPayload =
        ComplexTypedSOAPPayload(soapMessageType, nodeNameForSOAPBody, qontractTypeName, namespaces)
}

