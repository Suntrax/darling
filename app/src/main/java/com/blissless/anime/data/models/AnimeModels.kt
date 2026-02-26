package com.blissless.anime.data.models

import kotlinx.serialization.Serializable

@Serializable
data class ExploreAnime(
    val id: Int,
    val title: String,
    val cover: String,
    val banner: String? = null,
    val episodes: Int = 0,
    val latestEpisode: Int? = null,
    val averageScore: Int? = null,
    val genres: List<String> = emptyList()
)

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

// Response classes
@Serializable
data class ViewerResponse(val data: ViewerData)

@Serializable
data class ViewerData(val Viewer: ViewerInfo)

@Serializable
data class ViewerInfo(val id: Int, val name: String, val avatar: AvatarInfo?)

@Serializable
data class AvatarInfo(val medium: String)

@Serializable
data class MediaListResponse(val data: MediaListData)

@Serializable
data class MediaListData(val MediaListCollection: MediaListCollectionInfo)

@Serializable
data class MediaListCollectionInfo(val lists: List<MediaListInfo>)

@Serializable
data class MediaListInfo(val name: String, val status: String?, val entries: List<MediaListEntry>)

@Serializable
data class MediaListEntry(val id: Int, val mediaId: Int, val progress: Int?, val status: String?, val media: MediaInfo)

@Serializable
data class MediaInfo(
    val id: Int,
    val title: TitleInfo,
    val coverImage: CoverInfo?,
    val bannerImage: String?,
    val episodes: Int?,
    val nextAiringEpisode: AiringInfo?,
    val status: String?,
    val averageScore: Int?,
    val genres: List<String>?
)

@Serializable
data class TitleInfo(val romaji: String?, val english: String?)

@Serializable
data class CoverInfo(val large: String?, val medium: String?)

@Serializable
data class AiringInfo(val episode: Int, val airingAt: Int)

@Serializable
data class ExploreResponse(val data: ExplorePageData)

@Serializable
data class ExplorePageData(val Page: ExploreMediaList)

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