# Gson: mantieni i modelli di dati usati con reflection
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**

-keep class com.google.gson.** { *; }
-keep class com.sidekick.app.data.** { *; }

# ARCore / SceneView
-keep class com.google.ar.** { *; }
-keep class io.github.sceneview.** { *; }
-dontwarn com.google.ar.**
-dontwarn io.github.sceneview.**
