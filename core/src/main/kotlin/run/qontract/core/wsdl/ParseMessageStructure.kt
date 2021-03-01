package run.qontract.core.wsdl

import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.Pattern
import run.qontract.core.pattern.XMLPattern
import run.qontract.core.value.*

class ParseMessageStructure(private val wsdl: WSDL, private val wsdlTypeReference: String, private val soapMessageType: SOAPMessageType, private val existingTypes: Map<String, Pattern>, private val operationName: String) :
    MessageTypeInfoParser {
    override fun execute(): MessageTypeInfoParser {
        val topLevelElement = wsdl.findElement(
            wsdlTypeReference.withoutNamespacePrefix(), // TODO might need to do this in a cleaner way
            wsdl.resolveNamespace(wsdlTypeReference)
        )

        val qontractTypeName = "${operationName.replace(":", "_")}${soapMessageType.messageTypeName.capitalize()}"
        val typeInfo = getQontractTypes(qontractTypeName, wsdlTypeReference, topLevelElement, wsdl, existingTypes, emptySet())
        val namespaces: Map<String, String> = wsdl.getNamespaces(typeInfo)
        val nodeNameForSOAPBody = (typeInfo.nodes.first() as XMLNode).realName

        val soapPayload = when {
            (typeInfo.nodes.first() as XMLNode).attributes.containsKey("qontract_type") -> ComplexTypedSOAPPayload(
                soapMessageType,
                nodeNameForSOAPBody,
                qontractTypeName,
                namespaces
            )
            else -> SimpleTypedSOAPPayload(soapMessageType, typeInfo.nodes.first() as XMLNode, namespaces)
        }

        return MessageTypeProcessingComplete(SoapPayloadType(typeInfo.types, soapPayload))
    }

    private fun getQontractTypes(qontractTypeName: String, wsdlTypeReference: String, element: XMLNode, wsdl: WSDL, existingTypes: Map<String, Pattern>, typeStack: Set<String>): WSDLTypeInfo {
        return when {
            hasSimpleTypeAttribute(element) -> createSimpleType(element, existingTypes).let {
                WSDLTypeInfo(
                    it.first,
                    it.second
                )
            }
            qontractTypeName in typeStack -> WSDLTypeInfo(types = existingTypes)
            else -> {
                val complexType = wsdl.getComplexTypeNode(element)

                val childTypeInfo = generateChildren(
                    wsdl,
                    qontractTypeName,
                    complexType,
                    existingTypes,
                    typeStack.plus(qontractTypeName)
                )

                val isQualified = isQualified(element, wsdlTypeReference, wsdl)
                val nodeName = element.getAttributeValue("name")

                val qualifiedNodeName = when {
                    isQualified -> "${wsdlTypeReference.namespacePrefix()}:${nodeName.withoutNamespacePrefix()}"
                    else -> nodeName
                }

                val nodeTypeInfo = XMLNode(typeNodeName, emptyMap(), childTypeInfo.nodes)
                val inPlaceNode = toXMLNode("<$qualifiedNodeName qontract_type=\"$qontractTypeName\"/>").let {
                    it.copy(attributes = it.attributes.plus(getQontractAttributes(element)))
                }

                val namespacePrefix = when {
                    isQualified ->
                        if(wsdlTypeReference.namespacePrefix().isNotBlank())
                            listOf(wsdl.mapToNamespacePrefixInDefinitions(wsdlTypeReference.namespacePrefix(), element))
                        else
                            emptyList() // TODO we are not providing a prefix here, if the type reference didn't contain it, because it was defined inline, and did not have to be looked up. But if it is provided in a qualified schema, does it need a namespace?
                    else ->
                        emptyList()
                }

                WSDLTypeInfo(
                    listOf(inPlaceNode),
                    existingTypes.plus(childTypeInfo.types).plus(qontractTypeName to XMLPattern(nodeTypeInfo)),
                    childTypeInfo.namespacePrefixes.plus(namespacePrefix)
                )
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

    private fun generateChildren(wsdl: WSDL, parentTypeName: String, complexType: XMLNode, existingTypes: Map<String, Pattern>, typeStack: Set<String>): WSDLTypeInfo {
        val childParts: List<XMLNode> = complexType.childNodes.filterIsInstance<XMLNode>().filterNot { it.name == "annotation" }

        return childParts.fold(WSDLTypeInfo()) { wsdlTypeInfo, child ->
            when(child.name) {
                "element" -> {
                    val (wsdlTypeReference, qontractTypeName, resolvedChild) = when {
                        child.attributes.containsKey("ref") -> {
                            val wsdlTypeReference = child.attributes.getValue("ref").toStringValue()
                            val qontractTypeName = wsdlTypeReference.replace(':', '_')

                            val resolvedChild = wsdl.findElement(wsdlTypeReference)
                            Triple(wsdlTypeReference, qontractTypeName, resolvedChild)
                        }
                        child.attributes.containsKey("type") -> {
                            val wsdlTypeReference = child.attributes.getValue("type").toStringValue()
                            val qontractTypeName = wsdlTypeReference.replace(':', '_')

                            Triple(wsdlTypeReference, qontractTypeName, child)
                        }
                        else -> {
                            val wsdlTypeReference = ""

                            val elementName = child.attributes["name"]
                                ?: throw ContractException("Element does not have a name: $child")
                            val qontractTypeName = "${parentTypeName}_$elementName"

                            Triple(wsdlTypeReference, qontractTypeName, child)
                        }
                    }

                    val (newNode, generatedTypes, namespacePrefixes) = getQontractTypes(
                        qontractTypeName,
                        wsdlTypeReference,
                        resolvedChild,
                        wsdl,
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

    private fun createSimpleType(
        element: XMLNode,
        types: Map<String, Pattern>
    ): Pair<List<XMLValue>, Map<String, Pattern>> {
        val node = createSimpleType(element)
        return Pair(listOf(node), types)
    }
}

private fun createSimpleType(
    element: XMLNode
): XMLNode {
    val typeName = element.attributes.getValue("type").toStringValue().withoutNamespacePrefix()
    val value = when {
        primitiveStringTypes.contains(typeName) -> StringValue("(string)")
        primitiveNumberTypes.contains(typeName) -> StringValue("(number)")
        primitiveDateTypes.contains(typeName) -> StringValue("(datetime)")
        primitiveBooleanType.contains(typeName) -> StringValue("(boolean)")
        else -> throw ContractException("""Primitive type "$typeName" not recognized""")
    }

    val qontractAttributes = getQontractAttributes(element)

    return XMLNode(element.getAttributeValue("name").withoutNamespacePrefix(), qontractAttributes, listOf(value))
}

private fun getQontractAttributes(element: XMLNode): Map<String, StringValue> {
    return when {
        elementIsOptional(element) -> mapOf(OCCURS_ATTRIBUTE_NAME to StringValue(OPTIONAL_ATTRIBUTE_VALUE))
        multipleElementsCanExist(element) -> mapOf(OCCURS_ATTRIBUTE_NAME to StringValue(MULTIPLE_ATTRIBUTE_VALUE))
        else -> emptyMap()
    }
}

private fun multipleElementsCanExist(element: XMLNode): Boolean {
    return element.attributes.containsKey("maxOccurs")
            && (element.attributes["maxOccurs"]?.toStringValue() == "unbounded"
            || element.attributes.getValue("maxOccurs").toStringValue().toInt() > 1)
}

private fun elementIsOptional(element: XMLNode): Boolean {
    return element.attributes["minOccurs"]?.toStringValue() == "0" && !element.attributes.containsKey("maxOccurs")
}

fun isPrimitiveType(node: XMLNode): Boolean {
    val type = node.attributes.getValue("type").toStringValue()
    val namespace = node.resolveNamespace(type)

    if(namespace.isBlank())
        return primitiveTypes.contains(type)

    return namespace == primitiveNamespace
}

val primitiveStringTypes = listOf("string", "anyType", "duration", "time", "date", "gYearMonth", "gYear", "gMonthDay", "gDay", "gMonth", "hexBinary", "base64Binary", "anyURI", "QName", "NOTATION")
val primitiveNumberTypes = listOf("int", "integer", "long", "decimal", "float", "double", "numeric")
val primitiveDateTypes = listOf("dateTime")
val primitiveBooleanType = listOf("boolean")
val primitiveTypes = primitiveStringTypes.plus(primitiveNumberTypes).plus(primitiveDateTypes).plus(primitiveBooleanType)

internal const val primitiveNamespace = "http://www.w3.org/2001/XMLSchema"

const val OCCURS_ATTRIBUTE_NAME = "qontract_occurs"
const val OPTIONAL_ATTRIBUTE_VALUE = "optional"
const val MULTIPLE_ATTRIBUTE_VALUE = "multiple"
