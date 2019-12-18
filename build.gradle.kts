import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version Versions.kotlin
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(Deps.kotlin_coroutines)

    implementation(project(":quark-common"))
    implementation(project(":renderer-ascii"))
    implementation(project(":renderer-svg"))

    testImplementation(Deps.kotlin_test)
}

allprojects {
    group = "io.github.dector.${rootProject.name}"
    version = "0.1-SNAPSHOT"

    repositories {
        jcenter()
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
        kotlinOptions.freeCompilerArgs = listOf("-Xexperimental=kotlin.ExperimentalUnsignedTypes")
    }
}

val test by tasks.getting(Test::class) {
    useJUnitPlatform()
}
