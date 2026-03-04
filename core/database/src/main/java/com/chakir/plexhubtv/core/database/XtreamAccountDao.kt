package com.chakir.plexhubtv.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface XtreamAccountDao {

    @Query("SELECT * FROM xtream_accounts")
    fun observeAll(): Flow<List<XtreamAccountEntity>>

    @Query("SELECT * FROM xtream_accounts WHERE id = :id")
    suspend fun getById(id: String): XtreamAccountEntity?

    @Upsert
    suspend fun upsert(account: XtreamAccountEntity)

    @Query("DELETE FROM xtream_accounts WHERE id = :id")
    suspend fun delete(id: String)
}
