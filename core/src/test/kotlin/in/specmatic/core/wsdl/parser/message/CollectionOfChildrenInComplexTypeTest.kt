package `in`.specmatic.core.wsdl.parser.message

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import `in`.specmatic.core.value.toXMLNode

internal class CollectionOfChildrenInComplexTypeTest {
    @Test
    fun `should generate child nodes`() {
        val parent = mockk<ComplexElement>()

        val child = toXMLNode("<data/>")
        val parentTypeName = "ParentType"

        every {
            parent.generateChildren(parentTypeName, child, emptyMap(), emptySet())
        }.returns(mockk())

        val collection = CollectionOfChildrenInComplexType(parent, child, mockk(), parentTypeName)
        collection.process(mockk(), emptyMap(), emptySet())

        verify(exactly = 1) {
            parent.generateChildren(parentTypeName, child, emptyMap(), emptySet())
        }
    }
}