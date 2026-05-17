package eu.kanade.tachiyomi.animesource.model

import kotlin.math.roundToLong

data class SEpisode(
    val url: String = "",
    val name: String = "",
    val episode_number: Float = 0f,
    val date_upload: Long = 0L,
    val scanlator: String = "",
) {
    val episode_number_long: Long get() = episode_number.roundToLong()
}
