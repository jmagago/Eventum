import java.io.File
import java.util.Properties

plugins {
    id("com.android.application") version "8.13.0"
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
        
        // Configuración para compatibilidad con 16 KB page size
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
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
    
    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(listOf("-Xlint:deprecation"))
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        viewBinding = true
    }
    
    // Configuración para compatibilidad con 16 KB page size
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(libs.coreKtx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    
    // Firebase
    implementation(platform(libs.firebaseBom))
    implementation(libs.firebaseAuth)
    implementation(libs.firebaseFirestore)
    implementation(libs.firebaseDatabase)
    implementation("com.google.firebase:firebase-storage")
    
    // Google Play Services
    implementation(libs.playServicesBase)
    implementation(libs.playServicesAuth)
    
    // ML Kit para escanear códigos de barras
    implementation(libs.mlkitBarcodeScanning)
    implementation(libs.playServicesMlkitBarcodeScanning)
    
    // ZXing para generar códigos QR
    implementation(libs.zxing)
    
    // Gson para serialización/deserialización JSON
    implementation(libs.gson)
    
    // CircleImageView para imágenes de perfil circulares
    implementation(libs.circleimageview)
    
    // Glide para cargar imágenes
    implementation(libs.glide)
    annotationProcessor(libs.glideCompiler)
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.espressoCore)
}