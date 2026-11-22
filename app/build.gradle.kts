import java.security.KeyStore
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signierschlüssel kommt aus GitHub Secrets (nie im Repo!).
// Fehlt er (z. B. lokaler Test-Build), wird mit dem Debug-Schlüssel signiert.
val ksFile: File = file("release.jks")
val ksPass: String? = System.getenv("KEYSTORE_PASSWORD")
val hasReleaseKey = ksFile.exists() && !ksPass.isNullOrEmpty()

// Fingerabdruck (SHA-256) des Signier-Zertifikats → die App prüft beim Start, ob sie echt ist
val certSha256: String = if (hasReleaseKey) {
    // Schlägt das fehl, bricht der Build ab, statt die Prüfung still abzuschalten
    val ks = KeyStore.getInstance("PKCS12")
    ksFile.inputStream().use { input -> ks.load(input, ksPass!!.toCharArray()) }
    val cert = ks.getCertificate("tresor")
        ?: throw GradleException("Im Signierschlüssel fehlt der Eintrag 'tresor'.")
    MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        .joinToString("") { b -> "%02x".format(b) }
} else ""

android {
    namespace = "app.glass.tresor"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.glass.tresor"
        minSdk = 30
        targetSdk = 35
        // GitHub setzt die Build-Nummer automatisch → jede neue Version hat eine höhere Nummer
        // Versionsnummer = Minuten seit 1970 → steigt immer, egal welches Repo oder welcher Zähler
        val build = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1
        val code = (project.findProperty("versionCode") as String?)?.toIntOrNull()
            ?: (System.currentTimeMillis() / 60_000L).toInt()
        versionCode = code
        versionName = "1.$build"
        val repo = (project.findProperty("githubRepo") as String?) ?: ""
        buildConfigField("String", "GITHUB_REPO", "\"$repo\"")
        buildConfigField("String", "CERT_SHA256", "\"$certSha256\"")
        // Fingerabdruck des Quellcodes (von GitHub) – erkennt „gleiche Version, nur neu gebaut“
        val src = ((project.findProperty("sourceHash") as String?) ?: "").filter { it.isLetterOrDigit() }.take(40)
        buildConfigField("String", "SOURCE_HASH", "\"$src\"")
    }

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
            // Code verschleiern und verkleinern (R8)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    // Eigene Kamera (Fotos/Videos gehen direkt verschlüsselt in den Tresor)
    val camerax = "1.4.0"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-video:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    // Browser: Tor-Weiterleitung (Proxy) und Skripte vor dem Laden
    implementation("androidx.webkit:webkit:1.12.1")

    // Chat: Tor (Onion-Dienste), Schlüsselaustausch X25519, QR-Codes
    implementation("info.guardianproject:tor-android:0.4.8.16")
    implementation("info.guardianproject:jtorctl:0.4.5.7")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
