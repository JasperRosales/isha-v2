-dontwarn org.jsoup.**
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class dev.jasper.ishav2.**$$serializer { *; }
-keepclassmembers class dev.jasper.ishav2.** { *** Companion; }
-keepclasseswithmembers class dev.jasper.ishav2.** { kotlinx.serialization.KSerializer serializer(...); }

# MediaPipe LLM inference: the native engine calls back into these classes by name
# and ships no consumer rules of its own.
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
# protobuf-lite (used by MediaPipe) reads message fields reflectively.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
-dontwarn com.google.protobuf.**
