package com.andrespar2.parflix.data.remote.mapper

import com.andrespar2.parflix.core.util.TimeUtils
import com.andrespar2.parflix.data.local.entity.AltTitleEntity
import com.andrespar2.parflix.data.local.entity.DetailsEntity
import com.andrespar2.parflix.data.local.model.JsonStore
import com.andrespar2.parflix.data.local.model.StoredCompany
import com.andrespar2.parflix.data.local.model.StoredCountry
import com.andrespar2.parflix.data.local.model.StoredGenre
import com.andrespar2.parflix.data.local.model.StoredLanguage
import com.andrespar2.parflix.data.local.model.StoredPerson
import com.andrespar2.parflix.data.local.model.StoredRating
import com.andrespar2.parflix.data.local.model.StoredRecommendation
import com.andrespar2.parflix.data.local.model.StoredSeason
import com.andrespar2.parflix.data.local.model.StoredVideo
import com.andrespar2.parflix.data.remote.dto.TmdbMovieDto
import com.andrespar2.parflix.data.remote.dto.TmdbTvDto
import com.andrespar2.parflix.domain.MediaType

/**
 * Conversión de las fichas de TMDB al modelo local.
 *
 * Preferencia de idioma: los textos en español (es-CO y variantes) se priorizan
 * sobre el inglés; los títulos alternativos se conservan para la búsqueda.
 */
object TmdbMappers {

    /** Idiomas preferidos, en orden. */
    private val preferredOrder = listOf("es-co", "es-mx", "es-ar", "es-es", "es", "en")

    fun detailsFromMovie(dto: TmdbMovieDto, mediaType: MediaType, now: Long): DetailsEntity {
        val rating = dto.releaseDates?.results
            ?.firstOrNull { it.iso3166.equals("CO", ignoreCase = true) }
            ?.releaseDates?.firstNotNullOfOrNull { it.certification?.takeIf { c -> c.isNotBlank() } }
            ?: dto.releaseDates?.results
                ?.firstOrNull { it.iso3166.equals("US", ignoreCase = true) }
                ?.releaseDates?.firstNotNullOfOrNull { it.certification?.takeIf { c -> c.isNotBlank() } }

        return DetailsEntity(
            tmdbId = dto.id ?: 0,
            mediaType = mediaType.name,
            originalTitle = dto.originalTitle ?: dto.title,
            tagline = dto.tagline?.takeIf { it.isNotBlank() },
            overview = dto.overview?.takeIf { it.isNotBlank() },
            status = dto.status,
            releaseDate = dto.releaseDate,
            runtimeMinutes = dto.runtime?.takeIf { it > 0 },
            voteAverage = dto.voteAverage,
            voteCount = dto.voteCount,
            popularity = dto.popularity,
            budget = dto.budget,
            revenue = dto.revenue,
            ageRating = rating,
            genresJson = JsonStore.encodeGenres(dto.genres.toGenres()),
            countriesJson = JsonStore.encodeCountries(
                dto.productionCountries.orEmpty().mapNotNull { c ->
                    c.name?.let { StoredCountry(c.iso3166, it) }
                }
            ),
            languagesJson = JsonStore.encodeLanguages(
                dto.spokenLanguages.orEmpty().mapNotNull { l ->
                    (l.name ?: l.englishName)?.let { StoredLanguage(l.iso639, it) }
                }
            ),
            companiesJson = JsonStore.encodeCompanies(dto.productionCompanies.toCompanies()),
            networksJson = null,
            castJson = JsonStore.encodePeople(dto.credits?.cast.toCast()),
            crewJson = JsonStore.encodePeople(dto.credits?.crew.toDirectors()),
            videosJson = JsonStore.encodeVideos(dto.videos?.results.toVideos()),
            recommendationsJson = JsonStore.encodeRecommendations(
                dto.recommendations?.results.toRecommendations(MediaType.MOVIE)
            ),
            contentRatingsJson = null,
            externalIdsJson = null,
            seasonsJson = null,
            lastUpdated = now,
        )
    }

