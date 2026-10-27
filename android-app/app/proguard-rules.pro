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

# ── Room Database, DAOs & Entities ────────────────────────────────────
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public <methods>;
}
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class **_Impl { *; }
-keep interface com.app.data.local.dao.** { *; }
-keep class com.app.data.local.dao.** { *; }
-keep class com.app.data.local.entity.** { *; }
-keep class com.app.data.local.db.** { *; }
-dontwarn androidx.room.paging.**
-dontwarn androidx.room.**

# ── Serialization ────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKd
-keep,allowobfuscation,allowshrinking class kotlinx.serialization.internal.**
-keep,allowobfuscation,allowshrinking class kotlinx.serialization.**

# ── Hilt & Dagger ───────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep @interface dagger.hilt.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
    *;
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(...);
    *;
}
-keep class * extends androidx.lifecycle.ViewModelProvider$Factory { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep class * extends dagger.hilt.internal.TestSingletonComponent { *; }
-keep @dagger.hilt.android.EarlyEntryPoint class * { *; }
-keep @dagger.hilt.EntryPoint class * { *; }
-keep @dagger.hilt.EntryPoint interface * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.hilt.InstallIn interface * { *; }
-keep class com.app.di.** { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
    @javax.inject.Inject <methods>;
}

# ── Domain Models, Use Cases, Repositories & Retrofit APIs ──────────
-keep class com.app.domain.** { *; }
-keep class com.app.data.repository.** { *; }
-keep class com.app.data.remote.dto.** { *; }
-keep interface com.app.data.remote.api.** { *; }
-keep class com.app.data.remote.api.** { *; }
-keepclassmembers interface com.app.data.remote.api.** {
    <methods>;
}
-keep class com.app.presentation.viewmodel.** { *; }
-keepclassmembers class com.app.presentation.viewmodel.** {
    @javax.inject.Inject <init>(...);
}
-keep class com.app.data.updater.** { *; }
-keep class com.app.security.** { *; }

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

# ── AndroidX Security & Google Tink (EncryptedSharedPreferences) ────
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.shaded.protobuf.** { *; }
-dontwarn com.google.crypto.tink.shaded.protobuf.**
-keep class com.google.crypto.tink.subtle.** { *; }
-dontwarn com.google.crypto.tink.subtle.**
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
    *;
}

# ── AndroidX WorkManager & Workers ───────────────────────────────────
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.app.data.sync.** { *; }
-dontwarn com.app.data.sync.**
-keep class com.app.data.watchdog.** { *; }
-dontwarn com.app.data.watchdog.**
-keep class com.app.data.sms.** { *; }
-dontwarn com.app.data.sms.**

# ── Glance AppWidget ────────────────────────────────────────────────
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**
-keep class com.app.presentation.widget.** { *; }

# ── AndroidX SplashScreen ───────────────────────────────────────────
-keep class androidx.core.splashscreen.** { *; }
-dontwarn androidx.core.splashscreen.**

# ── Jetpack Compose ─────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── Gson (used by Retrofit converter) ───────────────────────────────
-keepattributes EnclosingMethod
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── Prevent R8 from removing error info ─────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
