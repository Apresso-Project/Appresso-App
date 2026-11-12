import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// URL base del backend. En debug se toma, en este orden, de:
//   1. la propiedad de Gradle  -Pappresso.api.baseUrl=...
//   2. appresso.api.baseUrl en android/local.properties (archivo personal, no se sube al repo)
//   3. http://10.0.2.2:8080/  (el alias que tiene el emulador para el localhost del computador)
// En un teléfono físico hay que poner la IP del computador en la red Wi-Fi (ver android/README.md).
val propiedadesLocales = Properties().apply {
    val archivo = rootProject.file("local.properties")
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}

// Retrofit exige que la URL base termine en "/".
fun conBarraFinal(url: String) = if (url.endsWith("/")) url else "$url/"

val urlBaseDebug = conBarraFinal(
    providers.gradleProperty("appresso.api.baseUrl").orNull
        ?: propiedadesLocales.getProperty("appresso.api.baseUrl")
        ?: "http://10.0.2.2:8080/"
)

android {
    namespace = "com.appresso.app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.appresso.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$urlBaseDebug\"")
        }
        release {
            // Aún no hay servidor desplegado (entregable 8.5.1). El dominio ".invalid" está reservado
            // y nunca resuelve: un release hecho antes de tiempo no puede hablar con nada por error.
            buildConfigField("String", "API_BASE_URL", "\"https://appresso.invalid/\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Red: llamadas HTTP al backend y conversión de JSON.
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    // Tiempo real (8.2.1): lectura del JSON de los eventos
    implementation(libs.gson)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