    fun detailsFromTv(dto: TmdbTvDto, mediaType: MediaType, now: Long): DetailsEntity {
        val rating = dto.contentRatings?.results
            ?.firstOrNull { it.iso3166.equals("CO", ignoreCase = true) }?.rating
            ?: dto.contentRatings?.results
                ?.firstOrNull { it.iso3166.equals("US", ignoreCase = true) }?.rating

        return DetailsEntity(
            tmdbId = dto.id ?: 0,
            mediaType = mediaType.name,
            originalTitle = dto.originalName ?: dto.name,
            tagline = dto.tagline?.takeIf { it.isNotBlank() },
            overview = dto.overview?.takeIf { it.isNotBlank() },
            status = dto.status,
            releaseDate = null,
            firstAirDate = dto.firstAirDate,
            lastAirDate = dto.lastAirDate,
            runtimeMinutes = dto.episodeRunTime?.firstOrNull { it > 0 },
            numberOfSeasons = dto.numberOfSeasons,
            numberOfEpisodes = dto.numberOfEpisodes,
            voteAverage = dto.voteAverage,
            voteCount = dto.voteCount,
            popularity = dto.popularity,
            ageRating = rating,
            genresJson = JsonStore.encodeGenres(dto.genres.toGenres()),
            countriesJson = JsonStore.encodeCountries(
                dto.productionCountries.orEmpty().mapNotNull { c -> c.name?.let { StoredCountry(c.iso3166, it) } }
            ),
            languagesJson = JsonStore.encodeLanguages(
                dto.spokenLanguages.orEmpty().mapNotNull { l ->
                    (l.name ?: l.englishName)?.let { StoredLanguage(l.iso639, it) }
                }
            ),
            companiesJson = JsonStore.encodeCompanies(dto.productionCompanies.toCompanies()),
            networksJson = JsonStore.encodeCompanies(dto.networks.toCompanies()),
            castJson = JsonStore.encodePeople(dto.credits?.cast.toCast()),
            crewJson = JsonStore.encodePeople(
                buildList {
                    addAll(dto.createdBy.orEmpty().map { StoredPerson(it.id, it.name.orEmpty(), role = "Creador") })
                    addAll(dto.credits?.crew.toDirectors())
                }
            ),
            videosJson = JsonStore.encodeVideos(dto.videos?.results.toVideos()),
            recommendationsJson = JsonStore.encodeRecommendations(
                dto.recommendations?.results.toRecommendations(mediaType)
            ),
            contentRatingsJson = null,
            externalIdsJson = null,
            seasonsJson = JsonStore.encodeSeasons(
                dto.seasons.orEmpty().mapNotNull { s ->
                    val number = s.seasonNumber ?: return@mapNotNull null
                    StoredSeason(
                        number = number,
                        name = s.name,
                        episodeCount = s.episodeCount,
                        posterPath = s.posterPath,
                        airDate = s.airDate,
                        overview = s.overview,
                    )
                }
            ),
            lastUpdated = now,
        )
    }

    /** Títulos alternativos + traducciones, en orden de preferencia de idioma. */
    fun altTitles(
        tmdbId: Int,
        mediaType: MediaType,
        altTitles: List<Pair<String?, String?>>, // (idioma, título)
        translations: List<Triple<String?, String?, String?>>, // (idioma, título, país)
    ): List<AltTitleEntity> {
        val result = LinkedHashMap<String, AltTitleEntity>()

        altTitles.forEach { (language, title) ->
            val clean = title?.trim().orEmpty()
            if (clean.isEmpty()) return@forEach
            val key = clean.lowercase()
            result.putIfAbsent(
                key,
                AltTitleEntity(
                    tmdbId = tmdbId,
                    mediaType = mediaType.name,
                    title = clean,
                    language = language,
                    fromTranslation = false,
                )
            )
        }

        translations.forEach { (language, title, country) ->
            val clean = title?.trim().orEmpty()
            if (clean.isEmpty()) return@forEach
            val key = clean.lowercase()
            result.putIfAbsent(
                key,
                AltTitleEntity(
                    tmdbId = tmdbId,
                    mediaType = mediaType.name,
                    title = clean,
                    language = language,
                    country = country,
                    fromTranslation = true,
                )
            )
        }

        // Orden estable por preferencia de idioma (los primeros son los mejores).
        return result.values.sortedBy { entity ->
            preferredOrder.indexOf((entity.language ?: "").lowercase()).let { if (it < 0) 99 else it }
        }
    }

