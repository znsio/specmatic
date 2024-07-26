package io.specmatic.core.wsdl.parser.message

import io.specmatic.core.pattern.ContractException
import io.specmatic.core.pattern.XML_ATTR_OPTIONAL_SUFFIX
import io.specmatic.core.value.Value
import io.specmatic.core.value.XMLNode
import io.specmatic.core.value.localName

class AttributeElement(xmlNode: XMLNode) {
    val name: String
    val type: Value
    private val mandatory: Boolean
    val nameWithOptionality: String

    init {
        name = fromNameAttribute(xmlNode)
            ?: throw ContractException("'name' not defined for attribute: ${xmlNode.oneLineDescription}")
        type = elementTypeValue(xmlNode)
        mandatory = isMandatory(xmlNode) ?: false
        nameWithOptionality = when (mandatory) {
            true -> name
            else -> "${name}${XML_ATTR_OPTIONAL_SUFFIX}"
        }
    }
}

fun isMandatory(element: XMLNode): Boolean? {
    return element.attributes["use"]?.let {
        it.toStringLiteral().localName() == "required"
    }
}

fun fromNameAttribute(element: XMLNode): String? {
    return element.attributes["name"]?.toStringLiteral()?.localName()
}
