plugins {
    application
    alias(libs.plugins.javafx)
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt()) }
}

javafx {
    version = libs.versions.javafx.get()
    modules("javafx.controls")
}

dependencies { implementation(project(":core")) }

application {
    mainModule = "io.github.stevdrey.monadaforge.desktop"
    mainClass = "io.github.stevdrey.monadaforge.desktop.ForgeApplication"
    applicationDefaultJvmArgs = listOf("--enable-native-access=javafx.graphics")
}

tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
