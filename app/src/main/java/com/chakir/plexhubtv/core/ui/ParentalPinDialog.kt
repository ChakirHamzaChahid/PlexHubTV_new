package com.chakir.plexhubtv.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.core.designsystem.NetflixDarkGray
import com.chakir.plexhubtv.core.designsystem.NetflixRed

/**
 * D-Pad friendly PIN input dialog for parental controls.
 *
 * Two modes:
 * - **SetPin**: Set or change the PIN (requires confirmation entry)
 * - **VerifyPin**: Verify PIN before switching profiles
 */
@Composable
fun ParentalPinDialog(
    mode: PinDialogMode,
    error: String? = null,
    onPinSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirmStep by remember { mutableStateOf(false) }
    var localError by remember(error) { mutableStateOf(error) }
    val firstButtonFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { firstButtonFocus.requestFocus() } catch (_: Exception) { }
    }

    val title = when {
        mode == PinDialogMode.SetPin && !isConfirmStep -> stringResource(R.string.parental_pin_set_title)
        mode == PinDialogMode.SetPin && isConfirmStep -> stringResource(R.string.parental_pin_confirm_title)
        else -> stringResource(R.string.parental_pin_verify_title)
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
                    .width(360.dp)
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
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(16.dp))

                    // PIN dots display
                    val currentPin = if (isConfirmStep) confirmPin else pin
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        repeat(4) { index ->
                            PinDot(filled = index < currentPin.length)
                        }
                    }

                    // Error message
                    if (localError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = localError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = NetflixRed,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Number pad (D-Pad friendly grid)
                    val numbers = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("", "0", ""),
                    )

                    var isFirstButton = true
                    numbers.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            row.forEach { digit ->
                                if (digit.isEmpty()) {
                                    Spacer(Modifier.size(56.dp))
                                } else {
                                    val modifier = if (isFirstButton) {
                                        isFirstButton = false
                                        Modifier.focusRequester(firstButtonFocus)
                                    } else {
                                        Modifier
                                    }
                                    NumberButton(
                                        digit = digit,
                                        modifier = modifier,
                                        onClick = {
                                            localError = null
                                            if (isConfirmStep) {
                                                if (confirmPin.length < 4) confirmPin += digit
                                            } else {
                                                if (pin.length < 4) pin += digit
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Action buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Backspace
                        PinActionButton(
                            text = stringResource(R.string.parental_pin_backspace),
                            backgroundColor = Color.Gray.copy(alpha = 0.5f),
                            onClick = {
                                if (isConfirmStep) {
                                    if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                                } else {
                                    if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                }
                            },
                        )
                        // OK
                        PinActionButton(
                            text = stringResource(R.string.parental_pin_ok),
                            backgroundColor = NetflixRed,
                            enabled = currentPin.length == 4,
                            onClick = {
                                when {
                                    mode == PinDialogMode.SetPin && !isConfirmStep -> {
                                        isConfirmStep = true
                                    }
                                    mode == PinDialogMode.SetPin && isConfirmStep -> {
                                        if (pin == confirmPin) {
                                            onPinSubmit(pin)
                                        } else {
                                            localError = "PINs do not match"
                                            confirmPin = ""
                                        }
                                    }
                                    else -> {
                                        onPinSubmit(pin)
                                    }
                                }
                            },
                        )
                        // Cancel
                        PinActionButton(
                            text = stringResource(R.string.parental_pin_cancel),
                            backgroundColor = Color.Gray.copy(alpha = 0.5f),
                            onClick = onDismiss,
                        )
                    }
                }
            }
        }
    }
}

enum class PinDialogMode {
    SetPin,
    VerifyPin,
}

@Composable
private fun PinDot(filled: Boolean) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .background(
                if (filled) Color.White else Color.White.copy(alpha = 0.3f),
                CircleShape,
            ),
    )
}

@Composable
private fun NumberButton(
    digit: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isFocused) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f),
        modifier = modifier
            .size(56.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = digit,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun PinActionButton(
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
