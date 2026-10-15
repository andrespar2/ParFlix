# Reglas R8/ProGuard de ParFlix.
# La configuración por defecto de Android ya conserva lo necesario para
# Retrofit, OkHttp y Moshi (rules empaquetadas en las propias librerías).

# -- Moshi: mantener DTOs y adaptadores generados (se localizan por reflexión) --
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep class com.andrespar2.parflix.data.remote.dto.** { *; }
-keep class **JsonAdapter { *; }
-keep @com.squareup.moshi.JsonQualifier class *

# -- Retrofit: interfaces y anotaciones --
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# -- Reglas de la app --
# El generador de secretos ofuscados debe conservarse para decodificar en runtime.
-keep class com.andrespar2.parflix.core.security.BuildTimeSecrets { *; }
-keep class com.andrespar2.parflix.core.security.SecretVault { *; }

# WorkManager
-dontwarn androidx.work.**

# WebView + JavaScript del reproductor
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
