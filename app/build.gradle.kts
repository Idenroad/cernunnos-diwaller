plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ── Load .env file for cloud provider keys ──
// Reads .env from the project root if it exists, otherwise uses placeholders.
// See .env.example for instructions.
val envFile = rootProject.file(".env")
val envVars = mutableMapOf(
    "CERNUNNOS_GOOGLE_CLIENT_ID" to "REPLACE_WITH_YOUR_GOOGLE_CLIENT_ID",
    "CERNUNNOS_DROPBOX_APP_KEY" to "REPLACE_WITH_YOUR_DROPBOX_APP_KEY",
)
if (envFile.exists()) {
    envFile.readLines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
        val eqIdx = trimmed.indexOf('=')
        if (eqIdx > 0) {
            val key = trimmed.substring(0, eqIdx).trim()
            val value = trimmed.substring(eqIdx + 1).trim()
            envVars[key] = value
        }
    }
}

android {
    namespace = "com.cernunnos.authenticator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cernunnos.authenticator"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // AppAuth redirect scheme placeholder
        manifestPlaceholders["appAuthRedirectScheme"] = "com.cernunnos.authenticator"

        // Cloud provider keys — injected from .env at build time
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"${envVars["CERNUNNOS_GOOGLE_CLIENT_ID"]}\"")
        buildConfigField("String", "DROPBOX_APP_KEY", "\"${envVars["CERNUNNOS_DROPBOX_APP_KEY"]}\"")
    }

    // ── Release signing ──
    // The keystore path and password can be overridden via environment variables
    // for CI/CD builds. Defaults to a local keystore in the project root.
    val keystorePath = System.getenv("CERNUNNOS_KEYSTORE_PATH")
        ?: rootProject.file("cernunnos-release.keystore").absolutePath
    val keystorePass = System.getenv("CERNUNNOS_KEYSTORE_PASS") ?: "cernunnos-diwaller-2024"
    val keyAliasEnv = System.getenv("CERNUNNOS_KEY_ALIAS") ?: "cernunnos"
    val keyPassEnv = System.getenv("CERNUNNOS_KEY_PASS") ?: "cernunnos-diwaller-2024"
    if (File(keystorePath).exists()) {
        signingConfigs {
            create("release") {
                storeFile = File(keystorePath)
                storePassword = keystorePass
                keyAlias = keyAliasEnv
                keyPassword = keyPassEnv
            }
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
            if (File(keystorePath).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/**/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)

    implementation(libs.security.crypto)
    implementation(libs.biometric)
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.github.mwiede:jsch:0.2.17")
    implementation("androidx.browser:browser:1.8.0")
    implementation("net.openid:appauth:0.11.1")

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.zxing.core)
    implementation(libs.mlkit.barcode)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    implementation(libs.bouncycastle)
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0") {
        exclude(group = "org.bouncycastle")
    }

    testImplementation(libs.junit)
    testImplementation("org.mockito:mockito-core:5.12.0")
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.security:security-crypto:1.1.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
