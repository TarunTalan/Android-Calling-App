package com.example.myapplication.ui.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.PhoneCallback
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.ui.components.DialKey
import com.example.myapplication.ui.theme.CallGreen
import com.example.myapplication.ui.theme.CallGreenDark
import com.example.myapplication.viewmodel.CallViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private val DIAL_KEYS = listOf(
    "1" to "",
    "2" to "ABC",
    "3" to "DEF",
    "4" to "GHI",
    "5" to "JKL",
    "6" to "MNO",
    "7" to "PQRS",
    "8" to "TUV",
    "9" to "WXYZ",
    "*" to "",
    "0" to "+",
    "#" to ""
)

private const val MAX_DIAL_LENGTH = 15

// ─────────────────────────────────────────────────────────────────────────────
// Data model
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A contact entry used for dial-pad suggestions and incoming call resolution.
 *
 * [numberLabel] is the phone-type string from ContactsContract ("Mobile", "Work", …).
 * It defaults to empty so the existing hardcoded fallback list still compiles.
 */
data class ContactSuggestion(
    val name: String, val number: String, val numberLabel: String = "",
    /** Avatar initials, e.g. "JD" for "John Doe". */
    val initials: String = name.split(" ").filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
)

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun numberFontSize(length: Int): Int = when {
    length >= 15 -> 20
    length >= 13 -> 22
    length >= 11 -> 26
    length >= 9 -> 30
    length >= 7 -> 34
    length >= 5 -> 38
    else -> 42
}

