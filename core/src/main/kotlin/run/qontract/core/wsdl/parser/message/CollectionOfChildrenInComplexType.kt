package run.qontract.core.wsdl.parser.message

import run.qontract.core.pattern.XMLPattern
import run.qontract.core.value.XMLNode
import run.qontract.core.wsdl.parser.WSDL
import run.qontract.core.wsdl.parser.WSDLTypeInfo

internal class CollectionOfChildrenInComplexType(private val parent: ComplexElement, private val child: XMLNode, val wsdl: WSDL, private val parentTypeName: String):
    ComplexTypeChild {
    override fun process(wsdlTypeInfo: WSDLTypeInfo, existingTypes: Map<String, XMLPattern>, typeStack: Set<String>): WSDLTypeInfo =
        parent.generateChildren(parentTypeName, child, existingTypes, typeStack)

}