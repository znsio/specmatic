package `in`.specmatic.core.wsdl.parser.message

import `in`.specmatic.core.pattern.XMLPattern
import `in`.specmatic.core.value.XMLNode
import `in`.specmatic.core.wsdl.parser.WSDL
import `in`.specmatic.core.wsdl.parser.WSDLTypeInfo

class ComplexTypeExtension(
    private val complexTypeNode: XMLNode,
    val wsdl: WSDL,
    private val parentTypeName: String
): ComplexTypeChild {
    override fun process(
        wsdlTypeInfo: WSDLTypeInfo,
        existingTypes: Map<String, XMLPattern>,
        typeStack: Set<String>
    ): WSDLTypeInfo {
        val extension = complexTypeNode.findFirstChildByName("extension", "Found complexContent node without base attribute: $complexTypeNode")

        val parentComplexType = wsdl.findTypeFromAttribute(extension, "base")
        val parentTypeInfo = generateChildren(parentTypeName, parentComplexType, existingTypes, typeStack, wsdl)

        val extensionChild = extension.childNodes.filterIsInstance<XMLNode>().filterNot {
            it.name == "annotation"
        }.firstOrNull()

        val childTypeInfo = when {
            extensionChild != null -> generateChildren(parentTypeName, extensionChild, wsdlTypeInfo.types.plus(parentTypeInfo.types), typeStack, wsdl)
            else -> WSDLTypeInfo(types = parentTypeInfo.types)
        }

        return WSDLTypeInfo(
            parentTypeInfo.nodes.plus(childTypeInfo.nodes),
            childTypeInfo.types,
            parentTypeInfo.namespacePrefixes.plus(childTypeInfo.namespacePrefixes)
        )
    }

}