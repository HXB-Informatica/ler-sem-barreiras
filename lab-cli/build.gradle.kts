plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

application {
    mainClass.set("pt.lersembarreiras.lab.AlignLabKt")
    applicationName = "lab-cli"
}
