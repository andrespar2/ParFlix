package com.andrespar2.parflix.core.network

import com.andrespar2.parflix.core.security.CredentialStore
import com.andrespar2.parflix.data.remote.TmdbApi
import com.andrespar2.parflix.data.remote.VimeusApi
import com.squareup.moshi.Moshi
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Construye los clientes de Vimeus y TMDB. Cada dispositivo consulta
 * directamente los proveedores: no hay backend intermediario.
 */
class Providers(credentialStore: CredentialStore) {

    private val moshi: Moshi = Moshi.Builder().build()

    private val baseClient = HttpClients.base()

    val vimeus: VimeusApi = Retrofit.Builder()
        .baseUrl("https://vimeus.com/")
        .client(
            baseClient.newBuilder()
                .addInterceptor(VimeusAuthInterceptor { credentialStore.current() })
                .build()
        )
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(VimeusApi::class.java)

    val tmdb: TmdbApi = Retrofit.Builder()
        .baseUrl("https://api.themoviedb.org/3/")
        .client(
            baseClient.newBuilder()
                .addInterceptor(TmdbAuthInterceptor { credentialStore.current() })
                .addInterceptor(TmdbDefaultsInterceptor())
                .build()
        )
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(TmdbApi::class.java)
}
