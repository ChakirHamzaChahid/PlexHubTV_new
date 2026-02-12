package com.chakir.plexhubtv.feature.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.core.model.Profile

/**
 * Route pour l'écran de sélection de profil avec ViewModel.
 */
@Composable
fun ProfileSelectionRoute(
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateToHome: () -> Unit,
    onNavigateToManageProfiles: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val navigationEvents = viewModel.navigationEvents

    // Handle navigation events
    LaunchedEffect(navigationEvents) {
        navigationEvents.collect { event ->
            when (event) {
                is ProfileNavigationEvent.NavigateToHome -> onNavigateToHome()
                is ProfileNavigationEvent.NavigateToManageProfiles -> onNavigateToManageProfiles()
                is ProfileNavigationEvent.NavigateBack -> onNavigateToHome()
            }
        }
    }

    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        ProfileSelectionScreen(
            profiles = uiState.profiles,
            onProfileSelected = { viewModel.onAction(ProfileAction.SelectProfile(it)) },
            onManageProfiles = { viewModel.onAction(ProfileAction.ManageProfiles) }
        )
    }

    // Show error snackbar if needed
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // TODO: Show error snackbar
        }
    }
}

/**
 * Écran de sélection de profil.
 * Affiche tous les profils disponibles avec possibilité d'en créer un nouveau.
 */
@Composable
fun ProfileSelectionScreen(
    profiles: List<Profile>,
    onProfileSelected: (Profile) -> Unit,
    onManageProfiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Text(
                text = "Who's watching?",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Profile Grid
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(horizontal = 48.dp)
            ) {
                profiles.forEach { profile ->
                    ProfileCard(
                        profile = profile,
                        onClick = { onProfileSelected(profile) }
                    )
                }

                // Add Profile Button
                if (profiles.size < 5) {
                    AddProfileCard(onClick = onManageProfiles)
                }
            }

            // Manage Profiles Button
            TextButton(
                onClick = onManageProfiles,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("Manage Profiles")
            }
        }
    }
}

/**
 * Carte de profil individuelle.
 */
@Composable
private fun ProfileCard(
    profile: Profile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        label = "scale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "border"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(4.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val emoji = profile.avatarEmoji
            if (emoji != null && emoji.isNotEmpty()) {
                Text(
                    text = emoji,
                    fontSize = 56.sp
                )
            } else {
                Text(
                    text = profile.name.first().uppercase(),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Name
        Text(
            text = profile.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            color = if (isFocused) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center
        )

        // Kids Badge
        if (profile.isKidsProfile) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "KIDS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Carte pour ajouter un nouveau profil.
 */
@Composable
private fun AddProfileCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        label = "scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Add Button
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .border(
                    width = 2.dp,
                    color = if (isFocused) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add Profile",
                tint = if (isFocused) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(48.dp)
            )
        }

        // Label
        Text(
            text = "Add Profile",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            color = if (isFocused) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center
        )
    }
}
