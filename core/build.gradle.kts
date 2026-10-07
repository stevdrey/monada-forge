plugins { `java-library` }

java {
    toolchain { languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt()) }
}

tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }
