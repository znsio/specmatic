package run.qontract.core.value

import run.qontract.core.pattern.Pattern

data class TypeDeclaration(val typeValue: String, val types: Map<String, Pattern> = emptyMap())