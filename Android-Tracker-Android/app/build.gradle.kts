plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.devtools.ksp")
    alias(libs.plugins.kotlin.compose)  // Adicionado para KSP
}

android {
    namespace = "dev.carlosalberto.locationtrackerapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.carlosalberto.locationtrackerapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"  // Verifique a versão mais recente!
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-Xopt-in=kotlin.RequiresOptIn",
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:suppressKotlinVersionCompatibilityCheck=true"
        )
    }

    signingConfigs {
        create("release") {
            storeFile = file("C:\\NAS-Carlos\\Dev\\Android\\AndroidStudioProjects\\LocationTrackerApp\\keystore.jks")
            storePassword = "pecharma"
            keyAlias = "ainz"
            keyPassword = "pecharma"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true  // Corrigido: 'isMinifyEnabled' em vez de 'minifyEnabled'
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        getByName("debug") {
            isMinifyEnabled = false  // Para evitar problemas no modo debug
        }
    }

    buildTypes.all {
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${project.findProperty("API_BASE_URL") ?: "https://api.seuproprio.com/api/"}\""
        )
    }


}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)

    // Google Play Services Location
    implementation(libs.play.services.location)

    // Retrofit para comunicação HTTP
    implementation(libs.retrofit)
    implementation(libs.converter.gson)

    // Room para armazenamento local
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)  // Esta linha deve funcionar agora

    // Coroutines para operações assíncronas
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.room.ktx)

    // Compose BOM para gerenciar versões automaticamente
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.ui)
    implementation(libs.androidx.material)
    implementation(libs.ui.tooling.preview)
    implementation(libs.activity.compose)

    // Dependências do Jetpack Compose
    implementation(libs.androidx.compose.ui.ui)
    implementation(libs.material3)
    implementation(libs.androidx.compose.ui.ui.tooling.preview)
    implementation(libs.androidx.activity.activity.compose)

}

