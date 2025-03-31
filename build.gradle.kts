// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    alias(libs.plugins.kotlin.android) apply false
}

buildscript {
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.2")
        classpath("com.google.gms:google-services:4.4.1")
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}