package com.andrespar2.parflix.data.remote

import com.andrespar2.parflix.data.remote.dto.TmdbGenreListDto
import com.andrespar2.parflix.data.remote.dto.TmdbListItemPageDto
import com.andrespar2.parflix.data.remote.dto.TmdbMovieDto
import com.andrespar2.parflix.data.remote.dto.TmdbSeasonDto
import com.andrespar2.parflix.data.remote.dto.TmdbTvDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API de TMDB (`https://api.themoviedb.org/3`).
 *
 * - La autenticación (`Authorization: Bearer`) y los parámetros comunes
 *   (`language=es-CO,es,en`, `include_adult=false`) los añaden interceptores.
 * - Las fichas usan `append_to_response` para obtener todo lo posible en una
 *   sola petición y respetar los límites del proveedor.
 * - El anime se consulta como TV (así lo cataloga TMDB).
 */
interface TmdbApi {

    @GET("movie/{id}")
    suspend fun movie(
        @Path("id") id: Int,
        @Query("append_to_response") append: String = APPEND_MOVIE,
    ): TmdbMovieDto

    @GET("tv/{id}")
    suspend fun tv(
        @Path("id") id: Int,
        @Query("append_to_response") append: String = APPEND_TV,
    ): TmdbTvDto

    @GET("tv/{id}/season/{season}")
    suspend fun season(
        @Path("id") id: Int,
        @Path("season") season: Int,
    ): TmdbSeasonDto

    @GET("genre/movie/list")
    suspend fun movieGenres(): TmdbGenreListDto

    @GET("genre/tv/list")
    suspend fun tvGenres(): TmdbGenreListDto

    /**
     * Descubre títulos de un género. Se usa para poblar las filas por categoría
     * del catálogo local (solo se conservan los títulos que ya existen y se
     * pueden ver en ParFlix).
     */
    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("with_genres") genreId: Int,
        @Query("page") page: Int,
        @Query("sort_by") sortBy: String = POPULARITY_DESC,
    ): TmdbListItemPageDto

    @GET("discover/tv")
    suspend fun discoverTv(
        @Query("with_genres") genreId: Int,
        @Query("page") page: Int,
        @Query("sort_by") sortBy: String = POPULARITY_DESC,
    ): TmdbListItemPageDto

    /** Búsqueda remota (complemento opcional de la búsqueda local). */
    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
    ): TmdbListItemPageDto

    companion object {
        const val APPEND_MOVIE =
            "alternative_titles,translations,credits,videos,external_ids,recommendations,similar,release_dates"
        const val APPEND_TV =
            "alternative_titles,translations,credits,videos,external_ids,recommendations,similar,content_ratings"

        /** Orden por defecto de los descubrimientos por género. */
        const val POPULARITY_DESC = "popularity.desc"
    }
}
