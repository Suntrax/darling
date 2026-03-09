package com.blissless.anime.data.models

import kotlinx.serialization.Serializable

@Serializable
data class AnimeMedia(
    val id: Int,
    val title: String,
    val cover: String,
    val banner: String? = null,
    val progress: Int = 0,
    val totalEpisodes: Int = 0,
    val latestEpisode: Int? = null,
    val status: String = "",
    val averageScore: Int? = null,
    val genres: List<String> = emptyList(),
    val listStatus: String = "",
    val listEntryId: Int? = null
)

@Serializable
data class TitleInfo(val romaji: String?, val english: String?)

@Serializable
data class CoverInfo(val large: String?, val medium: String?)

@Serializable
data class AiringInfo(val episode: Int, val airingAt: Int)

@Serializable
data class ExploreMediaList(val media: List<ExploreMediaInfo>)

@Serializable
data class ExploreMediaInfo(
    val id: Int,
    val title: TitleInfo,
    val coverImage: CoverInfo?,
    val bannerImage: String?,
    val episodes: Int?,
    val nextAiringEpisode: AiringInfo?,
    val averageScore: Int?,
    val genres: List<String>?
)