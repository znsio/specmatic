package application.example

import picocli.CommandLine

class ExamplesToValidateConverter : CommandLine.ITypeConverter<ExamplesToValidate> {
    override fun convert(value: String): ExamplesToValidate {
        return ExamplesToValidate.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: throw IllegalArgumentException("Invalid value: $value. Expected one of: ${ExamplesToValidate.entries.joinToString(", ")}")
    }
}