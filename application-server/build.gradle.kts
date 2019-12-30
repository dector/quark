plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(project(":quark-common"))
                implementation(project(":quark-generator"))
                implementation(project(":exporter-svg"))
            }
        }

        jvm().compilations["main"].defaultSourceSet {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                implementation("org.http4k:http4k-core:3.206.0")
                implementation("org.http4k:http4k-server-jetty:3.206.0")
            }
        }
    }
}
