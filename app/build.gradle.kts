plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "app.pgpkeydesk"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.pgpkeydesk"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        resourceConfigurations += listOf("en", "de")
    }

    val ksPath = System.getenv("PGPKEYDESK_KEYSTORE")
    signingConfigs {
        if (ksPath != null) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("PGPKEYDESK_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PGPKEYDESK_KEY_ALIAS")
                keyPassword = System.getenv("PGPKEYDESK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (ksPath != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources.excludes += setOf(
            "META-INF/versions/**", "META-INF/DEPENDENCIES", "META-INF/AL2.0", "META-INF/LGPL2.1",
            "META-INF/*.kotlin_module", "META-INF/MANIFEST.MF", "META-INF/BCKEY.*", "META-INF/BCKEY.DSA", "META-INF/*.SF"
        )
    }
    lint { abortOnError = true; warningsAsErrors = false }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    val bc = "1.78.1"
    implementation("org.bouncycastle:bcpg-jdk18on:$bc")
    implementation("org.bouncycastle:bcprov-jdk18on:$bc")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
