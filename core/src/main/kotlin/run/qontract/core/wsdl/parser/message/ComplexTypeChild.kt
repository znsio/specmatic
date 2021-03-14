package run.qontract.core.wsdl.parser.message

import run.qontract.core.pattern.XMLPattern
import run.qontract.core.wsdl.parser.WSDLTypeInfo

interface ComplexTypeChild {
    // TODO perhaps existing types param and the field WSDLTypeInfo.types are duplicates?
    fun process(wsdlTypeInfo: WSDLTypeInfo, existingTypes: Map<String, XMLPattern>, typeStack: Set<String>): WSDLTypeInfo
}