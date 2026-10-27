package com.andrespar2.parflix.ads

/** Formatos publicitarios que ParFlix sabe representar. */
enum class AdFormat {
    BANNER_320X50,
    NATIVE,
}

/** Ubicaciones estables: cada una tiene su propio estado y sus propios reintentos. */
enum class AdPlacement(
    val format: AdFormat,
    val label: String,
) {
    MAIN_BANNER(AdFormat.BANNER_320X50, AdConfiguration.AD_LABEL),
    CATALOG_NATIVE(AdFormat.NATIVE, AdConfiguration.SPONSORED_LABEL),
}

/** Resultado de la comprobación de autorización de un proveedor. */
sealed interface AdAuthorization {
    data class Authorized(val note: String) : AdAuthorization

    /** Hay una limitación pendiente de resolver con el proveedor. */
    data class PendingWrittenConsent(val reason: String) : AdAuthorization

    /** El formato está desactivado por configuración. */
    data class Disabled(val reason: String) : AdAuthorization
}

/** Tipos de fallo que la máquina de estados distingue. */
enum class AdFailureKind {
    NETWORK,
    TIMEOUT,
    NO_FILL,
    SERVER,
    RATE_LIMITED,
    PROVIDER,
    INVALID_RESPONSE,
    UNAUTHORIZED,
    CONFIGURATION,
    UNKNOWN,
    ;

    val isRecoverable: Boolean
        get() = when (this) {
            NETWORK, TIMEOUT, NO_FILL, SERVER, RATE_LIMITED, PROVIDER, INVALID_RESPONSE, UNKNOWN -> true
            UNAUTHORIZED, CONFIGURATION -> false
        }
}

/** Fallo normalizado, independiente del proveedor y del transporte. */
data class AdFailure(
    val kind: AdFailureKind,
    val message: String? = null,
    val httpCode: Int? = null,
    val retryAfterMs: Long? = null,
) {
    val isRecoverable: Boolean get() = kind.isRecoverable

    companion object {
        fun fromHttp(code: Int, retryAfterMs: Long? = null): AdFailure = when (code) {
            204 -> AdFailure(AdFailureKind.NO_FILL, "Sin inventario", httpCode = code)
            400, 401, 403, 404 -> AdFailure(
                AdFailureKind.CONFIGURATION,
                "Configuración rechazada por el proveedor",
                httpCode = code,
            )

            408 -> AdFailure(AdFailureKind.TIMEOUT, "Tiempo de espera agotado", httpCode = code)
            429 -> AdFailure(
                AdFailureKind.RATE_LIMITED,
                "Demasiadas solicitudes",
                httpCode = code,
                retryAfterMs = retryAfterMs,
            )

            in 500..599 -> AdFailure(
                AdFailureKind.SERVER,
                "Error temporal del proveedor",
                httpCode = code,
                retryAfterMs = retryAfterMs,
            )

            else -> AdFailure(AdFailureKind.UNKNOWN, "Respuesta HTTP $code", httpCode = code)
        }
    }
}

/** Creatividad entregada por un proveedor. */
sealed interface AdCreative {
    val format: AdFormat

    /**
     * Unidad web de Adsterra. En Android se carga una página dedicada del
     * sitio aprobado (`url`) para que el origen, las cookies y el script sean
     * reales; no se inyecta la etiqueta en un documento local con un origen
     * artificial.
     */
    data class WebTag(
        val html: String? = null,
        override val format: AdFormat,
        val url: String? = null,
    ) : AdCreative
}

sealed interface AdResult {
    data class Success(val creative: AdCreative) : AdResult

    data class Failure(val failure: AdFailure) : AdResult

    /** El proveedor respondió correctamente pero no había inventario. */
    data object NoFill : AdResult
}

/** Máquina de estados de un espacio publicitario. */
sealed interface AdLoadState {
    data object Idle : AdLoadState

    data object Loading : AdLoadState

    data class Ready(override val creative: AdCreative) : AdLoadState

    data class Visible(override val creative: AdCreative) : AdLoadState

    /**
     * Error recuperable. Se conserva el intento y la espera para que la UI (o
     * el siguiente primer plano) sepa que la recuperación sigue activa.
     */
    data class WaitingRetry(
        val attempt: Int,
        val delayMs: Long,
        val failure: AdFailure,
    ) : AdLoadState

    data class Paused(val reason: AdPauseReason) : AdLoadState

    data class Unauthorized(val reason: String) : AdLoadState

    data class Disabled(val reason: String) : AdLoadState

    /** Error definitivo: no se reintenta hasta corregir la configuración. */
    data class Unrecoverable(val failure: AdFailure) : AdLoadState

    val creative: AdCreative?
        get() = when (this) {
            is Ready -> creative
            is Visible -> creative
            else -> null
        }
}

enum class AdPauseReason {
    OFFLINE,
    APP_IN_BACKGROUND,
}

/** Resultado de evaluar si un fallo debe reintentarse. */
data class RetryDecision(
    val shouldRetry: Boolean,
    val attempt: Int,
    val delayMs: Long,
    val failure: AdFailure,
)
