package io.specmatic.templates
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver

class HtmlTemplateConfiguration {
    companion object {
        private fun configureTemplateEngine(): TemplateEngine {
            val templateResolver = ClassLoaderTemplateResolver().apply {
                prefix = "templates/"
                suffix = ".html"
                templateMode = TemplateMode.HTML
                characterEncoding = "UTF-8"
            }

            return TemplateEngine().apply {
                setTemplateResolver(templateResolver)
            }
        }

        fun process(templateName: String, variables: Map<String, Any>): String {
            val templateEngine = configureTemplateEngine()
            return templateEngine.process(templateName, Context().apply {
                setVariables(variables)
            })
        }
    }
}


