package com.mikotrack.data.repository

import com.mikotrack.data.db.CategoryDao
import com.mikotrack.data.db.Mappers.toDomain
import com.mikotrack.domain.model.WaypointCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Manages the waypoint categories, seeding the built-in ones on first use. */
class CategoryRepository(private val dao: CategoryDao) {

    fun observeCategories(): Flow<List<WaypointCategory>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun ensureDefaults() {
        dao.insertAll(DefaultCategory.entries.map { it.toEntity() })
    }
}
