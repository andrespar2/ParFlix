package com.andrespar2.parflix.ads

/**
 * Contrato de un proveedor publicitario.
 *
 * La implementación decide si está autorizada y entrega una creatividad. El
 * controlador se encarga del estado, la frecuencia y los reintentos, así que
 * añadir otro proveedor no obliga a tocar las pantallas.
 */
interface AdProvider {
    val id: String

    fun authorization(placement: AdPlacement): AdAuthorization

    suspend fun load(request: AdRequest): AdResult
}

data class AdRequest(val placement: AdPlacement)

/**
 * Proveedor Adsterra para Android.
 *
 * Las unidades configuradas son etiquetas web. La carga real permanece
 * deshabilitada mientras no exista autorización escrita de Adsterra para
 * usarlas dentro de una WebView de la APK. En ese estado [load] no abre
 * conexiones ni solicita impresiones: devuelve un error de autorización.
 */
class AdsterraWebProvider : AdProvider {

    override val id: String = "adsterra"

    override fun authorization(placement: AdPlacement): AdAuthorization =
        AdConfiguration.authorizationFor(placement.format)

    override suspend fun load(request: AdRequest): AdResult {
        val authorization = authorization(request.placement)
        if (authorization !is AdAuthorization.Authorized) {
            val reason = when (authorization) {
                is AdAuthorization.PendingWrittenConsent -> authorization.reason
                is AdAuthorization.Disabled -> authorization.reason
                is AdAuthorization.Authorized -> ""
            }
            return AdResult.Failure(
                AdFailure(AdFailureKind.UNAUTHORIZED, reason),
            )
        }

        val url = when (request.placement.format) {
            AdFormat.BANNER_320X50 -> AdConfiguration.BANNER_PAGE_URL
            AdFormat.NATIVE -> AdConfiguration.NATIVE_PAGE_URL
        }
        return AdResult.Success(
            AdCreative.WebTag(format = request.placement.format, url = url),
        )
    }
}
