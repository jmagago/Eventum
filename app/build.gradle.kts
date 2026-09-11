import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

val appVersion = Properties().apply {
    val versionFile = rootProject.file("version.properties")
    if (versionFile.exists()) {
        versionFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.us.eventum"
    compileSdk {
        version = release(37)
    }

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { localProperties.load(it) }
    }
    val qrSigningSecret = (localProperties.getProperty("qrSigningSecret")
        ?: "eventum-dev-qr-secret-change-in-prod")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

    defaultConfig {
        applicationId = "com.us.eventum"
        minSdk = 26
        targetSdk = 37
        buildConfigField("String", "QR_SIGNING_SECRET", "\"$qrSigningSecret\"")

        val majorVersion = appVersion.getProperty("major", "1").trim().toInt()
        val minorVersion = appVersion.getProperty("minor", "0").trim().toInt()
        val patchVersion = appVersion.getProperty("patch", "0").trim().toInt()

        versionCode = majorVersion * 10_000 + minorVersion * 100 + patchVersion
        // Quitar -alpha cuando publiquemos en Play Store
        versionName = "$majorVersion.$minorVersion.$patchVersion-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Configuración para compatibilidad con 16 KB page size
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    
    // Configuración para compatibilidad con 16 KB page size
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
    
    // Configuración de lint
    lint {
        lintConfig = file("lint.xml")
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
    implementation(libs.firebaseStorage)
    
    // Google Play Services
    implementation(libs.playServicesBase)
    implementation(libs.playServicesAuth)
    
    // CameraX (vista previa y análisis de frames para QR)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.guava)

    // ML Kit para escanear códigos de barras
    implementation(libs.mlkitBarcodeScanning)
    
    // ZXing para generar códigos QR
    implementation(libs.zxing)
    
    // Gson para serialización/deserialización JSON
    implementation(libs.gson)
    
    // CircleImageView para imágenes de perfil circulares
    implementation(libs.circleimageview)
    
    // Glide para cargar imágenes
    implementation(libs.glide)
    annotationProcessor(libs.glideCompiler)
    
    
    // Lifecycle components
    implementation(libs.lifecycleViewmodel)
    implementation(libs.lifecycleLivedata)
    
    // SwipeRefreshLayout
    implementation(libs.swiperefreshlayout)
    // Testing
    testImplementation(libs.junit4)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.espressoCore)
}