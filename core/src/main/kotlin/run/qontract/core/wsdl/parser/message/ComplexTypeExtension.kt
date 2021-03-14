package run.qontract.core.wsdl.parser.message

import run.qontract.core.pattern.XMLPattern
import run.qontract.core.value.XMLNode
import run.qontract.core.wsdl.parser.WSDL
import run.qontract.core.wsdl.parser.WSDLTypeInfo

class ComplexTypeExtension(
    private val parent: ComplexElement,
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
        val parentTypeInfo = parent.generateChildren(parentTypeName, parentComplexType, existingTypes, typeStack)

        val extensionChild = extension.childNodes.filterIsInstance<XMLNode>().filterNot {
            it.name == "annotation"
        }.getOrNull(0)

        val childTypeInfo = when {
            extensionChild != null -> parent.generateChildren(parentTypeName, extensionChild, wsdlTypeInfo.types.plus(parentTypeInfo.types), typeStack)
            else -> WSDLTypeInfo(types = parentTypeInfo.types)
        }

        return WSDLTypeInfo(
            parentTypeInfo.nodes.plus(childTypeInfo.nodes),
            childTypeInfo.types,
            parentTypeInfo.namespacePrefixes.plus(childTypeInfo.namespacePrefixes)
        )
    }

}