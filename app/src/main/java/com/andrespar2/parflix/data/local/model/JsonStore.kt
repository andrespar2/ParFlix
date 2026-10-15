package com.andrespar2.parflix.data.local.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * Serialización/deserialización de los metadatos secundarios almacenados en
 * `details`. Si un JSON quedara corrupto se devuelve una lista vacía: un dato
 * auxiliar nunca debe impedir ver una ficha.
 */
object JsonStore {

    private val moshi: Moshi = Moshi.Builder().build()

    fun encodeGenres(value: List<StoredGenre>): String = encode(value)
    fun decodeGenres(json: String?): List<StoredGenre> = decode(json)

    fun encodePeople(value: List<StoredPerson>): String = encode(value)
    fun decodePeople(json: String?): List<StoredPerson> = decode(json)

    fun encodeCompanies(value: List<StoredCompany>): String = encode(value)
    fun decodeCompanies(json: String?): List<StoredCompany> = decode(json)

    fun encodeCountries(value: List<StoredCountry>): String = encode(value)
    fun decodeCountries(json: String?): List<StoredCountry> = decode(json)

    fun encodeLanguages(value: List<StoredLanguage>): String = encode(value)
    fun decodeLanguages(json: String?): List<StoredLanguage> = decode(json)

    fun encodeVideos(value: List<StoredVideo>): String = encode(value)
    fun decodeVideos(json: String?): List<StoredVideo> = decode(json)

    fun encodeRecommendations(value: List<StoredRecommendation>): String = encode(value)
    fun decodeRecommendations(json: String?): List<StoredRecommendation> = decode(json)

    fun encodeSeasons(value: List<StoredSeason>): String = encode(value)
    fun decodeSeasons(json: String?): List<StoredSeason> = decode(json)

    fun encodeRatings(value: List<StoredRating>): String = encode(value)
    fun decodeRatings(json: String?): List<StoredRating> = decode(json)

    private inline fun <reified T> encode(value: List<T>): String {
        val type = Types.newParameterizedType(List::class.java, T::class.java)
        return runCatching { moshi.adapter<List<T>>(type).toJson(value) }.getOrDefault("[]")
    }

    private inline fun <reified T> decode(json: String?): List<T> {
        if (json.isNullOrBlank()) return emptyList()
        val type = Types.newParameterizedType(List::class.java, T::class.java)
        return runCatching { moshi.adapter<List<T>>(type).fromJson(json) }.getOrNull().orEmpty()
    }
}
