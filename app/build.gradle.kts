import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("androidx.navigation.safeargs.kotlin")
    id("com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

fun localProperty(name: String, fallback: String): String =
    localProperties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

// Values for BuildConfig fields, computed once (plain concatenation keeps the
// buildConfigField arguments free of nested string templates).
val apiBaseUrlProp: String = localProperty("api.baseUrl", "")
val grokApiKeyProp: String = localProperty("grok.apiKey", "")
val grokModelProp: String = localProperty("grok.model", "grok-4")
val grokBaseUrlProp: String = localProperty("grok.baseUrl", "https://api.x.ai/v1/")

android {
    namespace = "com.studytrack.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.studytrack.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // StudyTrack REST API base URL. Blank (the default) = LOCAL MODE:
        // subjects/tasks/progress live on-device and no backend is contacted.
        buildConfigField("String", "API_BASE_URL", "\"" + apiBaseUrlProp + "\"")

        // Direct LLM ("AI brain") configuration. When GROK_API_KEY is blank
        // the app uses the StudyTrack backend's /api/ai/task-assistance.
        buildConfigField("String", "GROK_API_KEY", "\"" + grokApiKeyProp + "\"")
        buildConfigField("String", "GROK_MODEL", "\"" + grokModelProp + "\"")
        buildConfigField("String", "GROK_BASE_URL", "\"" + grokBaseUrlProp + "\"")
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
        buildConfig = true
    }
}

dependencies {
    // AndroidX core UI
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.11.0")

    // Lifecycle / ViewModel (StateFlow + repeatOnLifecycle)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")

    // Navigation Component (single-Activity architecture)
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // Networking: Retrofit + OkHttp + kotlinx.serialization
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Firebase Authentication (email/password)
    // Google Sign-In: ID-token flow, exchanged for a Firebase credential in
    // AuthRepository. Needs the Google provider enabled in the Firebase
    // console plus this build's SHA-1 fingerprint registered.
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    implementation(platform("com.google.firebase:firebase-bom:32.8.1"))
    implementation("com.google.firebase:firebase-auth")

    // Unit tests
    testImplementation("junit:junit:4.13.2")
}
