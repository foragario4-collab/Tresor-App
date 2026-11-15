plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.glass.tresor"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.glass.tresor"
        minSdk = 30
        targetSdk = 35
        // GitHub setzt die Build-Nummer automatisch → jede neue Version hat eine höhere Nummer
        val build = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.$build"
        val repo = (project.findProperty("githubRepo") as String?) ?: ""
        buildConfigField("String", "GITHUB_REPO", "\"$repo\"")
    }

    // Signierschlüssel kommt aus GitHub Secrets (nie im Repo!).
    // Fehlt er (z. B. lokaler Test-Build), wird mit dem Debug-Schlüssel signiert.
    val ksFile = file("release.jks")
    val ksPass = System.getenv("KEYSTORE_PASSWORD")
    val hasReleaseKey = ksFile.exists() && !ksPass.isNullOrEmpty()
    signingConfigs {
        if (hasReleaseKey) {
            create("tresor") {
                storeFile = ksFile
                storePassword = ksPass
                keyAlias = "tresor"
                keyPassword = ksPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("tresor") else signingConfigs.getByName("debug")
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
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        resources.excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        // Tor-Bibliothek muss als Datei entpackt werden
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-ui:1.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Chat: Tor (Onion-Dienste), Schlüsselaustausch X25519, QR-Codes
    implementation("info.guardianproject:tor-android:0.4.9.12")
    implementation("info.guardianproject:jtorctl:0.4.5.7")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
