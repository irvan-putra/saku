package com.vanipy.saku

enum class Origin { AGENT, MANUAL, SYNTHETIC }

data class Entry(
    val id: String,
    val site: String,
    val username: String,
    val password: String,
    val origin: Origin,
    val version: Int,
    val lastChangeMillis: Long,
    val lastSyncMillis: Long?,
    val archived: Boolean = false,
)

object ArchiveFilter {
    fun visible(entries: List<Entry>, showArchived: Boolean): List<Entry> =
        if (showArchived) entries else entries.filter { !it.archived }
}
