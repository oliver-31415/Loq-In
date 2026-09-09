import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val loqinVersionCode = 228
val loqinVersionName = "2.2.8"

val loqinSecretPropertiesFile = rootProject.file("signing.properties")
val loqinSecretProperties = Properties().apply {
    if (loqinSecretPropertiesFile.isFile) {
        loqinSecretPropertiesFile.inputStream().use { input -> load(input) }
    }
}

fun loqinSecretProperty(name: String) = providers.gradleProperty(name)
    .orElse(providers.environmentVariable(name))
    .orElse(providers.provider { loqinSecretProperties.getProperty(name) ?: "" })

fun String.loqinTrimUnquoted(): String {
    val trimmed = trim()
    if (trimmed.length >= 2) {
        val first = trimmed.first()
        val last = trimmed.last()
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return trimmed.substring(1, trimmed.length - 1).trim()
        }
    }
    return trimmed
}

fun Provider<String>.loqinTrimmedUnquoted(): Provider<String> = map { it.loqinTrimUnquoted() }

val mapsApiKey = loqinSecretProperty("MAPS_API_KEY").loqinTrimmedUnquoted()

val releaseStoreFile = loqinSecretProperty("LOQIN_RELEASE_STORE_FILE")
val releaseStorePassword = loqinSecretProperty("LOQIN_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = loqinSecretProperty("LOQIN_RELEASE_KEY_ALIAS")
val releaseKeyPassword = loqinSecretProperty("LOQIN_RELEASE_KEY_PASSWORD")
val releaseSigningConfigured = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it.get().isNotBlank() }

android {
    namespace = "com.oliver.loqin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.oliver.loqin"
        minSdk = 27
        targetSdk = 36

        versionCode = loqinVersionCode
        versionName = loqinVersionName

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey.get()

        buildConfigField("boolean", "LOQIN_HAS_MAPS_API_KEY", mapsApiKey.get().isNotBlank().toString())
        buildConfigField("boolean", "LOQIN_RELEASE_SIGNING_CONFIGURED", releaseSigningConfigured.toString())
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    buildTypes {
        debug {
            // Separate app id so local builds co-install with the Play Store release
            // instead of being blocked by version/signature mismatch.
            applicationIdSuffix = ".loqindev"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

// Shared dependency declarations for the Android app module.
dependencies {

    // AndroidX core, UI, and preferences
    add("implementation", "androidx.preference:preference-ktx:1.2.1")
    add("implementation", "androidx.core:core-ktx:1.18.0")
    add("implementation", "androidx.appcompat:appcompat:1.7.1")
    add("implementation", "com.google.android.material:material:1.13.0")

    // Local statistics archive (Room). SharedPreferences remain the compatibility cache while
    // Room stores the durable, structured copy used for long-range history and backup/restore.
    val roomVersion = "2.8.4"
    add("implementation", "androidx.room:room-runtime:$roomVersion")
    add("ksp", "androidx.room:room-compiler:$roomVersion")

    // AndroidX biometric (app lock / uninstall protection)
    add("implementation", "androidx.biometric:biometric:1.1.0")

    // Coroutines helpers (lifecycleScope) + DataStore (for small user prefs)
    add("implementation", "androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    add("implementation", "androidx.datastore:datastore-preferences:1.2.1")

    // Location, maps, and Play Store in-app updates
    add("implementation", "com.google.android.gms:play-services-location:21.3.0")
    add("implementation", "com.google.android.gms:play-services-maps:20.0.0")
    add("implementation", "com.google.android.play:app-update-ktx:2.1.0")

    // CameraX
    val camerax = "1.6.1"
    add("implementation", "androidx.camera:camera-core:$camerax")
    add("implementation", "androidx.camera:camera-camera2:$camerax")
    add("implementation", "androidx.camera:camera-lifecycle:$camerax")
    add("implementation", "androidx.camera:camera-view:$camerax")

    // Provides com.google.common.util.concurrent.ListenableFuture for CameraX
    add("implementation", "com.google.guava:guava:33.5.0-android")

    // Barcode scanning and QR generation
    add("implementation", "com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    add("implementation", "com.google.zxing:core:3.5.4")
}

// Fix for duplicated classes: com.intellij:annotations vs org.jetbrains:annotations.
configurations.configureEach {
    exclude(group = "com.intellij", module = "annotations")
}
