package com.andrespar2.parflix.data.remote

import com.andrespar2.parflix.data.remote.dto.VimeusEnvelope
import com.andrespar2.parflix.data.remote.dto.VimeusItemDto
import com.andrespar2.parflix.data.remote.dto.VimeusListingData
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * API pública de Vimeus. La autenticación (`X-API-Key`) se añade con un
 * interceptor para no exponer la clave en el código de la interfaz.
 */
interface VimeusApi {

    @GET("api/listing/movies")
    suspend fun movies(@Query("page") page: Int): VimeusEnvelope<VimeusListingData<VimeusItemDto>>

    @GET("api/listing/series")
    suspend fun series(@Query("page") page: Int): VimeusEnvelope<VimeusListingData<VimeusItemDto>>

    @GET("api/listing/animes")
    suspend fun animes(@Query("page") page: Int): VimeusEnvelope<VimeusListingData<VimeusItemDto>>

    /** Episodios de una serie/anime: filtros `tmdb_id` y `season`, con paginación. */
    @GET("api/listing/episodes")
    suspend fun episodes(
        @Query("tmdb_id") tmdbId: Int,
        @Query("season") season: Int,
        @Query("page") page: Int = 1,
    ): VimeusEnvelope<VimeusListingData<VimeusItemDto>>
}
