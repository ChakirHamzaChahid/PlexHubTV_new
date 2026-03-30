package com.chakir.plexhubtv.feature.appprofile

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.core.model.Profile
import com.chakir.plexhubtv.core.ui.ParentalPinDialog
import com.chakir.plexhubtv.core.ui.PinDialogMode

/**
 * Ecran de gestion des profils utilisateur (Profils locaux de l'app).
 * Permet de basculer entre les profils, en creer, modifier ou supprimer.
 */
@Composable
fun AppProfileSwitchRoute(
    viewModel: AppProfileViewModel = hiltViewModel(),
    onProfileSwitched: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    AppProfileSwitchScreen(
        state = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppProfileSwitchScreen(
    state: AppProfileUiState,
    onAction: (AppProfileAction) -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_manage_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.profiles.size < 5) {
                        IconButton(onClick = { onAction(AppProfileAction.CreateProfile) }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.profile_add_description))
                        }
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
                                Text(stringResource(R.string.action_back))
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
                                stringResource(R.string.profile_no_profiles_available),
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
                            val isCurrentProfile = profile.id == state.activeProfile?.id
                            AppProfileListItem(
                                profile = profile,
                                isCurrentProfile = isCurrentProfile,
                                onClick = { onAction(AppProfileAction.SelectProfile(profile)) },
                                onEdit = { onAction(AppProfileAction.EditProfile(profile)) },
                                onDelete = { onAction(AppProfileAction.ConfirmDeleteProfile(profile)) },
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (state.showCreateDialog) {
        ProfileFormDialog(
            isEdit = false,
            onSubmit = { name, emoji, isKids ->
                onAction(AppProfileAction.SubmitCreateProfile(name, emoji, isKids))
            },
            onDismiss = { onAction(AppProfileAction.DismissDialog) },
        )
    }

    if (state.showEditDialog && state.profileToEdit != null) {
        ProfileFormDialog(
            isEdit = true,
            initialName = state.profileToEdit.name,
            initialEmoji = state.profileToEdit.avatarEmoji ?: "\uD83D\uDE0A",
            initialIsKids = state.profileToEdit.isKidsProfile,
            onSubmit = { name, emoji, isKids ->
                onAction(
                    AppProfileAction.SubmitEditProfile(
                        state.profileToEdit.id, name, emoji, isKids
                    )
                )
            },
            onDismiss = { onAction(AppProfileAction.DismissDialog) },
        )
    }

    if (state.showDeleteConfirmation && state.profileToDelete != null) {
        DeleteProfileConfirmDialog(
            profileName = state.profileToDelete.name,
            onConfirm = { onAction(AppProfileAction.DeleteProfile(state.profileToDelete.id)) },
            onDismiss = { onAction(AppProfileAction.DismissDialog) },
        )
    }

    // PIN verification dialog
    if (state.showPinDialog) {
        ParentalPinDialog(
            mode = PinDialogMode.VerifyPin,
            error = state.pinError,
            onPinSubmit = { pin -> onAction(AppProfileAction.VerifyPin(pin)) },
            onDismiss = { onAction(AppProfileAction.DismissPin) },
        )
    }
}

@Composable
fun AppProfileListItem(
    profile: Profile,
    isCurrentProfile: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
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
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                )
                if (profile.isKidsProfile) {
                    Text(
                        stringResource(R.string.profile_badge_kids),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Current profile indicator
            if (isCurrentProfile) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.profile_current_indicator),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
            }

            // Edit button
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.profile_edit_description),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Delete button (hidden for current profile)
            if (!isCurrentProfile) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.profile_delete_description),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewAppProfileSwitchScreen() {
    val profiles = kotlinx.collections.immutable.persistentListOf(
        Profile(id = "1", name = "Chakir", avatarEmoji = "\uD83D\uDC64"),
        Profile(id = "2", name = "Guest", avatarEmoji = "\uD83D\uDC65"),
    )
    AppProfileSwitchScreen(
        state = AppProfileUiState(profiles = profiles, activeProfile = profiles[0], isLoading = false),
        onAction = {},
        onNavigateBack = {},
    )
}
