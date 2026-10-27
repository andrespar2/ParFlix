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

        val html = when (request.placement.format) {
            AdFormat.BANNER_320X50 -> BANNER_HTML
            AdFormat.NATIVE -> NATIVE_HTML
        }
        return AdResult.Success(AdCreative.WebTag(html = html, format = request.placement.format))
    }

    private companion object {
        /** Código 320x50 entregado por Adsterra GET CODE. */
        val BANNER_HTML = """
            <!DOCTYPE html>
            <html lang="es">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=320, initial-scale=1">
              <style>
                html, body { margin: 0; padding: 0; background: #000000; overflow: hidden; }
                iframe { border: 0; display: block; }
                body { display: flex; align-items: center; justify-content: center; }
              </style>
            </head>
            <body>
            <script>
              atOptions = {
                'key': '${AdConfiguration.BANNER_KEY}',
                'format': 'iframe',
                'height': 50,
                'width': 320,
                'params': {}
              };
            </script>
            <script src="${AdConfiguration.BANNER_SCRIPT_URL}"></script>
            </body>
            </html>
        """.trimIndent()

        /** Código Native Banner entregado por Adsterra GET CODE. */
        val NATIVE_HTML = """
            <!DOCTYPE html>
            <html lang="es">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <style>
                html, body { margin: 0; padding: 0; background: transparent; }
                body { font-family: sans-serif; }
              </style>
            </head>
            <body>
            <script async="async" data-cfasync="false"
              src="${AdConfiguration.NATIVE_SCRIPT_URL}"></script>
            <div id="container-${AdConfiguration.NATIVE_KEY}"></div>
            </body>
            </html>
        """.trimIndent()
    }
}
