package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.core.model.XtreamAccount
import kotlinx.coroutines.flow.Flow

interface XtreamAccountRepository {
    fun observeAccounts(): Flow<List<XtreamAccount>>

    suspend fun getAccount(accountId: String): XtreamAccount?

    suspend fun addAccount(
        baseUrl: String,
        port: Int,
        username: String,
        password: String,
        label: String,
    ): Result<XtreamAccount>

    suspend fun removeAccount(accountId: String)

    suspend fun refreshAccountStatus(accountId: String): Result<XtreamAccount>

    suspend fun getDecryptedPassword(accountId: String): String?
}
