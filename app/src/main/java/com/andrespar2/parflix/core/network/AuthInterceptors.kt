package com.andrespar2.parflix.core.network

import com.andrespar2.parflix.core.security.Credentials
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Cabecera de autenticación de Vimeus: `X-API-Key`.
 * Las credenciales se leen en cada petición para permitir su rotación en
 * caliente y reducir el tiempo de vida en memoria.
 */
class VimeusAuthInterceptor(private val credentials: () -> Credentials) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val key = credentials().vimeusApiKey
        val builder = chain.request().newBuilder()
        if (key.isBlank()) return chain.proceed(builder.build())
        return chain.proceed(builder.header("X-API-Key", key).build())
    }
}

/**
 * Autenticación de TMDB: `Authorization: Bearer <token>` (preferido).
 * Si solo existe la clave v3, se adjunta como parámetro `api_key`.
 */
class TmdbAuthInterceptor(private val credentials: () -> Credentials) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val creds = credentials()
        val request = chain.request()
        val builder = request.newBuilder()
        when {
            creds.tmdbReadAccessToken.isNotBlank() -> {
                builder.header("Authorization", "Bearer ${creds.tmdbReadAccessToken}")
            }
            creds.tmdbApiKey.isNotBlank() -> {
                val url = request.url.newBuilder().addQueryParameter("api_key", creds.tmdbApiKey).build()
                builder.url(url)
            }
        }
        return chain.proceed(builder.build())
    }
}

/**
 * Añade parámetros comunes a TMDB (idioma preferente y `include_adult=false`)
 * salvo que la petición ya los defina.
 */
class TmdbDefaultsInterceptor(
    private val languagePreference: String = "es-CO,es,en",
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original: HttpUrl = chain.request().url
        if (original.host != "api.themoviedb.org") return chain.proceed(chain.request())
        val builder = original.newBuilder()
        if (original.queryParameter("language") == null) builder.addQueryParameter("language", languagePreference)
        if (original.queryParameter("include_adult") == null) builder.addQueryParameter("include_adult", "false")
        return chain.proceed(chain.request().newBuilder().url(builder.build()).build())
    }
}
