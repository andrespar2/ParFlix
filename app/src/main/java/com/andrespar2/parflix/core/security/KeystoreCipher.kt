package com.andrespar2.parflix.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifrado autenticado (AES-256-GCM) con una clave generada y custodiada por el
 * Android Keystore del dispositivo.
 *
 * La clave privada nunca sale del Keystore (en dispositivos con hardware
 * seguro, ni siquiera es exportable). Esto protege las credenciales que el
 * usuario introduzca manualmente en cada teléfono.
 *
 * Nota honesta: esto NO protege las credenciales incrustadas en la APK, porque
 * el APK es público y puede analizarse; solo protege datos almacenados en el
 * dispositivo (por ejemplo, claves personales del usuario).
 */
object KeystoreCipher {

    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "parflix_secure_store_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128

    /** Cifra [plain] y devuelve `iv || ciphertext` (el IV aleatorio va incluido). */
    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain)
        return iv + ciphertext
    }

    /** Descifra un blob generado por [encrypt]. Lanza excepción si fue alterado. */
    fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > IV_SIZE) { "Bloque cifrado inválido" }
        val iv = blob.copyOfRange(0, IV_SIZE)
        val ciphertext = blob.copyOfRange(IV_SIZE, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}
