package run.qontract.core.wsdl.parser.message

import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.XMLPattern
import run.qontract.core.value.*
import run.qontract.core.wsdl.parser.*
import run.qontract.core.wsdl.payload.ComplexTypedSOAPPayload
import run.qontract.core.wsdl.payload.SOAPPayload

data class ComplexTypeElement(val wsdlTypeReference: String, val element: XMLNode, val wsdl: WSDL) : WSDLPayloadElement {
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

        val qualification = getQualification(element, wsdlTypeReference, wsdl)

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
            "complexContent" -> ComplexContentInComplexType(this, child, wsdl, parentTypeName)
            else -> throw ContractException("Couldn't recognize child node $child")
        }
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

class ComplexContentInComplexType(
    private val parent: ComplexTypeElement,
    private val child: XMLNode,
    val wsdl: WSDL,
    private val parentTypeName: String
): ComplexTypeChild {
    override fun process(
        wsdlTypeInfo: WSDLTypeInfo,
        existingTypes: Map<String, XMLPattern>,
        typeStack: Set<String>
    ): WSDLTypeInfo {
        val extension = child.findFirstChildByName("extension") ?: throw ContractException("Found complexContent node without base attribute: $child")

        val parentComplexType = wsdl.findType(extension, "base")
        val (childrenFromParent, generatedTypesFromParent, namespacePrefixesFromParent) = parent.generateChildren(parentTypeName, parentComplexType, existingTypes, typeStack)

        val extensionChild = extension.childNodes.filterIsInstance<XMLNode>().filterNot { it.name == "annotation" }.getOrNull(0)
        val (childrenFromExtensionChild, generatedTypesFromChild, namespacePrefixesFromChild) = when {
            extensionChild != null -> parent.generateChildren(parentTypeName, extensionChild, wsdlTypeInfo.types.plus(generatedTypesFromParent), typeStack)
            else -> WSDLTypeInfo(types = generatedTypesFromParent)
        }

        return WSDLTypeInfo(
            childrenFromParent.plus(childrenFromExtensionChild),
            generatedTypesFromChild,
            namespacePrefixesFromParent.plus(namespacePrefixesFromChild)
        )
    }

}

class CollectionOfChildrenInComplexType(private val parent: ComplexTypeElement, private val child: XMLNode, val wsdl: WSDL, private val parentTypeName: String): ComplexTypeChild {
    override fun process(wsdlTypeInfo: WSDLTypeInfo, existingTypes: Map<String, XMLPattern>, typeStack: Set<String>): WSDLTypeInfo =
        parent.generateChildren(parentTypeName, child, existingTypes, typeStack)

}

class ElementInComplexType(private val child: XMLNode, val wsdl: WSDL, private val parentTypeName: String): ComplexTypeChild {
    override fun process(wsdlTypeInfo: WSDLTypeInfo, existingTypes: Map<String, XMLPattern>, typeStack: Set<String>): WSDLTypeInfo {
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

                val element = when {
                    hasSimpleTypeAttribute(child) -> SimpleElement(wsdlTypeReference, child, wsdl)
                    else -> ComplexTypeElement(wsdlTypeReference, child, wsdl)
                }

                Pair(qontractTypeName, element)
            }
            else -> {
                val wsdlTypeReference = ""

                val elementName = child.attributes["name"]
                    ?: throw ContractException("Element does not have a name: $child")
                val qontractTypeName = "${parentTypeName}_$elementName"

                val element = when {
                    hasSimpleTypeAttribute(child) -> SimpleElement(wsdlTypeReference, child, wsdl)
                    else -> ComplexTypeElement(wsdlTypeReference, child, wsdl)
                }

                Pair(qontractTypeName, element)
            }
        }

        val (newNode, generatedTypes, namespacePrefixes) =
            soapElement.getQontractTypes(qontractTypeName, existingTypes, typeStack)

        val newList: List<XMLValue> = wsdlTypeInfo.nodes.plus(newNode)
        val newTypes = wsdlTypeInfo.types.plus(generatedTypes)
        return WSDLTypeInfo(newList, newTypes, namespacePrefixes)
    }
}

interface ComplexTypeChild {
    fun process(wsdlTypeInfo: WSDLTypeInfo, existingTypes: Map<String, XMLPattern>, typeStack: Set<String>): WSDLTypeInfo
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
