import java.io.File
import java.util.Base64
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------
// Credenciales de compilación (privadas).
//
// El archivo `secrets.properties` (ignorado por Git) contiene los valores
// reales. Si no existe, se usan los valores vacíos de
// `secrets.defaults.properties` (versionado) para que el proyecto compile
// sin secretos.
//
// Los valores NUNCA se insertan como cadenas legibles: se generan ofuscados
// (XOR + Base64 + fragmentación) en una fuente Kotlin dentro de build/.
// Esto es una medida DISUASORIA, no un secreto criptográficamente protegido:
// cualquier persona con la APK puede recuperarlos mediante ingeniería inversa.
// ---------------------------------------------------------------------------

val defaultSecrets = file("secrets.defaults.properties")
val privateSecrets = rootProject.file("secrets.properties")
val secretsFile = if (privateSecrets.exists()) privateSecrets else defaultSecrets

// Se capturan rutas como valores serializables para que la caché de
// configuración de Gradle no guarde referencias al script.
val secretsInputFile: File = secretsFile
val secretsOutputDir: File = layout.buildDirectory.dir("generated/sources/parflixSecrets").get().asFile

val generateObfuscatedSecrets = tasks.register("generateObfuscatedSecrets") {
    inputs.file(secretsInputFile)
    outputs.dir(secretsOutputDir)
    doLast {
        val props = Properties()
        secretsInputFile.inputStream().use { props.load(it) }

        fun valueOf(key: String): String = props.getProperty(key)?.trim().orEmpty()

        // Clave XOR fija (solo disuasión; documentada como recuperable).
        val xorKey = "ParFlix-Obf-K3y-2026!".toByteArray(Charsets.UTF_8)

        fun encode(raw: String): List<String> {
            if (raw.isEmpty()) return listOf("", "", "")
            val bytes = raw.toByteArray(Charsets.UTF_8)
            val xored = ByteArray(bytes.size) { i -> (bytes[i].toInt() xor xorKey[i % xorKey.size].toInt()).toByte() }
            val b64 = Base64.getEncoder().encodeToString(xored)
            val chunk = (b64.length + 2) / 3
            // Fragmentos en orden invertido para dificultar la lectura casual.
            return listOf(
                b64.substring(0, chunk),
                b64.substring(chunk, (chunk * 2).coerceAtMost(b64.length)),
                b64.substring((chunk * 2).coerceAtMost(b64.length))
            ).reversed()
        }

        val outDir = secretsOutputDir
        val outFile = File(outDir, "com/andrespar2/parflix/core/security/BuildTimeSecrets.kt")
        outFile.parentFile.mkdirs()

        val f1 = encode(valueOf("provider.api.key"))
        val f2 = encode(valueOf("provider.view.key"))
        val f3 = encode(valueOf("tmdb.read.access.token"))
        val f4 = encode(valueOf("tmdb.api.key"))

        outFile.writeText(
            """
            // GENERADO AUTOMÁTICAMENTE por la tarea generateObfuscatedSecrets.
            // NO editar manualmente y NO versionar el archivo de origen de secretos.
            // Contenido ofuscado: recuperable por ingeniería inversa (disuasión, no secreto).
            package com.andrespar2.parflix.core.security

            import java.util.Base64

            internal object BuildTimeSecrets {
                private val k = byteArrayOf(${xorKey.joinToString(", ") { "0x${(it.toInt() and 0xFF).toString(16).padStart(2, '0')}" }})

                private val g0 = listOf(${f1.joinToString(", ") { "\"$it\"" }})
                private val g1 = listOf(${f2.joinToString(", ") { "\"$it\"" }})
                private val g2 = listOf(${f3.joinToString(", ") { "\"$it\"" }})
                private val g3 = listOf(${f4.joinToString(", ") { "\"$it\"" }})

                val vimeusApiKey: String get() = d(g0)
                val vimeusViewKey: String get() = d(g1)
                val tmdbReadAccessToken: String get() = d(g2)
                val tmdbApiKey: String get() = d(g3)

                private fun d(parts: List<String>): String {
                    if (parts.all { it.isEmpty() }) return ""
                    val joined = parts.reversed().joinToString("")
                    val bytes = Base64.getDecoder().decode(joined)
                    return String(bytes.mapIndexed { i, b -> (b.toInt() xor k[i % k.size].toInt()).toByte() }.toByteArray(), Charsets.UTF_8)
                }
            }
            """.trimIndent()
        )
    }
}

android {
    namespace = "com.andrespar2.parflix"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.andrespar2.parflix"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // Firma de distribución: leer `keystore.properties` (ignorado por Git)
        // o variables de entorno. Si no existen, `release` queda sin firma
        // explícita y se usa la de depuración para poder probar localmente.
        val ksProps = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f ->
            Properties().apply { f.inputStream().use { load(it) } }
        }
        create("release") {
            val storePath = ksProps?.getProperty("storeFile")
                ?: System.getenv("PARFLIX_STORE_FILE")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = ksProps?.getProperty("storePassword") ?: System.getenv("PARFLIX_STORE_PASSWORD")
                keyAlias = ksProps?.getProperty("keyAlias") ?: System.getenv("PARFLIX_KEY_ALIAS")
                keyPassword = ksProps?.getProperty("keyPassword") ?: System.getenv("PARFLIX_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
                ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets.getByName("main").java.srcDir(secretsOutputDir)

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests { isReturnDefaultValues = true }
    }
}

tasks.named("preBuild").configure { dependsOn(generateObfuscatedSecrets) }

dependencies {
    // AndroidX base
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Lifecycle + navegación
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Red: Retrofit + OkHttp + Moshi
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.moshi)
    ksp(libs.moshi.codegen)

    // Imágenes
    implementation(libs.coil.compose)
    // Publicidad Start.io
    implementation("com.startapp:inapp-sdk:5.2.5")

    // Trabajos en segundo plano y preferencias
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Pruebas
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
