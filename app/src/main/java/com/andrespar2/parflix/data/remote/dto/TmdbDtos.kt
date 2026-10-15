package com.andrespar2.parflix.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ---------------------------------------------------------------------------
// DTOs de TMDB. Se declaran como `nullable` y con `ignoreUnknownKeys` implícito
// (Moshi ignora campos no declarados), porque TMDB añade campos con frecuencia
// y la app nunca debe fallar por un cambio menor del esquema.
// ---------------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class TmdbGenreDto(val id: Int? = null, val name: String? = null)

@JsonClass(generateAdapter = true)
data class TmdbGenreListDto(val genres: List<TmdbGenreDto>? = null)

@JsonClass(generateAdapter = true)
data class TmdbCompanyDto(
    val id: Int? = null,
    val name: String? = null,
    @Json(name = "logo_path") val logoPath: String? = null,
    @Json(name = "origin_country") val originCountry: String? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbCountryDto(
    @Json(name = "iso_3166_1") val iso3166: String? = null,
    val name: String? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbLanguageDto(
    @Json(name = "iso_639_1") val iso639: String? = null,
    val name: String? = null,
    @Json(name = "english_name") val englishName: String? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbNamedDto(val id: Int? = null, val name: String? = null)

@JsonClass(generateAdapter = true)
data class TmdbCastDto(
    val id: Int? = null,
    val name: String? = null,
    val character: String? = null,
    val order: Int? = null,
    @Json(name = "profile_path") val profilePath: String? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbCrewDto(
    val id: Int? = null,
    val name: String? = null,
    val job: String? = null,
    val department: String? = null,
    @Json(name = "profile_path") val profilePath: String? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbCreditsDto(
    val cast: List<TmdbCastDto>? = null,
    val crew: List<TmdbCrewDto>? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbVideoDto(
    val id: String? = null,
    val key: String? = null,
    val name: String? = null,
    val site: String? = null,
    val type: String? = null,
    val official: Boolean? = null,
    val language: String? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbVideoListDto(val results: List<TmdbVideoDto>? = null)

@JsonClass(generateAdapter = true)
data class TmdbAltTitleDto(
    @Json(name = "iso_3166_1") val iso3166: String? = null,
    val title: String? = null,
    val type: String? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbAltTitleListDto(val titles: List<TmdbAltTitleDto>? = null)

@JsonClass(generateAdapter = true)
data class TmdbTranslationDataDto(
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @Json(name = "homepage") val homepage: String? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbTranslationDto(
    @Json(name = "iso_639_1") val iso639: String? = null,
    @Json(name = "iso_3166_1") val iso3166: String? = null,
    val name: String? = null,
    @Json(name = "english_name") val englishName: String? = null,
    val data: TmdbTranslationDataDto? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbTranslationsDto(val translations: List<TmdbTranslationDto>? = null)

@JsonClass(generateAdapter = true)
data class TmdbExternalIdsDto(
    @Json(name = "imdb_id") val imdbId: String? = null,
    @Json(name = "tvdb_id") val tvdbId: Int? = null,
    @Json(name = "facebook_id") val facebookId: String? = null,
    @Json(name = "instagram_id") val instagramId: String? = null,
    @Json(name = "twitter_id") val twitterId: String? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbContentRatingDto(
    @Json(name = "iso_3166_1") val iso3166: String? = null,
    val rating: String? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbContentRatingsDto(val results: List<TmdbContentRatingDto>? = null)

@JsonClass(generateAdapter = true)
data class TmdbReleaseDateDto(
    val certification: String? = null,
    val type: Int? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbReleaseDatesResultDto(
    @Json(name = "iso_3166_1") val iso3166: String? = null,
    @Json(name = "release_dates") val releaseDates: List<TmdbReleaseDateDto>? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbReleaseDatesDto(val results: List<TmdbReleaseDatesResultDto>? = null)

/** Resultado genérico de listas (recomendaciones, similares, etc.). */
@JsonClass(generateAdapter = true)
data class TmdbListItemDto(
    val id: Int? = null,
    val title: String? = null,
    val name: String? = null,
    @Json(name = "original_title") val originalTitle: String? = null,
    @Json(name = "original_name") val originalName: String? = null,
    val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "vote_count") val voteCount: Int? = null,
    val popularity: Double? = null,
    @Json(name = "media_type") val mediaType: String? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbListItemPageDto(
    val page: Int? = null,
    val results: List<TmdbListItemDto>? = null,
    @Json(name = "total_pages") val totalPages: Int? = null,
    @Json(name = "total_results") val totalResults: Int? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbSeasonSummaryDto(
    val id: Int? = null,
    val name: String? = null,
    val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "season_number") val seasonNumber: Int? = null,
    @Json(name = "episode_count") val episodeCount: Int? = null,
    @Json(name = "air_date") val airDate: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbEpisodeDto(
    val id: Int? = null,
    val name: String? = null,
    val overview: String? = null,
    @Json(name = "episode_number") val episodeNumber: Int? = null,
    @Json(name = "season_number") val seasonNumber: Int? = null,
    @Json(name = "air_date") val airDate: String? = null,
    val runtime: Int? = null,
    @Json(name = "still_path") val stillPath: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "vote_count") val voteCount: Int? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbSeasonDto(
    val id: Int? = null,
    val name: String? = null,
    val overview: String? = null,
    @Json(name = "season_number") val seasonNumber: Int? = null,
    @Json(name = "air_date") val airDate: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    val episodes: List<TmdbEpisodeDto>? = null,
)

/** Ficha de película (con `append_to_response` combinado). */
@JsonClass(generateAdapter = true)
data class TmdbMovieDto(
    val id: Int? = null,
    val adult: Boolean? = null,
    val title: String? = null,
    @Json(name = "original_title") val originalTitle: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    val status: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    val runtime: Int? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "vote_count") val voteCount: Int? = null,
    val popularity: Double? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    val budget: Long? = null,
    val revenue: Long? = null,
    @Json(name = "imdb_id") val imdbId: String? = null,
    val genres: List<TmdbGenreDto>? = null,
    @Json(name = "production_companies") val productionCompanies: List<TmdbCompanyDto>? = null,
    @Json(name = "production_countries") val productionCountries: List<TmdbCountryDto>? = null,
    @Json(name = "spoken_languages") val spokenLanguages: List<TmdbLanguageDto>? = null,
    @Json(name = "alternative_titles") val alternativeTitles: TmdbAltTitleListDto? = null,
    val translations: TmdbTranslationsDto? = null,
    val credits: TmdbCreditsDto? = null,
    val videos: TmdbVideoListDto? = null,
    @Json(name = "external_ids") val externalIds: TmdbExternalIdsDto? = null,
    val recommendations: TmdbListItemPageDto? = null,
    val similar: TmdbListItemPageDto? = null,
    val images: TmdbImagesDto? = null,
    @Json(name = "release_dates") val releaseDates: TmdbReleaseDatesDto? = null,
)

/** Ficha de serie/anime (con `append_to_response` combinado). */
@JsonClass(generateAdapter = true)
data class TmdbTvDto(
    val id: Int? = null,
    val name: String? = null,
    @Json(name = "original_name") val originalName: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    val status: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "last_air_date") val lastAirDate: String? = null,
    @Json(name = "number_of_seasons") val numberOfSeasons: Int? = null,
    @Json(name = "number_of_episodes") val numberOfEpisodes: Int? = null,
    @Json(name = "episode_run_time") val episodeRunTime: List<Int>? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "vote_count") val voteCount: Int? = null,
    val popularity: Double? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    val genres: List<TmdbGenreDto>? = null,
    val networks: List<TmdbCompanyDto>? = null,
    val seasons: List<TmdbSeasonSummaryDto>? = null,
    @Json(name = "created_by") val createdBy: List<TmdbNamedDto>? = null,
    @Json(name = "production_companies") val productionCompanies: List<TmdbCompanyDto>? = null,
    @Json(name = "production_countries") val productionCountries: List<TmdbCountryDto>? = null,
    @Json(name = "spoken_languages") val spokenLanguages: List<TmdbLanguageDto>? = null,
    @Json(name = "alternative_titles") val alternativeTitles: TmdbAltTitleListDto? = null,
    val translations: TmdbTranslationsDto? = null,
    val credits: TmdbCreditsDto? = null,
    val videos: TmdbVideoListDto? = null,
    @Json(name = "external_ids") val externalIds: TmdbExternalIdsDto? = null,
    val recommendations: TmdbListItemPageDto? = null,
    val similar: TmdbListItemPageDto? = null,
    val images: TmdbImagesDto? = null,
    @Json(name = "content_ratings") val contentRatings: TmdbContentRatingsDto? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbImageItemDto(
    @Json(name = "file_path") val filePath: String? = null,
    @Json(name = "iso_639_1") val iso639: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbImagesDto(
    val logos: List<TmdbImageItemDto>? = null,
    val posters: List<TmdbImageItemDto>? = null,
    val backdrops: List<TmdbImageItemDto>? = null,
)
