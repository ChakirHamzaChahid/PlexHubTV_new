package com.chakir.plexhubtv.feature.auth.profiles

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.core.designsystem.NetflixRed
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
    val screenDescription = stringResource(R.string.profile_screen_description)
    val loadingDescription = stringResource(R.string.profile_loading_description)
    val listDescription = stringResource(R.string.profile_list_description)
    val switchingDescription = stringResource(R.string.profile_switching_description)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag("screen_profiles")
                .semantics { contentDescription = screenDescription }
                .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = stringResource(R.string.profile_who_is_watching),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(48.dp))

            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(32.dp)
                        .testTag("profile_loading")
                        .semantics { contentDescription = loadingDescription }
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
                    Text(stringResource(R.string.action_retry))
                }
            } else {
                val gridState = rememberLazyGridState()
                val firstFocusRequester = remember { FocusRequester() }
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(32.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .testTag("profile_list")
                        .semantics { contentDescription = listDescription },
                ) {
                    itemsIndexed(state.users, key = { _, user -> user.id }) { index, user ->
                        UserProfileCard(
                            user = user,
                            onClick = { onAction(ProfileAction.SelectUser(user)) },
                            modifier = if (index == 0) {
                                Modifier.focusRequester(firstFocusRequester)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
                LaunchedEffect(Unit) {
                    firstFocusRequester.requestFocus()
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
                        .semantics { contentDescription = switchingDescription }
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
            Text(stringResource(R.string.action_back))
        }
    }
}

@Composable
fun UserProfileCard(
    user: PlexHomeUser,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        label = "profileCardScale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .scale(scale)
            .shadow(
                elevation = if (isFocused) 12.dp else 0.dp,
                shape = RoundedCornerShape(12.dp),
                clip = false,
            )
            .border(
                width = if (isFocused) 4.dp else 0.dp,
                color = if (isFocused) NetflixRed else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .clip(RoundedCornerShape(12.dp))
            .testTag("profile_card_${user.id}")
            .semantics { contentDescription = "Profil: ${user.title}" }
            .clickable { onClick() }
            .focusable()
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
            color = if (isFocused) NetflixRed else MaterialTheme.colorScheme.onBackground,
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
    val dialogDescription = stringResource(R.string.profile_pin_dialog_description)

    Dialog(onDismissRequest = onCancel) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("profile_pin_dialog")
                    .semantics { contentDescription = dialogDescription },
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.profile_pin_title, user.title),
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
                val cancelText = stringResource(R.string.action_cancel)
                val clearText = stringResource(R.string.profile_pin_clear)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val rows =
                        listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                            listOf(cancelText, "0", clearText),
                        )

                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { key ->
                                Button(
                                    onClick = {
                                        when (key) {
                                            cancelText -> onCancel()
                                            clearText -> onClear()
                                            else -> onDigitEnter(key)
                                        }
                                    },
                                    modifier = Modifier.size(width = 80.dp, height = 48.dp),
                                    colors =
                                        if (key == cancelText) {
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
