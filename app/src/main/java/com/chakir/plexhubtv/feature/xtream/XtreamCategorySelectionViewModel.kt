package com.chakir.plexhubtv.feature.xtream

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.model.CategorySelection
import com.chakir.plexhubtv.domain.repository.CategoryRepository
import com.chakir.plexhubtv.domain.repository.XtreamAccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class SelectableCategory(
    val categoryId: String,
    val categoryName: String,
    val categoryType: String, // "vod" or "series"
    val isSelected: Boolean,
)

data class CategorySection(
    val accountId: String,
    val accountLabel: String,
    val vodCategories: List<SelectableCategory>,
    val seriesCategories: List<SelectableCategory>,
)

data class XtreamCategorySelectionUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val sections: List<CategorySection> = emptyList(),
    val isConfirming: Boolean = false,
    val isSyncing: Boolean = false,
) {
    // Computed property: unified mode means all categories are selected
    val isUnifiedMode: Boolean
        get() = sections.all { section ->
            section.vodCategories.all { it.isSelected } &&
            section.seriesCategories.all { it.isSelected }
        }
}

sealed interface XtreamCategorySelectionAction {
    data class ToggleVodCategory(val accountId: String, val categoryId: String) : XtreamCategorySelectionAction
    data class ToggleSeriesCategory(val accountId: String, val categoryId: String) : XtreamCategorySelectionAction
    data class ToggleAllVod(val accountId: String) : XtreamCategorySelectionAction
    data class ToggleAllSeries(val accountId: String) : XtreamCategorySelectionAction
    data object Confirm : XtreamCategorySelectionAction
    data object Retry : XtreamCategorySelectionAction
}

sealed class XtreamCategorySelectionNavEvent {
    data object NavigateBack : XtreamCategorySelectionNavEvent()
}

