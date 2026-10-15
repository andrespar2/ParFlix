package com.andrespar2.parflix.core.security

/**
 * Credenciales efectivas usadas por la app.
 *
 * Advertencia de seguridad (explícita y sin adornos):
 * - Las credenciales incorporadas en el APK son RECUPERABLES mediante ingeniería
 *   inversa. La ofuscación en tiempo de compilación (XOR + Base64 + fragmentado)
 *   solo disuade la lectura casual; no es confidencialidad criptográfica.
 * - Si un proveedor exige que una credencial sea imposible de extraer, esa
 *   condición es incompatible con una APK autónoma que deba funcionar sin
 *   configuración del usuario.
 * - Las credenciales que el usuario introduzca manualmente sí se guardan
 *   cifradas con AES-GCM respaldado por Android Keystore.
 */
data class Credentials(
    val vimeusApiKey: String = "",
    val vimeusViewKey: String = "",
    val tmdbReadAccessToken: String = "",
    val tmdbApiKey: String = "",
) {
    val hasVimeus: Boolean get() = vimeusApiKey.isNotBlank() && vimeusViewKey.isNotBlank()
    val hasTmdb: Boolean get() = tmdbReadAccessToken.isNotBlank() || tmdbApiKey.isNotBlank()
    val isComplete: Boolean get() = hasVimeus && hasTmdb
    val isEmpty: Boolean get() = !hasVimeus && !hasTmdb
}
