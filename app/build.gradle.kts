import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(keystorePropertiesFile.inputStream())
    }
}

val envPropertiesFile = rootProject.file(".env")
val envProperties = Properties().apply {
    if (envPropertiesFile.exists()) {
        load(envPropertiesFile.inputStream())
    }
}

fun envString(key: String, default: String = ""): String =
    envProperties.getProperty(key, System.getenv(key) ?: default)

android {
    namespace = "com.nendo.argosy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nendo.argosy"
        minSdk = 26
        targetSdk = 35
        versionCode = 164
        versionName = "0.18.1-beta.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

        buildConfigField("String", "TITLEDB_API_SECRET", "\"${envString("TITLEDB_API_SECRET")}\"")
        buildConfigField("String", "TITLEDB_API_URL", "\"${envString("TITLEDB_API_URL", "https://api.argosy.dev")}\"")
        buildConfigField("String", "CHEATSDB_API_SECRET", "\"${envString("CHEATSDB_API_SECRET")}\"")
        buildConfigField("String", "RA_API_KEY", "\"${envString("RA_API_KEY")}\"")
        val ucdataPath = envString("UCDATA_PATH").ifEmpty { null }?.let {
            it.replace(Regex("\\\\u([0-9A-Fa-f]{4})")) { match ->
                match.groupValues[1].toInt(16).toChar().toString()
            }
        } ?: ""
        buildConfigField("String", "UCDATA_PATH", "\"$ucdataPath\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
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
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

val runIntegrationTests = project.hasProperty("runIntegrationTests")

tasks.withType<Test> {
    if (name == "testDebugUnitTest" || name == "testReleaseUnitTest") {
        if (!runIntegrationTests) {
            exclude("**/integration/**")
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.lifecycle)
    implementation(libs.kotlinx.coroutines.android)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // TV Compose
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Network
    implementation(libs.bundles.network)
    ksp(libs.moshi.kotlin)

    // Image loading
    implementation(libs.coil.compose)

    // Color extraction
    implementation(libs.androidx.palette)

    // Archive extraction (7z, tar, etc.)
    implementation(libs.commons.compress)
    implementation(libs.xz)

    // Libretro (built-in emulation) - local module for customization
    implementation(project(":libretrodroid"))

    // WorkManager
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
