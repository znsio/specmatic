package run.qontract.core.wsdl.parser.message

import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.XMLPattern
import run.qontract.core.value.*
import run.qontract.core.wsdl.parser.*
import run.qontract.core.wsdl.payload.ComplexTypedSOAPPayload
import run.qontract.core.wsdl.payload.SOAPPayload

data class ComplexElement(val wsdlTypeReference: String, val element: XMLNode, val wsdl: WSDL) : SOAPElement {
    override fun getQontractTypes(qontractTypeName: String, existingTypes: Map<String, XMLPattern>, typeStack: Set<String>): WSDLTypeInfo {
        if(qontractTypeName in typeStack)
            return WSDLTypeInfo(types = existingTypes)

        val complexType = wsdl.getComplexTypeNode(element)

        val childTypeInfo = generateChildren(
            wsdl,
            qontractTypeName,
            complexType,
            existingTypes,
            typeStack.plus(qontractTypeName)
        )

        val qualification = getQualification(element, wsdlTypeReference, wsdl)

        val nodeTypeInfo = XMLNode(TYPE_NODE_NAME, emptyMap(), childTypeInfo.nodes)
        val inPlaceNode = toXMLNode("<${qualification.nodeName} qontract_type=\"$qontractTypeName\"/>").let {
            it.copy(attributes = it.attributes.plus(getQontractAttributes(element)))
        }

        return WSDLTypeInfo(
            listOf(inPlaceNode),
            existingTypes.plus(childTypeInfo.types).plus(qontractTypeName to XMLPattern(nodeTypeInfo)),
            childTypeInfo.namespacePrefixes.plus(qualification.namespacePrefix)
        )
    }

    private fun generateChildren(wsdl: WSDL, parentTypeName: String, complexType: XMLNode, existingTypes: Map<String, XMLPattern>, typeStack: Set<String>): WSDLTypeInfo {
        val childParts: List<XMLNode> = complexType.childNodes.filterIsInstance<XMLNode>().filterNot { it.name == "annotation" }

        return childParts.fold(WSDLTypeInfo()) { wsdlTypeInfo, child ->
            when(child.name) {
                "element" -> {
                    val (qontractTypeName, soapElement) = when {
                        child.attributes.containsKey("ref") -> {
                            val wsdlTypeReference = child.attributes.getValue("ref").toStringValue()
                            val qontractTypeName = wsdlTypeReference.replace(':', '_')

                            val resolvedChild = wsdl.getSOAPElement(wsdlTypeReference)
                            Pair(qontractTypeName, resolvedChild)
                        }
                        child.attributes.containsKey("type") -> {
                            val wsdlTypeReference = child.attributes.getValue("type").toStringValue()
                            val qontractTypeName = wsdlTypeReference.replace(':', '_')

                            val element = if(hasSimpleTypeAttribute(child)) SimpleElement(
                                wsdlTypeReference,
                                child,
                                wsdl
                            ) else ComplexElement(wsdlTypeReference, child, wsdl)

                            Pair(qontractTypeName, element)
                        }
                        else -> {
                            val wsdlTypeReference = ""

                            val elementName = child.attributes["name"]
                                ?: throw ContractException("Element does not have a name: $child")
                            val qontractTypeName = "${parentTypeName}_$elementName"

                            val element = if(hasSimpleTypeAttribute(child)) SimpleElement(
                                wsdlTypeReference,
                                child,
                                wsdl
                            ) else ComplexElement(wsdlTypeReference, child, wsdl)

                            Pair(qontractTypeName, element)
                        }
                    }

                    val (newNode, generatedTypes, namespacePrefixes) = soapElement.getQontractTypes(
                        qontractTypeName,
                        existingTypes,
                        typeStack
                    )

                    val newList: List<XMLValue> = wsdlTypeInfo.nodes.plus(newNode)
                    val newTypes = wsdlTypeInfo.types.plus(generatedTypes)
                    WSDLTypeInfo(newList, newTypes, namespacePrefixes)

                }
                "sequence", "all" -> generateChildren(wsdl, parentTypeName, child, existingTypes, typeStack)
                "complexContent" -> {
                    val extension = child.findFirstChildByName("extension") ?: throw ContractException("Found complexContent node without base attribute: $child")

                    val parentComplexType = wsdl.findType(extension, "base")
                    val (childrenFromParent, generatedTypesFromParent, namespacePrefixesFromParent) = generateChildren(wsdl, parentTypeName, parentComplexType, existingTypes, typeStack)

                    val extensionChild = extension.childNodes.filterIsInstance<XMLNode>().filterNot { it.name == "annotation" }.getOrNull(0)
                    val (childrenFromExtensionChild, generatedTypesFromChild, namespacePrefixesFromChild) = when {
                        extensionChild != null -> generateChildren(wsdl, parentTypeName, extensionChild, wsdlTypeInfo.types.plus(generatedTypesFromParent), typeStack)
                        else -> WSDLTypeInfo(types = generatedTypesFromParent)
                    }

                    WSDLTypeInfo(
                        childrenFromParent.plus(childrenFromExtensionChild),
                        generatedTypesFromChild,
                        namespacePrefixesFromParent.plus(namespacePrefixesFromChild)
                    )
                }
                else -> throw ContractException("Couldn't recognize child node $child")
            }
        }
    }

    private fun isQualified(element: XMLNode, wsdlTypeReference: String, wsdl: WSDL): Boolean {
        val namespace = element.resolveNamespace(wsdlTypeReference)

        val schema = wsdl.findSchema(namespace)

        val schemaElementFormDefault = schema.attributes["elementFormDefault"]?.toStringValue()
        val elementForm = element.attributes["form"]?.toStringValue()

        return (elementForm ?: schemaElementFormDefault) == "qualified"
    }

    private fun getQualification(element: XMLNode, wsdlTypeReference: String, wsdl: WSDL): NamespaceQualification {
        val namespace = element.resolveNamespace(wsdlTypeReference)

        val schema = wsdl.findSchema(namespace)

        val schemaElementFormDefault = schema.attributes["elementFormDefault"]?.toStringValue()
        val elementForm = element.attributes["form"]?.toStringValue()

        return when(elementForm ?: schemaElementFormDefault) {
            "qualified" -> QualifiedNamespace(element, wsdlTypeReference, wsdl)
            else -> UnqualifiedNamespace(element.getAttributeValue("name"))
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

class UnqualifiedNamespace(val name: String) : NamespaceQualification {
    override val namespacePrefix: List<String>
        get() {
            return emptyList()
        }

    override val nodeName: String
        get() {
            return name
        }

}

class QualifiedNamespace(val element: XMLNode, private val wsdlTypeReference: String, val wsdl: WSDL) : NamespaceQualification {
    override val namespacePrefix: List<String>
        get() {
            return if(wsdlTypeReference.namespacePrefix().isNotBlank())
                listOf(wsdl.mapToNamespacePrefixInDefinitions(wsdlTypeReference.namespacePrefix(), element))
            else
                emptyList() // TODO we are not providing a prefix here, if the type reference didn't contain it, because it was defined inline, and did not have to be looked up. But if it is provided in a qualified schema, does it need a namespace?
        }

    override val nodeName: String
        get() {
            val nodeName = element.getAttributeValue("name")
            return "${wsdlTypeReference.namespacePrefix()}:${nodeName.withoutNamespacePrefix()}"
        }
}

interface NamespaceQualification {
    val namespacePrefix: List<String>
    val nodeName: String
}
