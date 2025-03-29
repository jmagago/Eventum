plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.us.eventum"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.us.eventum"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Import the Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:32.7.4"))

    // Firebase
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-storage")

    // Firebase UI Library
    implementation("com.firebaseui:firebase-ui-auth:8.0.2")
    implementation("com.firebaseui:firebase-ui-database:8.0.2")

    // AndroidX Core
    implementation(libs.appcompat)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Material Design
    implementation(libs.material)

    // Testing
    testImplementation("junit:junit:${libs.versions.junit.get()}")
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.espressoCore)
}