@HiltViewModel
class XtreamCategorySelectionViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val accountRepository: XtreamAccountRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val accountId: String = checkNotNull(savedStateHandle["accountId"]) {
        "XtreamCategorySelectionViewModel requires accountId in SavedStateHandle"
    }

    private val _uiState = MutableStateFlow(XtreamCategorySelectionUiState())
    val uiState: StateFlow<XtreamCategorySelectionUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<XtreamCategorySelectionNavEvent>()
    val navigationEvent: SharedFlow<XtreamCategorySelectionNavEvent> = _navigationEvent.asSharedFlow()

    init {
        loadCategories()
    }

    fun onAction(action: XtreamCategorySelectionAction) {
        when (action) {
            is XtreamCategorySelectionAction.ToggleVodCategory -> toggleVodCategory(action.accountId, action.categoryId)
            is XtreamCategorySelectionAction.ToggleSeriesCategory -> toggleSeriesCategory(action.accountId, action.categoryId)
            is XtreamCategorySelectionAction.ToggleAllVod -> toggleAllVod(action.accountId)
            is XtreamCategorySelectionAction.ToggleAllSeries -> toggleAllSeries(action.accountId)
            is XtreamCategorySelectionAction.Confirm -> confirm()
            is XtreamCategorySelectionAction.Retry -> loadCategories()
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val account = accountRepository.observeAccounts().first().find { it.id == accountId }
                if (account == null) {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Account not found")
                    }
                    return@launch
                }

                val categoriesResult = categoryRepository.getCategories(accountId)

                categoriesResult.fold(
                    onSuccess = { config ->
                        val vodCategories = config.categories
                            .filter { it.categoryType == "vod" }
                            .map { cat ->
                                SelectableCategory(
                                    categoryId = cat.categoryId,
                                    categoryName = cat.categoryName,
                                    categoryType = cat.categoryType,
                                    isSelected = cat.isAllowed,
                                )
                            }

                        val seriesCategories = config.categories
                            .filter { it.categoryType == "series" }
                            .map { cat ->
                                SelectableCategory(
                                    categoryId = cat.categoryId,
                                    categoryName = cat.categoryName,
                                    categoryType = cat.categoryType,
                                    isSelected = cat.isAllowed,
                                )
                            }

                        val section = CategorySection(
                            accountId = accountId,
                            accountLabel = account.label,
                            vodCategories = vodCategories,
                            seriesCategories = seriesCategories,
                        )

                        if (vodCategories.isEmpty() && seriesCategories.isEmpty()) {
                            _uiState.update {
                                it.copy(isLoading = false, error = "No categories found")
                            }
                        } else {
                            _uiState.update {
                                it.copy(isLoading = false, sections = listOf(section))
                            }
                        }
                    },
                    onFailure = { error ->
                        Timber.e(error, "Failed to load categories")
                        _uiState.update {
                            it.copy(isLoading = false, error = "Failed to load categories: ${error.message}")
                        }
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load Xtream categories")
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load categories: ${e.message}")
                }
            }
        }
    }

    private fun toggleVodCategory(accountId: String, categoryId: String) {
        _uiState.update { state ->
            state.copy(
                sections = state.sections.map { section ->
                    if (section.accountId == accountId) {
                        section.copy(
                            vodCategories = section.vodCategories.map { cat ->
                                if (cat.categoryId == categoryId) cat.copy(isSelected = !cat.isSelected)
                                else cat
                            },
                        )
                    } else section
                },
            )
        }
    }

    private fun toggleSeriesCategory(accountId: String, categoryId: String) {
        _uiState.update { state ->
            state.copy(
                sections = state.sections.map { section ->
                    if (section.accountId == accountId) {
                        section.copy(
                            seriesCategories = section.seriesCategories.map { cat ->
                                if (cat.categoryId == categoryId) cat.copy(isSelected = !cat.isSelected)
                                else cat
                            },
                        )
                    } else section
                },
            )
        }
    }

    private fun toggleAllVod(accountId: String) {
        _uiState.update { state ->
            state.copy(
                sections = state.sections.map { section ->
                    if (section.accountId == accountId) {
                        val allSelected = section.vodCategories.all { it.isSelected }
                        section.copy(
                            vodCategories = section.vodCategories.map { it.copy(isSelected = !allSelected) },
                        )
                    } else section
                },
            )
        }
    }

    private fun toggleAllSeries(accountId: String) {
        _uiState.update { state ->
            state.copy(
                sections = state.sections.map { section ->
                    if (section.accountId == accountId) {
                        val allSelected = section.seriesCategories.all { it.isSelected }
                        section.copy(
                            seriesCategories = section.seriesCategories.map { it.copy(isSelected = !allSelected) },
                        )
                    } else section
                },
            )
        }
    }

    private fun confirm() {
        viewModelScope.launch {
            _uiState.update { it.copy(isConfirming = true) }

            try {
                val currentState = _uiState.value
                val section = currentState.sections.firstOrNull()

                if (section != null) {
                    val allCategories = section.vodCategories + section.seriesCategories

                    // Determine filter mode based on selection
                    val allSelected = allCategories.all { it.isSelected }
                    val noneSelected = allCategories.none { it.isSelected }

                    val filterMode = when {
                        allSelected -> "all"
                        noneSelected -> "all" // Default to all if nothing selected
                        else -> "whitelist"
                    }

                    val categorySelections = allCategories.map { cat ->
                        CategorySelection(
                            categoryId = cat.categoryId,
                            categoryType = cat.categoryType,
                            isAllowed = cat.isSelected
                        )
                    }

                    val updateResult = categoryRepository.updateCategories(
                        accountId = accountId,
                        filterMode = filterMode,
                        categories = categorySelections
                    )

                    updateResult.fold(
                        onSuccess = {
                            Timber.i("Category selection saved for account $accountId (mode: $filterMode)")
                            _navigationEvent.emit(XtreamCategorySelectionNavEvent.NavigateBack)
                        },
                        onFailure = { error ->
                            Timber.e(error, "Failed to save category selection")
                            _uiState.update {
                                it.copy(isConfirming = false, error = "Failed to save: ${error.message}")
                            }
                        }
                    )
                } else {
                    _navigationEvent.emit(XtreamCategorySelectionNavEvent.NavigateBack)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save Xtream category selection")
                _uiState.update {
                    it.copy(isConfirming = false, error = "Failed to save: ${e.message}")
                }
            }
        }
    }
}
