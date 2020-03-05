import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform")
    id 'com.github.johnrengelman.shadow' version '5.2.0'
}

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))

                implementation(project(":quark-common"))
                implementation(project(":exporter-ascii"))
                implementation(project(":exporter-svg"))
            }
        }

        jvm().compilations["main"].defaultSourceSet {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
            }
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.freeCompilerArgs =
        listOf("-Xexperimental=kotlin.ExperimentalUnsignedTypes")
}

/*
dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation(project(":quark-common"))

    testImplementation(Deps.kotlin_test)
}
*/