    /** Mejor texto disponible según preferencia de idioma. */
    fun pickRating(ratings: List<StoredRating>): String? =
        ratings.firstOrNull { it.country.equals("CO", true) }?.rating
            ?: ratings.firstOrNull { it.country.equals("US", true) }?.rating
            ?: ratings.firstOrNull()?.rating

    // ---- helpers ----

    private fun List<com.andrespar2.parflix.data.remote.dto.TmdbGenreDto>?.toGenres(): List<StoredGenre> =
        orEmpty().mapNotNull { g -> g.id?.let { id -> StoredGenre(id, g.name.orEmpty()) } }
            .filter { it.name.isNotBlank() }

    private fun List<com.andrespar2.parflix.data.remote.dto.TmdbCompanyDto>?.toCompanies(): List<StoredCompany> =
        orEmpty().mapNotNull { c -> c.name?.let { StoredCompany(c.id, it, c.logoPath) } }

    private fun List<com.andrespar2.parflix.data.remote.dto.TmdbCastDto>?.toCast(): List<StoredPerson> =
        orEmpty()
            .sortedBy { it.order ?: Int.MAX_VALUE }
            .take(20)
            .mapNotNull { c ->
                c.name?.let {
                    StoredPerson(id = c.id, name = it, role = c.character?.takeIf { ch -> ch.isNotBlank() }, photoPath = c.profilePath)
                }
            }

    private fun List<com.andrespar2.parflix.data.remote.dto.TmdbCrewDto>?.toDirectors(): List<StoredPerson> =
        orEmpty()
            .filter { it.job.equals("Director", true) || it.job.equals("Creator", true) || it.job.equals("Writer", true) }
            .distinctBy { it.id }
            .take(12)
            .mapNotNull { c -> c.name?.let { StoredPerson(id = c.id, name = it, role = c.job, photoPath = c.profilePath) } }

    private fun List<com.andrespar2.parflix.data.remote.dto.TmdbVideoDto>?.toVideos(): List<StoredVideo> =
        orEmpty()
            .filter { it.site.equals("YouTube", true) && !it.key.isNullOrBlank() }
            .sortedWith(
                compareBy(
                    { if (it.type.equals("Trailer", true)) 0 else 1 },
                    { if ((it.language ?: "").startsWith("es")) 0 else 1 },
                    { if (it.official == true) 0 else 1 },
                )
            )
            .take(6)
            .mapNotNull { v ->
                v.key?.let { StoredVideo(it, v.name, v.site, v.type, v.language, v.official == true) }
            }

    private fun List<com.andrespar2.parflix.data.remote.dto.TmdbListItemDto>?.toRecommendations(
        fallbackType: MediaType,
    ): List<StoredRecommendation> = orEmpty().mapNotNull { r ->
        val id = r.id ?: return@mapNotNull null
        val title = (r.title ?: r.name)?.trim().orEmpty()
        if (title.isEmpty()) return@mapNotNull null
        val type = MediaType.fromString(r.mediaType) ?: fallbackType
        StoredRecommendation(
            tmdbId = id,
            mediaType = type.name,
            title = title,
            posterPath = r.posterPath,
            year = TimeUtils.yearOf(r.releaseDate ?: r.firstAirDate),
            voteAverage = r.voteAverage,
        )
    }
}
