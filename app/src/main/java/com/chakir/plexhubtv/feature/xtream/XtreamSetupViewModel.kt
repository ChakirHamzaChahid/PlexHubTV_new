package com.chakir.plexhubtv.feature.xtream

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.model.XtreamAccount
import com.chakir.plexhubtv.core.model.XtreamAccountStatus
import com.chakir.plexhubtv.domain.repository.XtreamAccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class XtreamSetupUiState(
    val baseUrl: String = "",
    val port: String = "8080",
    val username: String = "",
    val password: String = "",
    val label: String = "",
    val isTesting: Boolean = false,
    val isSaving: Boolean = false,
    val testResult: XtreamTestResult? = null,
    val savedAccount: XtreamAccount? = null,
    val accounts: List<XtreamAccount> = emptyList(),
)

sealed interface XtreamTestResult {
    data class Success(val status: String, val expiration: String?) : XtreamTestResult
    data class Error(val message: String) : XtreamTestResult
}

sealed interface XtreamSetupAction {
    data class UpdateBaseUrl(val value: String) : XtreamSetupAction
    data class UpdatePort(val value: String) : XtreamSetupAction
    data class UpdateUsername(val value: String) : XtreamSetupAction
    data class UpdatePassword(val value: String) : XtreamSetupAction
    data class UpdateLabel(val value: String) : XtreamSetupAction
    data object TestConnection : XtreamSetupAction
    data object SaveAccount : XtreamSetupAction
    data class RemoveAccount(val accountId: String) : XtreamSetupAction
    data object ClearForm : XtreamSetupAction
}

@HiltViewModel
class XtreamSetupViewModel @Inject constructor(
    private val accountRepo: XtreamAccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(XtreamSetupUiState())
    val uiState: StateFlow<XtreamSetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            accountRepo.observeAccounts().collect { accounts ->
                _uiState.update { it.copy(accounts = accounts) }
            }
        }
    }

    fun onAction(action: XtreamSetupAction) {
        when (action) {
            is XtreamSetupAction.UpdateBaseUrl -> _uiState.update { it.copy(baseUrl = action.value) }
            is XtreamSetupAction.UpdatePort -> _uiState.update { it.copy(port = action.value) }
            is XtreamSetupAction.UpdateUsername -> _uiState.update { it.copy(username = action.value) }
            is XtreamSetupAction.UpdatePassword -> _uiState.update { it.copy(password = action.value) }
            is XtreamSetupAction.UpdateLabel -> _uiState.update { it.copy(label = action.value) }
            is XtreamSetupAction.TestConnection -> testConnection()
            is XtreamSetupAction.SaveAccount -> saveAccount()
            is XtreamSetupAction.RemoveAccount -> removeAccount(action.accountId)
            is XtreamSetupAction.ClearForm -> clearForm()
        }
    }

    private fun testConnection() {
        val state = _uiState.value
        if (state.baseUrl.isBlank() || state.username.isBlank() || state.password.isBlank()) return

        _uiState.update { it.copy(isTesting = true, testResult = null) }
        viewModelScope.launch {
            val result = accountRepo.addAccount(
                baseUrl = state.baseUrl.trim(),
                port = state.port.toIntOrNull() ?: 8080,
                username = state.username.trim(),
                password = state.password.trim(),
                label = state.label.ifBlank { "Xtream ${state.username}" },
            )

            result.fold(
                onSuccess = { account ->
                    val expDate = account.expirationDate?.let {
                        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                            .format(java.util.Date(it * 1000))
                    }
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            testResult = XtreamTestResult.Success(
                                status = account.status.name,
                                expiration = expDate,
                            ),
                            savedAccount = account,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            testResult = XtreamTestResult.Error(
                                error.message ?: "Connection failed"
                            ),
                        )
                    }
                },
            )
        }
    }

    private fun saveAccount() {
        // Account is already saved during testConnection
        val saved = _uiState.value.savedAccount ?: return
        if (saved.status == XtreamAccountStatus.Active) {
            clearForm()
        }
    }

    private fun removeAccount(accountId: String) {
        viewModelScope.launch {
            accountRepo.removeAccount(accountId)
        }
    }

    private fun clearForm() {
        _uiState.update {
            it.copy(
                baseUrl = "",
                port = "8080",
                username = "",
                password = "",
                label = "",
                isTesting = false,
                isSaving = false,
                testResult = null,
                savedAccount = null,
            )
        }
    }
}
