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
            }
        }

        jvm().compilations["main"].defaultSourceSet {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
            }
        }
    }
}

/*
dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation(project(":quark-common"))

    testImplementation(Deps.kotlin_test)
}
*/
