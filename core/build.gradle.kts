plugins { `java-library` }

java {
    toolchain { languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt()) }
}

tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