// ─────────────────────────────────────────────────────────────────────────────
// Root screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DialPadScreen(
    viewModel: CallViewModel, context: Context
) {
    val dialedNumber by viewModel.dialedNumber.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val callPermission = rememberPermissionState(Manifest.permission.CALL_PHONE)

    // ── READ_CONTACTS permission ──────────────────────────────────────────────
    val contactsPermission = rememberPermissionState(Manifest.permission.READ_CONTACTS)

    // Load real device contacts once READ_CONTACTS is granted.
    LaunchedEffect(contactsPermission.status.isGranted) {
        if (contactsPermission.status.isGranted) {
            viewModel.loadDeviceContacts(context)
        }
    }

    // ── TextFieldValue state ──────────────────────────────────────────────────
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    // Sync ViewModel → local state only when text actually differs.
    // This prevents the LaunchedEffect from snapping the cursor to end on
    // selection-only changes (the cursor-revert bug).
    LaunchedEffect(dialedNumber) {
        if (dialedNumber != textFieldValue.text) {
            textFieldValue = TextFieldValue(
                text = dialedNumber, selection = TextRange(dialedNumber.length)
            )
        }
    }

    val contactName = viewModel.resolveContactName(dialedNumber)

    val contactSuggestions: List<ContactSuggestion> = remember(dialedNumber) {
        viewModel.searchContacts(dialedNumber)
    }

    // Shared handler: notify ViewModel only when text changes, not cursor moves.
    val onValueChange: (TextFieldValue) -> Unit = remember(viewModel) {
        { updated ->
            val prevText = textFieldValue.text
            textFieldValue = updated
            if (updated.text != prevText) {
                viewModel.setNumber(updated.text)
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // ── Header ────────────────────────────────────────────────────────
            DialPadHeader(onSimulateIncoming = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.simulateIncomingCall()
            })

            // ── Contact suggestions (vertical Truecaller-style) ───────────────
            // Slides in above the spacer so it pushes the dial-pad DOWN, keeping
            // the keypad always visible.
            AnimatedVisibility(
                visible = contactSuggestions.isNotEmpty(),
                enter = fadeIn(tween(180)) + expandVertically(tween(200)),
                exit = fadeOut(tween(130)) + shrinkVertically(tween(160))
            ) {
                ContactSuggestionsList(
                    suggestions = contactSuggestions, onSelect = { suggestion ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    textFieldValue = TextFieldValue(
                        text = suggestion.number, selection = TextRange(suggestion.number.length)
                    )
                    viewModel.setNumber(suggestion.number)
                },
                    // Nudge to grant permission shown at bottom of list when not yet granted
                    onRequestContactsPermission = if (!contactsPermission.status.isGranted) {
                        { contactsPermission.launchPermissionRequest() }
                    } else null)
            }

            // Push the number display + keypad to the bottom half of the screen
            Spacer(modifier = Modifier.weight(1f))

            // ── Number display ────────────────────────────────────────────────
            NumberDisplayArea(
                textFieldValue = textFieldValue, contactName = contactName, onTextFieldValueChange = onValueChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Dial-pad grid ─────────────────────────────────────────────────
            DialPadGrid(
                textFieldValue = textFieldValue, onTextFieldValueChange = { updated ->
                    val prevText = textFieldValue.text
                    textFieldValue = updated
                    if (updated.text != prevText) {
                        viewModel.setNumber(updated.text)
                    }
                })

            Spacer(modifier = Modifier.height(16.dp))

            // ── Bottom action row ─────────────────────────────────────────────
            BottomActionRow(
                dialedNumber = dialedNumber,
                textFieldValue = textFieldValue,
                callPermission = callPermission,
                onCall = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (callPermission.status.isGranted) {
                        try {
                            val intent = Intent(Intent.ACTION_CALL, "tel:$dialedNumber".toUri())
                            context.startActivity(intent)
                        } catch (_: SecurityException) {
                            callPermission.launchPermissionRequest()
                        }
                    } else {
                        callPermission.launchPermissionRequest()
                    }
                    viewModel.startOutgoingCall()
                },
                onBackspace = { updated ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    textFieldValue = updated
                    viewModel.setNumber(updated.text)
                },
                onClearAll = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    textFieldValue = TextFieldValue("")
                    viewModel.clearNumber()
                })

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DialPadHeader(onSimulateIncoming: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Keypad", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground
        )
        OutlinedButton(
            onClick = onSimulateIncoming,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = CallGreen),
            border = BorderStroke(1.dp, CallGreen.copy(alpha = 0.5f)),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PhoneCallback,
                contentDescription = "Simulate incoming call",
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(text = "Incoming", fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Contact suggestions list  —  Truecaller / AOSP dialer style
//
// Vertical LazyColumn, max 4 rows visible before scrolling.
// Each row:  [Avatar]  [Name (bold) / Number · Type label]  [Quick-call button]
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ContactSuggestionsList(
    suggestions: List<ContactSuggestion>,
    onSelect: (ContactSuggestion) -> Unit,
    onRequestContactsPermission: (() -> Unit)?
) {
    val itemHeightDp = 64.dp
    val maxVisible = 4
    val listHeight = itemHeightDp * minOf(suggestions.size, maxVisible)

    Column(modifier = Modifier.fillMaxWidth()) {

        HorizontalDivider(
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f), thickness = 0.5.dp
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(listHeight)
        ) {
            items(suggestions, key = { "${it.name}|${it.number}" }) { suggestion ->
                ContactSuggestionRow(
                    suggestion = suggestion, onClick = { onSelect(suggestion) })
            }
        }

        // Permission nudge — only shown when contacts haven't been granted yet
        if (onRequestContactsPermission != null) {
            TextButton(
                onClick = onRequestContactsPermission, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            ) {
                Text(
                    text = "Allow contacts access for more suggestions",
                    fontSize = 11.sp,
                    color = CallGreen.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f), thickness = 0.5.dp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Single suggestion row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ContactSuggestionRow(
    suggestion: ContactSuggestion, onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = ripple(bounded = true)
        ) { onClick() }.padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── Avatar circle with initials ───────────────────────────────────────
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(avatarColor(suggestion.name)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = suggestion.initials, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White
            )
        }

        // ── Name + number / label ─────────────────────────────────────────────
        Column(
            modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = suggestion.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = formatDisplayNumber(suggestion.number),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (suggestion.numberLabel.isNotEmpty()) {
                    Text(
                        text = "·", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                    )
                    // e.g. "Mobile", "Work", "Home"
                    Text(
                        text = suggestion.numberLabel,
                        fontSize = 11.sp,
                        color = CallGreen.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }
            }
        }

        // ── Quick-call circle button ──────────────────────────────────────────
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(CallGreen.copy(alpha = 0.12f)).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = CallGreen.copy(alpha = 0.3f))
                ) { onClick() }, contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Call,
                contentDescription = "Call ${suggestion.name}",
                tint = CallGreen,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Deterministic avatar color derived from the contact's name hash so the
 * same contact always shows the same color across sessions.
 */
private fun avatarColor(name: String): Color {
    val palette = listOf(
        Color(0xFF5C6BC0), // indigo
        Color(0xFF26A69A), // teal
        Color(0xFFEF5350), // red
        Color(0xFFAB47BC), // purple
        Color(0xFF42A5F5), // blue
        Color(0xFFFF7043), // deep orange
        Color(0xFF66BB6A), // green
        Color(0xFFEC407A), // pink
        Color(0xFF8D6E63), // brown
        Color(0xFF78909C), // blue-grey
    )
    return palette[Math.abs(name.hashCode()) % palette.size]
}

/**
 * Simple visual formatter: "9876543210" → "98765 43210".
 * No country-code parsing — goal is readability, not E.164 compliance.
 */
private fun formatDisplayNumber(number: String): String {
    val digits = number.filter { it.isDigit() }
    return when {
        digits.length == 10 -> "${digits.substring(0, 5)} ${digits.substring(5)}"
        digits.length > 10 -> "+${digits.takeLast(12).chunked(3).joinToString(" ")}"
        else -> number
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Number display — BasicTextField gives native selection / copy / paste
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NumberDisplayArea(
    textFieldValue: TextFieldValue, contactName: String, onTextFieldValueChange: (TextFieldValue) -> Unit
) {
    val fontSize = numberFontSize(textFieldValue.text.length)
    val onBackground = MaterialTheme.colorScheme.onBackground

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
    ) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = onTextFieldValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = fontSize.sp, fontWeight = FontWeight.Thin, color = onBackground, textAlign = TextAlign.Center
            ),
            cursorBrush = SolidColor(CallGreen),
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
                .padding(vertical = 8.dp, horizontal = 4.dp)
        )

        // Resolved contact name shown below the number
        AnimatedVisibility(
            visible = contactName.isNotEmpty(), enter = fadeIn(tween(180)), exit = fadeOut(tween(120))
        ) {
            Text(
                text = contactName,
                fontSize = 15.sp,
                color = CallGreen,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dial-pad grid
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DialPadGrid(
    textFieldValue: TextFieldValue, onTextFieldValueChange: (TextFieldValue) -> Unit
) {
    DIAL_KEYS.chunked(3).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            row.forEach { (digit, sub) ->
                DialKey(digit = digit, subText = sub, size = 76.dp, onPress = { d ->
                    val cur = textFieldValue
                    val s = cur.selection.start
                    val e = cur.selection.end
                    if (cur.text.length < MAX_DIAL_LENGTH) {
                        val newText = cur.text.substring(0, s) + d + cur.text.substring(e)
                        onTextFieldValueChange(
                            TextFieldValue(text = newText, selection = TextRange(s + d.length))
                        )
                    }
                }, onLongPress = if (digit == "0") { _ ->
                    val cur = textFieldValue
                    val s = cur.selection.start
                    val e = cur.selection.end
                    if (cur.text.length < MAX_DIAL_LENGTH) {
                        val newText = cur.text.substring(0, s) + "+" + cur.text.substring(e)
                        onTextFieldValueChange(
                            TextFieldValue(text = newText, selection = TextRange(s + 1))
                        )
                    }
                } else null)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom action row
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class, ExperimentalPermissionsApi::class)
@Composable
private fun BottomActionRow(
    dialedNumber: String,
    textFieldValue: TextFieldValue,
    callPermission: com.google.accompanist.permissions.PermissionState,
    onCall: () -> Unit,
    onBackspace: (TextFieldValue) -> Unit,
    onClearAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(76.dp)) // phantom spacer

        // ── Call button ───────────────────────────────────────────────────────
        Box(
            contentAlignment = Alignment.Center, modifier = Modifier.size(76.dp).clip(CircleShape).background(
                    if (dialedNumber.isNotEmpty()) Brush.radialGradient(listOf(CallGreen, CallGreenDark))
                    else Brush.radialGradient(
                        listOf(CallGreen.copy(alpha = 0.4f), CallGreenDark.copy(alpha = 0.4f))
                    )
                ).combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = Color.White.copy(alpha = 0.3f)),
                    enabled = dialedNumber.isNotEmpty(),
                    onClick = onCall
                )
        ) {
            Icon(
                imageVector = Icons.Filled.Call,
                contentDescription = "Call",
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }

        // ── Backspace button ──────────────────────────────────────────────────
        Box(modifier = Modifier.size(76.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(
                visible = dialedNumber.isNotEmpty(),
                enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.8f),
                exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.8f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(76.dp).clip(CircleShape).combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, color = Color.White.copy(alpha = 0.15f)),
                            onClick = {
                                val cur = textFieldValue
                                val s = cur.selection.start
                                val e = cur.selection.end
                                val newText: String
                                val newCursor: Int
                                if (s != e) {
                                    newText = cur.text.removeRange(s, e)
                                    newCursor = s
                                } else if (s > 0) {
                                    newText = cur.text.removeRange(s - 1, s)
                                    newCursor = s - 1
                                } else return@combinedClickable
                                onBackspace(
                                    TextFieldValue(text = newText, selection = TextRange(newCursor))
                                )
                            },
                            onLongClick = onClearAll
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ContactSuggestionRowPreview() {
    MaterialTheme {
        ContactSuggestionRow(
            suggestion = ContactSuggestion(
                name = "Rohan Sharma", number = "9876543210", numberLabel = "Mobile"
            ), onClick = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ContactSuggestionsListPreview() {
    MaterialTheme {
        ContactSuggestionsList(
            suggestions = listOf(
                ContactSuggestion("Rohan Sharma", "9876543210", "Mobile"),
                ContactSuggestion("Rahul Gupta", "9123456789", "Work"),
                ContactSuggestion("Ravi Kumar", "9000000001", "Home"),
            ), onSelect = {}, onRequestContactsPermission = null
        )
    }
}