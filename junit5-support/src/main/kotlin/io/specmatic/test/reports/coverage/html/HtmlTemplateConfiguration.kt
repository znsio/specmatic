package io.specmatic.test.reports.coverage.html
import org.thymeleaf.TemplateEngine
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver

class HtmlTemplateConfiguration {
    companion object {
        fun configureTemplateEngine(): TemplateEngine {
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
    }
}


