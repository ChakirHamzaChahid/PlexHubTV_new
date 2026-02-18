package com.chakir.plexhubtv.feature.auth.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.chakir.plexhubtv.core.model.PlexHomeUser

/**
 * Écran de sélection de profil (Plex Home).
 * Permet de choisir quel utilisateur regarde (User Switching).
 */
@Composable
fun ProfileRoute(
    viewModel: ProfileViewModel = hiltViewModel(),
    onSwitchSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.switchSuccess) {
        if (uiState.switchSuccess) {
            onSwitchSuccess()
        }
    }

    ProfileScreen(
        state = uiState,
        onAction = viewModel::onAction,
        onBack = onBack,
    )
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag("screen_profiles")
                .semantics { contentDescription = "Écran de sélection de profil" }
                .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "Qui regarde ?",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(48.dp))

            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(32.dp)
                        .testTag("profile_loading")
                        .semantics { contentDescription = "Chargement des profils" }
                )
            } else if (state.error != null) {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(16.dp)
                        .testTag("profile_error")
                        .semantics { contentDescription = "Erreur: ${state.error}" },
                )
                Button(onClick = { onAction(ProfileAction.LoadUsers) }) {
                    Text("Réessayer")
                }
            } else {
                val gridState = rememberLazyGridState()
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(32.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .testTag("profile_list")
                        .semantics { contentDescription = "Liste des profils" },
                ) {
                    items(state.users, key = { it.id }) { user ->
                        UserProfileCard(
                            user = user,
                            onClick = { onAction(ProfileAction.SelectUser(user)) },
                        )
                    }
                }
            }
        }

        if (state.showPinDialog && state.selectedUser != null) {
            PinEntryDialog(
                user = state.selectedUser,
                pinValue = state.pinValue,
                onDigitEnter = { onAction(ProfileAction.EnterPinDigit(it)) },
                onCancel = { onAction(ProfileAction.CancelPin) },
                onClear = { onAction(ProfileAction.ClearPin) },
            )
        }

        if (state.isSwitching) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .testTag("profile_switching")
                        .semantics { contentDescription = "Changement de profil en cours" }
                        .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        // Back Button
        TextButton(
            onClick = onBack,
            modifier = Modifier.padding(16.dp).align(Alignment.BottomStart),
        ) {
            Text("Retour")
        }
    }
}

@Composable
fun UserProfileCard(
    user: PlexHomeUser,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .testTag("profile_card_${user.id}")
                .semantics { contentDescription = "Profil: ${user.title}" }
                .clickable { onClick() }
                .padding(12.dp),
    ) {
        AsyncImage(
            model = user.thumb,
            contentDescription = user.title,
            modifier =
                Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = user.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (user.protected || user.hasPassword) {
            Text(
                text = "🔒 Protégé",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PinEntryDialog(
    user: PlexHomeUser,
    pinValue: String,
    onDigitEnter: (String) -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("profile_pin_dialog")
                    .semantics { contentDescription = "Dialogue de saisie du code PIN" },
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Code PIN pour ${user.title}",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 24.dp),
                ) {
                    repeat(4) { index ->
                        val char = pinValue.getOrNull(index)?.let { "*" } ?: ""
                        Box(
                            modifier =
                                Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = char, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Numeric Keypad
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val rows =
                        listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                            listOf("Annuler", "0", "Effacer"),
                        )

                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { key ->
                                Button(
                                    onClick = {
                                        when (key) {
                                            "Annuler" -> onCancel()
                                            "Effacer" -> onClear()
                                            else -> onDigitEnter(key)
                                        }
                                    },
                                    modifier = Modifier.size(width = 80.dp, height = 48.dp),
                                    colors =
                                        if (key == "Annuler") {
                                            ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error,
                                            )
                                        } else {
                                            ButtonDefaults.buttonColors()
                                        },
                                ) {
                                    Text(text = key, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
