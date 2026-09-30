# Rewordly R8 / ProGuard rules.
# Retrofit, OkHttp, kotlinx.serialization, Room and Hilt ship their own consumer rules.

# Keep generic signatures and annotations used by Retrofit service interfaces.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault

# Network DTOs are (de)serialized via generated serializers; keep their companions.
-keep,includedescriptorclasses class com.rewordly.app.core.network.model.**$$serializer { *; }
-keepclassmembers class com.rewordly.app.core.network.model.** {
    *** Companion;
}

# Type-safe navigation routes are serialized by name.
-keep class com.rewordly.app.core.navigation.** { *; }

# Keep line numbers for readable crash reports, hide original source file names.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
