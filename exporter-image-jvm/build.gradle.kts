plugins {
    kotlin("jvm")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation(project(":quark-common"))

    testImplementation(Deps.kotlin_test)
}
