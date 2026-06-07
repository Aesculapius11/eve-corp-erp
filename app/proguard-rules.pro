# EveCorpERP ProGuard Rules

# Keep Room entities and DAOs
-keep class com.evecorp.erp.data.local.entity.** { *; }
-keep class com.evecorp.erp.data.local.dao.** { *; }

# Keep Moshi DTOs
-keep class com.evecorp.erp.data.remote.dto.** { *; }

# Keep ESI API response models
-keepclassmembers class com.evecorp.erp.data.remote.dto.** {
    <init>(...);
}

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

# Moshi
-keep @com.squareup.moshi.JsonClass class * { *; }
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Hilt
-keepclasseswithmembers class * {
    @dagger.hilt.* <fields>;
}

# WorkManager
-keep class com.evecorp.erp.sync.SyncWorker { *; }

# Google Tink / security-crypto
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.api.client.http.**
-dontwarn com.google.api.client.util.**
-dontwarn org.joda.time.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-keep class com.google.crypto.tink.** { *; }
-keep class com.google.api.client.** { *; }
