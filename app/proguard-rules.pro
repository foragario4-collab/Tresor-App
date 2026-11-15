# R8: Code verschleiern, aber alles behalten, was per Reflection oder JNI genutzt wird.

# Tor (native Bibliothek + Steuerung)
-keep class org.torproject.** { *; }
-keep class net.freehaven.tor.control.** { *; }
-dontwarn org.torproject.**
-dontwarn net.freehaven.tor.control.**

# BouncyCastle (X25519, HKDF) – nutzt intern Reflection
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**

# QR-Codes
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.google.zxing.**

# Media3 (Videoplayer)
-dontwarn androidx.media3.**

# Nur beim Kompilieren gebrauchte Hinweis-Klassen, fehlen zur Laufzeit absichtlich
# (sonst bricht R8 mit "Missing classes detected" ab)
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.jetbrains.annotations.**
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Native Methoden nie umbenennen
-keepclasseswithmembernames class * {
    native <methods>;
}

# Fehlermeldungen behalten Zeilennummern, Dateinamen werden verschleiert
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute x

# Enum-Namen werden gespeichert (Ordner-Schutz, Tarnung …) → dürfen sich zwischen Versionen nie ändern
-keep enum app.glass.tresor.** { *; }
