package com.andrespar2.parflix.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Resultado uniforme de una llamada de red. Permite distinguir errores de
 * conectividad, HTTP y formato sin usar excepciones en la UI.
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    /** Error de red: sin conexión, DNS, timeout, TLS, etc. */
    data class NetworkError(val cause: String) : ApiResult<Nothing>

    /** Error HTTP con código; 401/429/5xx se tratan de forma diferenciada. */
    data class HttpError(val code: Int, val message: String?) : ApiResult<Nothing>

    /** Respuesta recibida pero inesperada o no interpretable. */
    data class DataError(val cause: String) : ApiResult<Nothing>
}

/**
 * Ejecuta una llamada de red con reintentos y backoff exponencial.
 *
 * - Reintenta 429 y 5xx (respetando `Retry-After` si existe).
 * - No reintenta 400/401/404.
 * - No reintenta errores irrecuperables de conectividad de forma infinita.
 */
suspend fun <T> apiCall(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 500,
    maxDelayMs: Long = 8_000,
    block: suspend () -> T,
): ApiResult<T> {
    var attempt = 0
    var delayMs = initialDelayMs
    while (true) {
        attempt++
        try {
            return ApiResult.Success(block())
        } catch (ce: CancellationException) {
            throw ce
        } catch (http: HttpException) {
            val code = http.code()
            val retryable = code == 429 || code in 500..599
            if (retryable && attempt < maxAttempts) {
                val retryAfter = http.response()?.headers()?.get("Retry-After")
                    ?.trim()?.toLongOrNull()?.times(1000)
                delay((retryAfter ?: delayMs).coerceAtMost(30_000))
                delayMs = (delayMs * 2).coerceAtMost(maxDelayMs)
                continue
            }
            return ApiResult.HttpError(code, http.message())
        } catch (io: IOException) {
            val retryable = attempt < maxAttempts && io !is UnknownHostException
            if (retryable) {
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(maxDelayMs)
                continue
            }
            val cause = when (io) {
                is UnknownHostException -> "sin conexión o DNS no disponible"
                is SocketTimeoutException -> "tiempo de espera agotado"
                else -> io.message ?: io.javaClass.simpleName
            }
            return ApiResult.NetworkError(cause)
        } catch (t: Throwable) {
            return ApiResult.DataError(t.message ?: t.javaClass.simpleName)
        }
    }
}
