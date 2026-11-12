package com.muse.feature.agent.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.muse.feature.agent.data.local.entity.RootGrantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RootGrantDao {

    @Query("SELECT * FROM root_grants ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RootGrantEntity>>

    @Query("SELECT COUNT(*) FROM root_grants WHERE realPath = :realPath AND operation = :operation")
    suspend fun count(realPath: String, operation: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RootGrantEntity)

    @Query("DELETE FROM root_grants WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM root_grants")
    suspend fun clearAll()
}
