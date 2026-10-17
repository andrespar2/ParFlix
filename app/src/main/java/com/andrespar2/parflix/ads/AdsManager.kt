package com.andrespar2.parflix.ads

import android.content.Context
import android.content.pm.ApplicationInfo
import android.view.View
import com.startapp.sdk.ads.banner.BannerCreator
import com.startapp.sdk.ads.banner.BannerFormat
import com.startapp.sdk.ads.banner.BannerListener
import com.startapp.sdk.ads.banner.BannerRequest
import com.startapp.sdk.ads.nativead.NativeAdDetails
import com.startapp.sdk.ads.nativead.NativeAdPreferences
import com.startapp.sdk.ads.nativead.StartAppNativeAd
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Estado de una ranura publicitaria. */
sealed interface AdSlotState {
    /** Aún no se ha pedido nada. */
    data object Idle : AdSlotState

    /** Petición en curso. */
    data object Loading : AdSlotState

    /** Hay inventario disponible. */
    data class Ready(val ad: Any) : AdSlotState

    /** Sin inventario o error: la UI oculta el contenedor. */
    data object Failed : AdSlotState
}

/**
 * Punto único de contacto con el SDK de Start.io.
 *
 * Responsabilidades:
 *  - Inicializar el SDK una sola vez por proceso.
 *  - Cachear el banner para que no se repitan peticiones al navegar.
 *  - Cargar anuncios nativos bajo demanda, con control de frecuencia y
 *    enfriamiento tras fallos.
 *
 * No envía claves del catálogo, credenciales ni historial: el SDK solo
 * conoce el App ID y sus propios identificadores de anuncio.
 */
class AdsManager(context: Context) {

    private val appContext = context.applicationContext

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _bannerState = MutableStateFlow<AdSlotState>(AdSlotState.Idle)
    val bannerState: StateFlow<AdSlotState> = _bannerState.asStateFlow()

    private var bannerView: View? = null
    private var bannerRequested = false

    private var lastNativeFailureAt = 0L

    private val isDebuggable: Boolean =
        (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /**
     * Inicializa el SDK. Idempotente: las llamadas posteriores no hacen nada.
     * Debe llamarse desde [android.app.Application.onCreate].
     */
    @Suppress("DEPRECATION")
    @Synchronized
    fun init() {
        if (_isInitialized.value) return

        if (AdsConfig.TEST_ADS_IN_DEBUG && isDebuggable) {
            // Solo depuración: en release se piden anuncios reales.
            StartAppSDK.setTestAdsEnabled(true)
        }

        StartAppSDK.initParams(appContext, AdsConfig.START_IO_APP_ID)
            // `setReturnAdsEnabled` está obsoleto: Start.io gestiona los ads de
            // salida desde el portal. Se desactiva aquí igualmente para que la
            // APK no active formatos automáticos aunque el portal los habilite.
            .setReturnAdsEnabled(AdsConfig.RETURN_ADS_ENABLED)
            .setCallback { _isInitialized.value = true }
            .init()
    }

    /**
     * Registra el consentimiento del usuario (GDPR/CCPA) cuando ParFlix lo
     * obtenga. Si la app aún no gestiona consentimiento, el SDK aplica su
     * flujo por defecto por región; no hace falta llamar a este método.
     */
    fun setConsent(consent: String, timestampSeconds: Long, given: Boolean) {
        StartAppSDK.setUserConsent(appContext, consent, timestampSeconds, given)
    }

    // ---------------------------------------------------------------------
    // Banner
    // ---------------------------------------------------------------------

    /** Vista del banner ya cargada, o `null` si aún no hay inventario. */
    fun bannerView(): View? = bannerView

    /**
     * Pide el banner si aún no se ha cargado ni fallado. La vista se cachea:
     * navegar entre pestañas no vuelve a pedir el anuncio ni lo duplica.
     */
    fun requestBannerIfNeeded() {
        if (bannerRequested) return
        if (!_isInitialized.value) return

        bannerRequested = true
        _bannerState.value = AdSlotState.Loading

        BannerRequest(appContext)
            .setAdFormat(BannerFormat.BANNER)
            .load { creator: BannerCreator?, error: String? ->
                if (creator != null && error == null) {
                    val view = creator.create(appContext, object : BannerListener {
                        override fun onReceiveAd(banner: View) = Unit
                        override fun onFailedToReceiveAd(banner: View) {
                            bannerView = null
                            _bannerState.value = AdSlotState.Failed
                        }
                        override fun onImpression(banner: View) = Unit
                        override fun onClick(banner: View) = Unit
                    })
                    bannerView = view
                    _bannerState.value = AdSlotState.Ready(view)
                } else {
                    // Sin inventario: se oculta el contenedor y no se repite la
                    // petición. El SDK gestiona su propia recarga automática.
                    bannerView = null
                    _bannerState.value = AdSlotState.Failed
                }
            }
    }

    // ---------------------------------------------------------------------
    // Anuncios nativos
    // ---------------------------------------------------------------------

    /**
     * Carga un anuncio nativo con su imagen ya descargada.
     *
     * Devuelve `null` cuando el SDK no está inicializado, no hay inventario o
     * la petición falla. No reintenta en bucle: la frecuencia la decide quien
     * llama mediante [shouldSkipNativeRetry].
     */
    suspend fun loadNativeAd(): NativeAdDetails? {
        if (!_isInitialized.value) return null

        val ad = requestNativeAd()
        if (ad == null) lastNativeFailureAt = System.currentTimeMillis()
        return ad
    }

    /**
     * Indica si conviene esperar antes de volver a pedir un anuncio nativo
     * tras un fallo, para no saturar de peticiones mientras se desplaza el
     * catálogo.
     */
    fun shouldSkipNativeRetry(): Boolean {
        if (lastNativeFailureAt == 0L) return false
        return System.currentTimeMillis() - lastNativeFailureAt < AdsConfig.AD_RETRY_COOLDOWN_MS
    }

    private suspend fun requestNativeAd(): NativeAdDetails? =
        suspendCancellableCoroutine { continuation ->
            val preferences = NativeAdPreferences().apply {
                setAutoBitmapDownload(true)
                setAdsNumber(1)
            }

            val nativeAd = StartAppNativeAd(appContext)
            nativeAd.setPreferences(preferences)
            nativeAd.loadAd(object : AdEventListener {
                override fun onReceiveAd(ad: Ad) {
                    val details = nativeAd.nativeAds?.firstOrNull()
                    if (continuation.isActive) continuation.resume(details)
                }

                override fun onFailedToReceiveAd(ad: Ad?) {
                    if (continuation.isActive) continuation.resume(null)
                }
            })
        }
}
