package com.chakir.plexhubtv.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.chakir.plexhubtv.core.model.Profile

/**
 * Profile Switch Screen - Allows switching between local app profiles
 */

/**
 * Écran de changement de profil utilisateur (Profils locaux de l'app).
 * Permet de basculer entre les profils locaux de l'application.
 */
@Composable
fun ProfileSwitchRoute(
    viewModel: ProfileViewModel = hiltViewModel(),
    onProfileSwitched: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    ProfileSwitchScreen(
        state = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSwitchScreen(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Switch Profile") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp),
                        ) {
                            Icon(
                                Icons.Filled.Error,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                state.error,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onNavigateBack) {
                                Text("Back")
                            }
                        }
                    }
                }
                state.profiles.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.PersonOff,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No profiles available",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                else -> {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.profiles, key = { it.id }) { profile ->
                            ProfileListItem(
                                profile = profile,
                                isCurrentProfile = profile.id == state.activeProfile?.id,
                                onClick = { onAction(ProfileAction.SelectProfile(profile)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileListItem(
    profile: Profile,
    isCurrentProfile: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val emoji = profile.avatarEmoji
                    if (!emoji.isNullOrEmpty()) {
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    } else {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            Spacer(Modifier.width(16.dp))

            // Profile info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    profile.name,
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                )
            }

            // Current profile indicator
            if (isCurrentProfile) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Current profile",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewProfileSwitchScreen() {
    val profiles =
        listOf(
            Profile(id = "1", name = "Chakir", avatarEmoji = "👤"),
            Profile(id = "2", name = "Guest", avatarEmoji = "👥"),
        )
    ProfileSwitchScreen(
        state = ProfileUiState(profiles = profiles, activeProfile = profiles[0], isLoading = false),
        onAction = {},
        onNavigateBack = {},
    )
}
