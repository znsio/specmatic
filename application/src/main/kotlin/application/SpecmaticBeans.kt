package application

import org.junit.platform.launcher.Launcher
import org.junit.platform.launcher.core.LauncherFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class SpecmaticBeans {
    @Bean
    open fun junitLauncher(): Launcher = LauncherFactory.create()
}