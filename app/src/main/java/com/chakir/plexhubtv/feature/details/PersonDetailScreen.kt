package com.chakir.plexhubtv.feature.details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.R
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.chakir.plexhubtv.core.designsystem.NetflixBlack
import com.chakir.plexhubtv.core.designsystem.NetflixLightGray
import com.chakir.plexhubtv.core.model.Person
import com.chakir.plexhubtv.core.model.PersonCredit

@Composable
fun PersonDetailRoute(
    viewModel: PersonDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    BackHandler(onBack = onNavigateBack)

    PersonDetailScreen(
        state = uiState,
        onBack = onNavigateBack,
        onToggleFavorite = viewModel::toggleFavorite,
    )
}

@Composable
private fun PersonDetailScreen(
    state: PersonDetailUiState,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack),
    ) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )
            }

            state.error != null -> {
                Text(
                    text = state.error,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            state.person != null -> {
                val person = state.person
                Column(modifier = Modifier.fillMaxSize()) {
                    // ── FIXED HEADER: Photo + Name + Meta + Bio (always visible) ──
                    PersonFixedHeader(
                        person = person,
                        isFavorite = state.isFavorite,
                        onToggleFavorite = onToggleFavorite,
                        modifier = Modifier.fillMaxHeight(0.38f),
                    )

                    // ── SCROLLABLE BODY: Credit rows ──
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 40.dp),
                    ) {
                        // Cast credits (Acting)
                        if (person.castCredits.isNotEmpty()) {
                            item(key = "cast_header") {
                                SectionHeader(
                                    title = stringResource(R.string.person_known_for),
                                    modifier = Modifier.padding(start = 48.dp, top = 16.dp, bottom = 12.dp),
                                )
                            }
                            item(key = "cast_row") {
                                CreditRow(credits = person.castCredits)
                            }
                        }

                        // Crew credits (Directing, Writing, etc.)
                        if (person.crewCredits.isNotEmpty()) {
                            item(key = "crew_header") {
                                SectionHeader(
                                    title = stringResource(R.string.person_behind_camera),
                                    modifier = Modifier.padding(start = 48.dp, top = 24.dp, bottom = 12.dp),
                                )
                            }
                            item(key = "crew_row") {
                                CreditRow(credits = person.crewCredits)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonFixedHeader(
    person: Person,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 48.dp, end = 48.dp, bottom = 8.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Row {
            // Photo
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(person.photoUrl)
                    .build(),
                contentDescription = person.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape),
            )

            Spacer(modifier = Modifier.width(32.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = person.name,
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = stringResource(if (isFavorite) R.string.person_remove_favorite else R.string.person_add_favorite),
                            tint = if (isFavorite) Color(0xFFE91E63) else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Metadata row
                val metaItems = buildList {
                    person.knownFor?.let { add(it) }
                    person.birthday?.let { add(context.getString(R.string.person_born, it)) }
                    person.placeOfBirth?.let { add(it) }
                }
                if (metaItems.isNotEmpty()) {
                    Text(
                        text = metaItems.joinToString(" \u2022 "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NetflixLightGray,
                    )
                }

                val bio = person.biography
                if (bio != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

@Composable
private fun CreditRow(credits: List<PersonCredit>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(credits, key = { "${it.id}_${it.character ?: it.job}" }) { credit ->
            CreditCard(credit = credit)
        }
    }
}

@Composable
private fun CreditCard(credit: PersonCredit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(
        modifier = Modifier
            .width(140.dp)
            .focusable(interactionSource = interactionSource)
            .scale(if (isFocused) 1.05f else 1f),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(credit.posterUrl)
                .build(),
            contentDescription = credit.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
                .then(
                    if (isFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                    else Modifier
                ),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = credit.title,
            style = MaterialTheme.typography.bodySmall,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.8f),
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val subtitle = credit.character ?: credit.job
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = NetflixLightGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        credit.year?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = NetflixLightGray.copy(alpha = 0.7f),
            )
        }
    }
}
