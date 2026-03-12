package com.chakir.plexhubtv.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.model.Person
import com.chakir.plexhubtv.core.model.PersonCredit
import com.chakir.plexhubtv.core.network.ApiKeyManager
import com.chakir.plexhubtv.core.network.TmdbApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/"

data class PersonDetailUiState(
    val isLoading: Boolean = true,
    val person: Person? = null,
    val error: String? = null,
)

@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tmdbApiService: TmdbApiService,
    private val apiKeyManager: ApiKeyManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonDetailUiState())
    val uiState: StateFlow<PersonDetailUiState> = _uiState.asStateFlow()

    private val personName: String = savedStateHandle.get<String>("personName") ?: ""

    init {
        loadPerson()
    }

    private fun loadPerson() {
        viewModelScope.launch {
            _uiState.value = PersonDetailUiState(isLoading = true)

            val apiKey = apiKeyManager.getTmdbApiKey()
            if (apiKey.isNullOrBlank()) {
                _uiState.value = PersonDetailUiState(
                    isLoading = false,
                    error = "TMDB API key not configured",
                )
                return@launch
            }

            try {
                // Search for the person by name
                val searchResponse = tmdbApiService.searchPerson(apiKey, personName)
                val firstResult = searchResponse.results.firstOrNull()

                if (firstResult == null) {
                    _uiState.value = PersonDetailUiState(
                        isLoading = false,
                        error = "Person not found",
                    )
                    return@launch
                }

                // Get full details with combined credits
                val detail = tmdbApiService.getPersonDetails(firstResult.id, apiKey)

                val castCredits = detail.combinedCredits?.cast
                    ?.filter { !it.character.isNullOrBlank() }
                    ?.sortedByDescending { it.voteAverage ?: 0.0 }
                    ?.distinctBy { it.id }
                    ?.map { credit ->
                        PersonCredit(
                            id = credit.id,
                            title = credit.title ?: credit.name ?: "",
                            mediaType = credit.mediaType ?: "movie",
                            character = credit.character,
                            job = null,
                            posterUrl = credit.posterPath?.let { "${TMDB_IMAGE_BASE}w342$it" },
                            voteAverage = credit.voteAverage,
                            year = extractYear(credit.releaseDate ?: credit.firstAirDate),
                        )
                    } ?: emptyList()

                val crewCredits = detail.combinedCredits?.crew
                    ?.sortedByDescending { it.voteAverage ?: 0.0 }
                    ?.distinctBy { it.id }
                    ?.map { credit ->
                        PersonCredit(
                            id = credit.id,
                            title = credit.title ?: credit.name ?: "",
                            mediaType = credit.mediaType ?: "movie",
                            character = null,
                            job = credit.job,
                            posterUrl = credit.posterPath?.let { "${TMDB_IMAGE_BASE}w342$it" },
                            voteAverage = credit.voteAverage,
                            year = extractYear(credit.releaseDate ?: credit.firstAirDate),
                        )
                    } ?: emptyList()

                val person = Person(
                    id = detail.id,
                    name = detail.name,
                    biography = detail.biography?.takeIf { it.isNotBlank() },
                    birthday = detail.birthday,
                    deathday = detail.deathday,
                    placeOfBirth = detail.placeOfBirth,
                    photoUrl = detail.profilePath?.let { "${TMDB_IMAGE_BASE}w500$it" },
                    knownFor = detail.knownForDepartment,
                    castCredits = castCredits,
                    crewCredits = crewCredits,
                )

                _uiState.value = PersonDetailUiState(isLoading = false, person = person)

                Timber.d("PersonDetail: Loaded ${person.name} with ${castCredits.size} cast + ${crewCredits.size} crew credits")
            } catch (e: Exception) {
                Timber.e(e, "PersonDetail: Failed to load person '$personName'")
                _uiState.value = PersonDetailUiState(
                    isLoading = false,
                    error = "Failed to load person details",
                )
            }
        }
    }

    private fun extractYear(dateStr: String?): String? {
        if (dateStr.isNullOrBlank()) return null
        return dateStr.take(4).takeIf { it.length == 4 }
    }
}
