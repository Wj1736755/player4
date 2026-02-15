plugins {
    alias(libs.plugins.android).apply(false)
    alias(libs.plugins.kotlinAndroid).apply(false)
    alias(libs.plugins.ksp).apply(false)
    alias(libs.plugins.detekt).apply(false)
}

apply(from = "version.gradle")
apply(from = "update-version.gradle")

tasks.register<Delete>("clean") {
    delete {
        rootProject.buildDir
    }
}
