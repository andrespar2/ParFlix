package com.andrespar2.parflix.core.network

import android.util.Log
import com.andrespar2.parflix.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Factoría del cliente HTTP.
 *
 * - HTTPS obligatorio (cleartext deshabilitado en el manifiesto).
 * - Gzip automático (OkHttp lo negocia por defecto).
 * - Reintentos a nivel de conexión; el backoff por 429/5xx se gestiona en la capa
 *   de repositorio para poder respetar `Retry-After`.
 * - Registro seguro: NUNCA se imprimen cabeceras de autorización ni URLs con
 *   `view_key`/`api_key`.
 */
object HttpClients {

    private const val CONNECT_TIMEOUT_S = 20L
    private const val READ_TIMEOUT_S = 30L
    private const val CALL_TIMEOUT_S = 60L

    fun base(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .writeTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_S, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .addInterceptor(SafeLoggingInterceptor())
        .build()
}

/**
 * Interceptor de registro que redacta cualquier dato sensible antes de escribir
 * en logcat: cabeceras de autenticación y parámetros de consulta con claves.
 */
class SafeLoggingInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!BuildConfig.DEBUG) return chain.proceed(chain.request())
        val request = chain.request()
        val started = System.currentTimeMillis()
        val response = try {
            chain.proceed(request)
        } catch (t: Throwable) {
            Log.w(TAG, "${request.method} ${redact(request.url.toString())} -> ${t.javaClass.simpleName}")
            throw t
        }
        if (VERBOSE) {
            Log.d(
                TAG,
                "${request.method} ${redact(request.url.toString())} -> ${response.code} " +
                    "(${System.currentTimeMillis() - started} ms)"
            )
        }
        return response
    }

    companion object {
        private const val TAG = "ParFlix/Http"
        private const val VERBOSE = false

        /** Elimina valores de parámetros sensibles de una URL para poder registrarla. */
        fun redact(url: String): String = url
            .replace(Regex("(view_key=)[^&]+"), "$1***")
            .replace(Regex("(api_key=)[^&]+"), "$1***")
    }
}
