package com.andrespar2.parflix.ads

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
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
import kotlinx.coroutines.delay
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
    private var lastBannerFailureAt = 0L

    private var lastNativeFailureAt = 0L

    private val isDebuggable: Boolean =
        (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    private val tag = "ParFlixAds"

    /**
     * Inicializa el SDK. Idempotente: las llamadas posteriores no hacen nada.
     * Debe llamarse desde [android.app.Application.onCreate].
     */
    @Suppress("DEPRECATION")
    @Synchronized
    fun init() {
        if (_isInitialized.value) {
            Log.d(tag, "init: SDK ya inicializado. isDebuggable=$isDebuggable, appId=${AdsConfig.START_IO_APP_ID}")
            return
        }

        Log.d(tag, "init: inicializando Start.io. isDebuggable=$isDebuggable, TEST_ADS_IN_DEBUG=${AdsConfig.TEST_ADS_IN_DEBUG}, appId=${AdsConfig.START_IO_APP_ID}")
        if (AdsConfig.TEST_ADS_IN_DEBUG && isDebuggable) {
            Log.d(tag, "init: habilitando test ads (DEBUG)")
            StartAppSDK.setTestAdsEnabled(true)
        }

        StartAppSDK.initParams(appContext, AdsConfig.START_IO_APP_ID)
            .setReturnAdsEnabled(AdsConfig.RETURN_ADS_ENABLED)
            .setCallback {
                Log.d(tag, "init: callback recibido - SDK inicializado")
                _isInitialized.value = true
            }
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
     * Indica si conviene esperar antes de volver a pedir el banner tras un fallo.
     */
    fun shouldSkipBannerRetry(): Boolean {
        if (lastBannerFailureAt == 0L) return false
        return System.currentTimeMillis() - lastBannerFailureAt < AdsConfig.AD_RETRY_COOLDOWN_MS
    }

    /**
     * Fuerza una nueva petición de banner si no hay uno cacheado.
     * No fuerza si ya está Ready.
     */
    fun requestBannerIfNeeded(force: Boolean = false) {
        if (bannerView != null && bannerState.value is AdSlotState.Ready) {
            Log.d(tag, "banner: ya existe vista cacheada, skip")
            return
        }
        if (!_isInitialized.value) {
            Log.d(tag, "banner: SDK no inicializado, no se solicita")
            return
        }
        if (!force && shouldSkipBannerRetry()) {
            Log.d(tag, "banner: cooldown tras fallo, skip. lastFailure=${lastBannerFailureAt}")
            return
        }
        if (bannerRequested && !force && bannerState.value is AdSlotState.Loading) {
            Log.d(tag, "banner: ya en carga")
            return
        }

        bannerRequested = true
        _bannerState.value = AdSlotState.Loading
        Log.d(tag, "banner: solicitando BannerRequest (320x50), force=$force")

        BannerRequest(appContext)
            .setAdFormat(BannerFormat.BANNER)
            .load { creator: BannerCreator?, error: String? ->
                if (creator != null && error == null) {
                    Log.d(tag, "banner: onLoad ok - creando vista")
                    val view = creator.create(appContext, object : BannerListener {
                        override fun onReceiveAd(banner: View) {
                            Log.d(tag, "banner: onReceiveAd")
                        }
                        override fun onFailedToReceiveAd(banner: View) {
                            Log.d(tag, "banner: onFailedToReceiveAd")
                            bannerView = null
                            lastBannerFailureAt = System.currentTimeMillis()
                            _bannerState.value = AdSlotState.Failed
                            bannerRequested = false
                        }
                        override fun onImpression(banner: View) {
                            Log.d(tag, "banner: onImpression")
                        }
                        override fun onClick(banner: View) {
                            Log.d(tag, "banner: onClick")
                        }
                    })
                    bannerView = view
                    lastBannerFailureAt = 0L
                    _bannerState.value = AdSlotState.Ready(view)
                    Log.d(tag, "banner: READY")
                } else {
                    Log.d(tag, "banner: onLoad error. error=$error creatorNull=${creator == null}")
                    bannerView = null
                    lastBannerFailureAt = System.currentTimeMillis()
                    _bannerState.value = AdSlotState.Failed
                    bannerRequested = false
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
        if (!_isInitialized.value) {
            // Espera breve a la inicialización si aún no ha terminado
            val start = System.currentTimeMillis()
            while (!_isInitialized.value && System.currentTimeMillis() - start < 5000) {
                kotlinx.coroutines.delay(50)
            }
        }
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
            Log.d(tag, "native: solicitando StartAppNativeAd (autoBitmap=true, adsNumber=1)")
            val preferences = NativeAdPreferences().apply {
                setAutoBitmapDownload(true)
                setAdsNumber(1)
            }

            val nativeAd = StartAppNativeAd(appContext)
            nativeAd.setPreferences(preferences)
            nativeAd.loadAd(object : AdEventListener {
                override fun onReceiveAd(ad: Ad) {
                    val details = nativeAd.nativeAds?.firstOrNull()
                    Log.d(tag, "native: onReceiveAd - details=${if (details == null) "null" else "present"} count=${nativeAd.nativeAds?.size ?: 0}")
                    if (continuation.isActive) continuation.resume(details)
                }

                override fun onFailedToReceiveAd(ad: Ad?) {
                    Log.d(tag, "native: onFailedToReceiveAd ad=$ad")
                    if (continuation.isActive) continuation.resume(null)
                }
            })
        }
}
