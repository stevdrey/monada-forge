plugins { base }

allprojects {
    group = "io.github.stevdrey.monadaforge"
    version = "0.1.0-SNAPSHOT"
}

tasks.named("check") { dependsOn(":core:check", ":desktop:check") }
tasks.named("assemble") { dependsOn(":core:assemble", ":desktop:assemble") }
tasks.named("clean") { dependsOn(":core:clean", ":desktop:clean") }
