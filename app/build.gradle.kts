import java.io.File
import java.util.Properties

plugins {
    id("com.android.application") version "8.9.1"
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

// Función para leer y actualizar el número de build
fun getNextBuildNumber(): Int {
    val versionFile = File("version.properties")
    val properties = Properties()
    
    if (versionFile.exists()) {
        properties.load(versionFile.inputStream())
    }
    
    val currentBuildNumber = properties.getProperty("buildNumber", "1").trim().toInt()
    val nextBuildNumber = currentBuildNumber + 1
    
    properties.setProperty("buildNumber", nextBuildNumber.toString())
    versionFile.outputStream().use { properties.store(it, "Build number for Eventum app") }
    
    return currentBuildNumber
}

android {
    namespace = "com.us.eventum"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.us.eventum"
        minSdk = 26
        targetSdk = 34
        
        // Versión de la aplicación
        val majorVersion = 0  // Cambiado a 0 para indicar desarrollo temprano
        val minorVersion = 2  // Incrementado a 1 para indicar primera versión de desarrollo
        val patchVersion = 0
        val buildNumber = getNextBuildNumber() // Obtener el siguiente número de build automáticamente
        
        // El versionCode debe ser único y creciente
        versionCode = buildNumber
        
        // El versionName muestra la versión semántica con sufijo -alpha para indicar prelanzamiento
        versionName = "$majorVersion.$minorVersion.$patchVersion-alpha"
        
        println("Configuración de versión:")
        println("versionCode: $versionCode")
        println("versionName: $versionName")
        println("Build Number: $buildNumber")

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
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-database")
    
    // Google Play Services
    implementation("com.google.android.gms:play-services-base:18.3.0")
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    
    // ML Kit para escanear códigos de barras
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    
    // ZXing para generar códigos QR
    implementation("com.google.zxing:core:3.5.1")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}