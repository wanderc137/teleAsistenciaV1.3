# Reglas de ProGuard específicas de la app (release)
# WebRTC
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
# ML Kit
-keep class com.google.mlkit.** { *; }
# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**
# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
