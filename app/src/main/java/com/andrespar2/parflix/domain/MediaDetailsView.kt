package com.andrespar2.parflix.domain

import com.andrespar2.parflix.data.local.entity.CatalogItemEntity
import com.andrespar2.parflix.data.local.entity.DetailsEntity
import com.andrespar2.parflix.data.local.model.StoredCompany
import com.andrespar2.parflix.data.local.model.StoredCountry
import com.andrespar2.parflix.data.local.model.StoredGenre
import com.andrespar2.parflix.data.local.model.StoredLanguage
import com.andrespar2.parflix.data.local.model.StoredPerson
import com.andrespar2.parflix.data.local.model.StoredRecommendation
import com.andrespar2.parflix.data.local.model.StoredSeason
import com.andrespar2.parflix.data.local.model.StoredVideo

/**
 * Vista completa de una ficha: datos del catálogo local + metadatos de TMDB ya
 * deserializados. Si [details] es null, la ficha aún no se ha enriquecido.
 */
data class MediaDetailsView(
    val item: CatalogItemEntity,
    val details: DetailsEntity? = null,
    val genres: List<StoredGenre> = emptyList(),
    val cast: List<StoredPerson> = emptyList(),
    val crew: List<StoredPerson> = emptyList(),
    val companies: List<StoredCompany> = emptyList(),
    val countries: List<StoredCountry> = emptyList(),
    val languages: List<StoredLanguage> = emptyList(),
    val videos: List<StoredVideo> = emptyList(),
    val recommendations: List<StoredRecommendation> = emptyList(),
    val seasons: List<StoredSeason> = emptyList(),
    val altTitles: List<String> = emptyList(),
) {
    val title: String get() = item.title
    val posterPath: String? get() = details?.let { item.posterPath } ?: item.posterPath
    val overview: String? get() = details?.overview ?: item.overview
    val year: Int? get() = item.year
    val voteAverage: Double? get() = details?.voteAverage ?: item.voteAverage
    val mediaType: MediaType get() = item.type ?: MediaType.MOVIE

    /** Trailer principal, si existe. */
    val trailerKey: String? get() = videos.firstOrNull { it.site.equals("YouTube", true) }?.key

    /** Número de temporadas disponible: primero TMDB, después el listado de temporadas. */
    val seasonCount: Int
        get() = details?.numberOfSeasons
            ?: seasons.maxOfOrNull { it.number }
            ?: item.totalSeasons
            ?: 0

    /** Temporadas reales (se excluye "Especiales" = 0 cuando hay temporadas normales). */
    val displaySeasons: List<StoredSeason>
        get() {
            val real = seasons.filter { it.number > 0 }
            return if (real.isNotEmpty()) real.sortedBy { it.number } else seasons.sortedBy { it.number }
        }
}
