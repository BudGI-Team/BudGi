package com.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.app.data.local.entity.SharedSpaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SpaceDao {
    @Query("SELECT * FROM shared_spaces")
    fun observeSpaces(): Flow<List<SharedSpaceEntity>>

    @Query("SELECT * FROM shared_spaces WHERE id = :spaceId")
    fun observeSpaceById(spaceId: String): Flow<SharedSpaceEntity?>

    @Query("SELECT * FROM shared_spaces WHERE id = :spaceId")
    suspend fun getSpaceById(spaceId: String): SharedSpaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpace(space: SharedSpaceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpaces(spaces: List<SharedSpaceEntity>)

    @Query("SELECT * FROM shared_spaces")
    suspend fun getAllSpacesDirect(): List<SharedSpaceEntity>
    
    @Query("DELETE FROM shared_spaces WHERE id = :spaceId")
    suspend fun deleteSpace(spaceId: String)
    
    @Query("DELETE FROM shared_spaces")
    suspend fun clearSpaces()
}
