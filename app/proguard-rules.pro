# Keep rules for release minify (R8)

# Agora voice SDK (JNI callbacks)
-keep class io.agora.** { *; }
-dontwarn io.agora.**

# WebRTC (Stream)
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Gson / JSON models (reflection)
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Firebase / Firestore / Messaging
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Pusher Java client (uses SLF4J; Android has no StaticLoggerBinder → NOP logger)
-keep class com.pusher.** { *; }
-dontwarn com.pusher.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.**

# ML Kit face detection
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# AndroidX Biometric / Security crypto
-dontwarn androidx.biometric.**
-dontwarn androidx.security.crypto.**
