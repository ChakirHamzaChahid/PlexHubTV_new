package com.chakir.plexhubtv.feature.appprofile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chakir.plexhubtv.core.designsystem.NetflixDarkGray
import com.chakir.plexhubtv.core.designsystem.NetflixRed

private val AVATAR_EMOJIS = listOf(
    "\uD83D\uDE0A", "\uD83D\uDE0E", "\uD83C\uDFAC", "\uD83C\uDFAE", "\uD83C\uDFAF", "\uD83D\uDC64",
    "\uD83E\uDD8A", "\uD83D\uDC31", "\uD83C\uDFA8", "\uD83C\uDF1F", "\uD83D\uDE80", "\uD83C\uDFB5",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileFormDialog(
    isEdit: Boolean,
    initialName: String = "",
    initialEmoji: String = "\uD83D\uDE0A",
    initialIsKids: Boolean = false,
    onSubmit: (name: String, emoji: String, isKids: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedEmoji by remember { mutableStateOf(initialEmoji) }
    var isKids by remember { mutableStateOf(initialIsKids) }
    val nameFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { nameFocusRequester.requestFocus() } catch (_: Exception) { }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = NetflixDarkGray,
                modifier = Modifier
                    .width(450.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Title
                    Text(
                        text = if (isEdit) "Edit Profile" else "New Profile",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )

                    Spacer(Modifier.height(20.dp))

                    // Name field
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(nameFocusRequester),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                        ),
                    )

                    Spacer(Modifier.height(20.dp))

                    // Emoji picker
                    Text(
                        text = "Avatar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AVATAR_EMOJIS.forEach { emoji ->
                            EmojiOption(
                                emoji = emoji,
                                isSelected = emoji == selectedEmoji,
                                onClick = { selectedEmoji = emoji },
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Kids toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Kids Profile",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = isKids,
                            onCheckedChange = { isKids = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = NetflixRed,
                            ),
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    // Buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        DialogButton(
                            text = if (isEdit) "Save" else "Create",
                            backgroundColor = NetflixRed,
                            enabled = name.isNotBlank(),
                            onClick = { onSubmit(name, selectedEmoji, isKids) },
                        )
                        DialogButton(
                            text = "Cancel",
                            backgroundColor = Color.Gray.copy(alpha = 0.5f),
                            onClick = onDismiss,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteProfileConfirmDialog(
    profileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val confirmFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { confirmFocusRequester.requestFocus() } catch (_: Exception) { }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = NetflixDarkGray,
                modifier = Modifier
                    .width(400.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Delete profile \"$profileName\"?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "This cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                    )

                    Spacer(Modifier.height(24.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        DialogButton(
                            text = "Delete",
                            backgroundColor = NetflixRed,
                            onClick = onConfirm,
                            modifier = Modifier.focusRequester(confirmFocusRequester),
                        )
                        DialogButton(
                            text = "Cancel",
                            backgroundColor = Color.Gray.copy(alpha = 0.5f),
                            onClick = onDismiss,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiOption(
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor = when {
        isSelected -> NetflixRed
        isFocused -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) NetflixRed.copy(alpha = 0.2f)
                else Color.White.copy(alpha = 0.1f)
            )
            .border(2.dp, borderColor, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, fontSize = 24.sp)
    }
}

@Composable
private fun DialogButton(
    text: String,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val effectiveAlpha = if (enabled) 1f else 0.4f
    val bgColor = if (isFocused && enabled) backgroundColor else backgroundColor.copy(alpha = 0.6f * effectiveAlpha)

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = effectiveAlpha),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}
