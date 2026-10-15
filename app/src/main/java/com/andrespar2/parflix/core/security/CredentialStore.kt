package com.andrespar2.parflix.core.security

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import java.util.concurrent.atomic.AtomicReference

/**
 * Punto único de acceso a credenciales.
 *
 * Orden de resolución:
 * 1. Credenciales introducidas por el usuario en este dispositivo, cifradas con
 *    AES-GCM + Android Keystore (nunca en claro en disco).
 * 2. Credenciales incorporadas en la APK, ofuscadas durante la compilación
 *    privada ([BuildTimeSecrets]).
 *
 * Las credenciales NO se cachean de forma permanente: se decodifican en cada
 * consulta para reducir su tiempo de vida en memoria. No se registran jamás.
 */
class CredentialStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val cachedOverride = AtomicReference<Credentials?>(null)

    /** Credenciales incorporadas en la APK (pueden estar vacías si se compiló sin secretos). */
    fun builtIn(): Credentials = Credentials(
        vimeusApiKey = BuildTimeSecrets.vimeusApiKey,
        vimeusViewKey = BuildTimeSecrets.vimeusViewKey,
        tmdbReadAccessToken = BuildTimeSecrets.tmdbReadAccessToken,
        tmdbApiKey = BuildTimeSecrets.tmdbApiKey,
    )

    /**
     * Credenciales efectivas para las peticiones. Se invoca en cada petición de
     * red; el coste de decodificación es despreciable.
     */
    fun current(): Credentials {
        val override = override()
        if (override.isComplete) return override
        val built = builtIn()
        return if (built.isComplete) {
            built
        } else {
            // Mezcla: completa lo que falte con las credenciales integradas.
            Credentials(
                vimeusApiKey = override.vimeusApiKey.ifBlank { built.vimeusApiKey },
                vimeusViewKey = override.vimeusViewKey.ifBlank { built.vimeusViewKey },
                tmdbReadAccessToken = override.tmdbReadAccessToken.ifBlank { built.tmdbReadAccessToken },
                tmdbApiKey = override.tmdbApiKey.ifBlank { built.tmdbApiKey },
            )
        }
    }

    /** Credenciales propias del usuario en este dispositivo (vacías si no hay). */
    fun override(): Credentials {
        cachedOverride.get()?.let { return it }
        val decoded = readOverrideFromDisk() ?: Credentials()
        cachedOverride.set(decoded)
        return decoded
    }

    fun hasOverride(): Boolean = readOverrideFromDisk()?.isEmpty == false

    fun hasUsableCredentials(): Boolean = current().isComplete

    fun saveOverride(credentials: Credentials) {
        val payload = listOf(
            credentials.vimeusApiKey,
            credentials.vimeusViewKey,
            credentials.tmdbReadAccessToken,
            credentials.tmdbApiKey,
        ).joinToString(SEPARATOR)
        val encrypted = KeystoreCipher.encrypt(payload.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_OVERRIDE, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
        cachedOverride.set(credentials)
    }

    fun clearOverride() {
        prefs.edit().remove(KEY_OVERRIDE).apply()
        cachedOverride.set(null)
    }

    /** Descripción enmascarada para mostrar en Ajustes (nunca el valor real). */
    fun describe(): String {
        val c = current()
        val parts = buildList {
            add(if (c.hasVimeus) "Catálogo: definida" else "Catálogo: no configurada")
            add(if (c.hasTmdb) "TMDB: definida" else "TMDB: no configurada")
            add(if (override().isComplete) "origen: usuario" else "origen: integrada en la APK")
        }
        return parts.joinToString(" · ")
    }

    @SuppressLint("ApplySharedPref")
    private fun readOverrideFromDisk(): Credentials? {
        val stored = prefs.getString(KEY_OVERRIDE, null) ?: return null
        return try {
            val blob = Base64.decode(stored, Base64.NO_WRAP)
            val plain = KeystoreCipher.decrypt(blob).toString(Charsets.UTF_8)
            val parts = plain.split(SEPARATOR)
            if (parts.size != 4) return null
            Credentials(
                vimeusApiKey = parts[0],
                vimeusViewKey = parts[1],
                tmdbReadAccessToken = parts[2],
                tmdbApiKey = parts[3],
            )
        } catch (t: Throwable) {
            // Si el Keystore se invalidó (restauración, cambio de bloqueo), se descarta
            // el contenido: no es recuperable y no debe romper la app.
            prefs.edit().remove(KEY_OVERRIDE).commit()
            null
        }
    }

    private companion object {
        const val PREFS_NAME = "parflix_secure"
        const val KEY_OVERRIDE = "credentials_enc_v1"
        const val SEPARATOR = "\u0000"
    }
}
