package `in`.specmatic.core.wsdl.parser.message

import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.value.Value
import `in`.specmatic.core.value.XMLNode

class AttributeElement(xmlNode: XMLNode) {
    var name:String
    var type:Value
    init {
        name = fromNameAttribute(xmlNode) ?: throw ContractException("'name' not defined for attribute: ${xmlNode.oneLineDescription}")
        type = elementTypeValue(xmlNode)
    }
}