# ══════════════════════════════════════════════════════════════════════
# BudGi ProGuard Rules — Production-grade for finance app
# ══════════════════════════════════════════════════════════════════════

# ── Retrofit & OkHttp ────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# OkHttp WebSocket classes
-keep class okhttp3.internal.ws.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# ── Coroutines ───────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ── Room ─────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ── Serialization ────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKd
-keep,allowobfuscation,allowshrinking class kotlinx.serialization.internal.**
-keep,allowobfuscation,allowshrinking class kotlinx.serialization.**

# ── Hilt ─────────────────────────────────────────────────────────────
-keep,allowobfuscation,allowshrinking @interface dagger.hilt.**
-keep class dagger.hilt.** { *; }

# ── Data Models (Keep domain models for serialization) ───────────────
-keep class com.app.domain.model.** { *; }
-keep class com.app.data.remote.dto.** { *; }

# ── Coil Image Loading ──────────────────────────────────────────────
-keep class coil.** { *; }
-dontwarn coil.**

# ── AndroidX Biometric ──────────────────────────────────────────────
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# ── AndroidX Credentials / Google Sign-In ────────────────────────────
-keep class androidx.credentials.** { *; }
-dontwarn androidx.credentials.**
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.android.libraries.identity.googleid.**

# ── AndroidX Security (EncryptedSharedPreferences) ──────────────────
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ── Gson (used by Retrofit converter) ───────────────────────────────
-keepattributes EnclosingMethod
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ── Prevent R8 from removing error info ─────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
