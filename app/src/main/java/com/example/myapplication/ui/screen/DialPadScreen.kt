package com.example.myapplication.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.ui.components.CircleButton
import com.example.myapplication.ui.components.DialKey
import com.example.myapplication.ui.theme.*
import com.example.myapplication.utils.formatPhoneDisplay
import com.example.myapplication.viewmodel.CallViewModel

// ─── Data model ───────────────────────────────────────────────────────────────
data class ContactSuggestion(
    val name: String, val number: String, val numberLabel: String = ""
)

// ─── Dial-pad key layout ──────────────────────────────────────────────────────
private val DIAL_KEYS = listOf(
    listOf("1" to "", "2" to "ABC", "3" to "DEF"),
    listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
    listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
    listOf("*" to "", "0" to "+", "#" to "")
)

private val FAB_SIZE = 60.dp
private val FAB_MARGIN = 20.dp

// ─── Allowed characters ───────────────────────────────────────────────────────
private fun String.filterDialChars() = filter { it.isDigit() || it == '+' || it == '*' || it == '#' }.take(15)

// ─── Cursor-aware insert ──────────────────────────────────────────────────────
private fun insertAtCursor(tfv: TextFieldValue, str: String): TextFieldValue {
    val text = tfv.text
    val s = tfv.selection.start.coerceIn(0, text.length)
    val e = tfv.selection.end.coerceIn(0, text.length)
    val newText = (text.substring(0, s) + str + text.substring(e)).filterDialChars()
    val newCursor = (s + str.length).coerceAtMost(newText.length)
    return tfv.copy(text = newText, selection = TextRange(newCursor))
}

// ─── Cursor-aware delete ──────────────────────────────────────────────────────
private fun deleteAtCursor(tfv: TextFieldValue): TextFieldValue {
    val text = tfv.text
    val s = tfv.selection.start.coerceIn(0, text.length)
    val e = tfv.selection.end.coerceIn(0, text.length)
    return if (s != e) {
        val newText = text.substring(0, s) + text.substring(e)
        tfv.copy(text = newText, selection = TextRange(s))
    } else if (s > 0) {
        val newText = text.substring(0, s - 1) + text.substring(s)
        tfv.copy(text = newText, selection = TextRange(s - 1))
    } else {
        tfv
    }
}

// ─── Pre-allocated Layout Resources ───────────────────────────────────────────
private val ScreenBackgroundBrush = Brush.verticalGradient(
    colors = listOf(SurfaceDark, BackgroundDark, BackgroundDark)
)
private val PanelBackgroundBrush = Brush.verticalGradient(
    colors = listOf(SurfaceElevated, SurfaceDark)
)
private val AvatarBrush = Brush.radialGradient(
    listOf(Color(0xFF1C2E1C), Color(0xFF0D180D))
)

// ─── Number Spacing Formatter ─────────────────────────────────────────────────
private class SimplePhoneVisualTransformation : VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): TransformedText {
        val raw = text.text
        var out = ""
        val originalToTransformed = mutableListOf<Int>()
        var offset = 0
        
        for (i in raw.indices) {
            originalToTransformed.add(offset)
            out += raw[i]
            offset++
            
            // Add space for standard formats like +1 555 123 4567 or 123 456 7890
            val shouldSpace = if (raw.startsWith("+")) {
                i == 1 || i == 4 || i == 7
            } else if (raw.length <= 10) {
                i == 2 || i == 5
            } else {
                i == 4 || i == 9
            }
            
            if (shouldSpace && i != raw.lastIndex) {
                out += " "
                offset++
            }
        }
        originalToTransformed.add(offset)
        
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return if (offset < originalToTransformed.size) originalToTransformed[offset] else out.length
            }
            override fun transformedToOriginal(offset: Int): Int {
                return originalToTransformed.indexOfLast { it <= offset }.coerceAtLeast(0)
            }
        }
        return TransformedText(androidx.compose.ui.text.AnnotatedString(out), offsetMapping)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  DialpadScreen
//
//  Layer stack (root = Box):
//  0 ─ LazyColumn     (full-screen contacts list — always interactive)
//  1 ─ Scrim          (transparent, full-screen; visible only when panel is open;
//                      tapping it collapses the panel — no visual footprint)
//  2 ─ DialpadPanel   (align=BottomCenter, slides in/out with AnimatedVisibility)
//  3 ─ FAB            (align=BottomEnd, visible only when panel is collapsed)
//
//  Tap-outside behaviour: the scrim (Layer 1) covers everything above the panel.
//  A tap anywhere outside the panel footprint hits the scrim first and sets
//  panelExpanded = false, collapsing the panel back to the FAB.
//  Scroll-to-top behaviour: scrolledDown derivedState fires → panelExpanded = false.
//  Keyboard: InterceptPlatformTextInput wraps BasicTextField and swallows the
//            IME session — keyboard never appears; cursor/paste work normally.
// ═════════════════════════════════════════════════════════════════════════════
@Composable
fun DialpadScreen(viewModel: CallViewModel) {

    val dialedNumber by viewModel.dialedNumber.collectAsStateWithLifecycle()
    val contactSuggestions by viewModel.contactSuggestions.collectAsStateWithLifecycle()

    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    LaunchedEffect(dialedNumber) {
        if (dialedNumber != textFieldValue.text) {
            textFieldValue = TextFieldValue(
                text = dialedNumber, selection = TextRange(dialedNumber.length)
            )
        }
    }

    var panelExpanded by remember { mutableStateOf(true) }

    // Intercept back press while the panel is open — collapse it instead of exiting
    BackHandler(enabled = panelExpanded) { panelExpanded = false }
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    val scrolledDown by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > with(density) { 32.dp.roundToPx() }
        }
    }
    LaunchedEffect(scrolledDown) { if (scrolledDown) panelExpanded = false }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                colors = listOf(SurfaceDark, BackgroundDark, BackgroundDark)
            )
        )
    ) {

        // ── Layer 0: Contact list — ALWAYS interactive ────────────────────────
        LazyColumn(
            state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 0.dp, bottom = 100.dp
            ), verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "contacts_header") {
                ContactsHeader(
                    title = if (dialedNumber.isEmpty() && contactSuggestions.isNotEmpty()) "Recent Calls" else "Contacts",
                    onSimulateIncoming = { viewModel.simulateIncomingCall() }
                )
            }

            items(contactSuggestions, key = { "${it.name}|${it.number}" }) { contact ->
                ContactRow(
                    contact = contact, onClick = {
                        viewModel.setNumber(contact.number)
                        viewModel.startOutgoingCall()
                    })
            }
        }

        // ── Layer 1: Transparent scrim — tap outside panel collapses it ──────
        // Covers the full screen above the panel. Passes zero visual weight but
        // consumes every tap so nothing behind it fires AND the panel collapses.
        if (panelExpanded) {
            Box(
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTapGestures { panelExpanded = false }
                    })
        }

        // ── Layer 2: Dialpad panel ────────────────────────────────────────────
        // Slides in/out from the bottom edge.
        // pointerInput on the panel's modifier swallows all events within the
        // panel footprint — nothing outside (above the panel) is blocked.
        AnimatedVisibility(
            visible = panelExpanded,
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 280f), initialOffsetY = { it }) + fadeIn(
                tween(
                    180
                )
            ),
            exit = slideOutVertically(
                animationSpec = tween(220), targetOffsetY = { it }) + fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            DialpadPanel(modifier = Modifier.pointerInput(Unit) {
                // Swallow every pointer event so contacts behind the panel
                // are not accidentally triggered.
                awaitPointerEventScope { while (true) awaitPointerEvent() }
            }, textFieldValue = textFieldValue, onTextFieldValueChange = { newTfv ->
                val filtered = newTfv.text.filterDialChars()
                val finalTfv = if (filtered == newTfv.text) newTfv
                else newTfv.copy(
                    text = filtered, selection = TextRange(
                        newTfv.selection.start.coerceAtMost(filtered.length),
                        newTfv.selection.end.coerceAtMost(filtered.length)
                    )
                )
                textFieldValue = finalTfv
                viewModel.setNumber(finalTfv.text)
            }, onKeyPress = { digit ->
                val newTfv = insertAtCursor(textFieldValue, digit)
                textFieldValue = newTfv
                viewModel.setNumber(newTfv.text)
            }, onZeroLongPress = {
                val newTfv = insertAtCursor(textFieldValue, "+")
                textFieldValue = newTfv
                viewModel.setNumber(newTfv.text)
            }, onDelete = {
                val newTfv = deleteAtCursor(textFieldValue)
                textFieldValue = newTfv
                viewModel.setNumber(newTfv.text)
            }, onDeleteLong = {
                textFieldValue = TextFieldValue("")
                viewModel.clearNumber()
            }, onCall = { viewModel.startOutgoingCall() })
        }

        // ── Layer 3: FAB ──────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = !panelExpanded,
            enter = scaleIn(spring(dampingRatio = 0.6f, stiffness = 400f)) + fadeIn(tween(180)),
            exit = scaleOut(tween(160)) + fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = FAB_MARGIN, bottom = FAB_MARGIN)
        ) {
            DialpadFab(onClick = { panelExpanded = true })
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Contacts header — Simulate Incoming button pinned to top-right
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun ContactsHeader(title: String = "Contacts", onSimulateIncoming: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 52.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
        )

        SimulateIncomingButton(onClick = onSimulateIncoming)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Dialpad FAB
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun DialpadFab(onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(FAB_SIZE).shadow(
            elevation = 10.dp,
            shape = CircleShape,
            ambientColor = CallGreen.copy(alpha = 0.35f),
            spotColor = CallGreen.copy(alpha = 0.5f)
        ).clip(CircleShape).background(CallGreen).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(bounded = true, color = Color.White.copy(alpha = 0.3f)),
            onClick = onClick
        ), contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Dialpad,
            contentDescription = "Open keypad",
            tint = Color.White,
            modifier = Modifier.size(26.dp)
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  DialpadPanel
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun DialpadPanel(
    modifier: Modifier,
    textFieldValue: TextFieldValue,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    onKeyPress: (String) -> Unit,
    onZeroLongPress: () -> Unit,
    onDelete: () -> Unit,
    onDeleteLong: () -> Unit,
    onCall: () -> Unit,
) {
    val dialedNumber = textFieldValue.text

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(PanelBackgroundBrush), 
        contentAlignment = Alignment.TopCenter
    ) {
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(), thickness = 0.5.dp, color = Color.White.copy(alpha = 0.10f)
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NumberRow(
                textFieldValue = textFieldValue,
                onValueChange = onTextFieldValueChange,
                onDelete = onDelete,
                onDeleteLong = onDeleteLong,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DIAL_KEYS.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { (digit, sub) ->
                            DialKey(
                                digit = digit,
                                subText = sub,
                                onPress = onKeyPress,
                                onLongPress = if (digit == "0") ({ onZeroLongPress() }) else null,
                                size = 68.dp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            CircleButton(
                onClick = onCall,
                backgroundColor = if (dialedNumber.isNotEmpty()) CallGreen
                else CallGreen.copy(alpha = 0.32f),
                pressedColor = Color(0xFF1A7A1A),
                size = 68.dp,
                icon = Icons.Filled.Call,
                iconTint = Color.White,
                label = "Call",
                enabled = dialedNumber.isNotEmpty()
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Simulate-incoming mini button (top-right of contacts header)
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun SimulateIncomingButton(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(SurfaceVariantDark).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = Color(0xFFFFB300).copy(alpha = 0.3f)),
                onClick = onClick
            ), contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Call,
                contentDescription = "Simulate incoming call",
                tint = Color(0xFFFFB300),
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = "Incoming",
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.2.sp,
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Number display row
//
//  HOW THE KEYBOARD IS PERMANENTLY BLOCKED — TrueCaller / dialer approach
//  ────────────────────────────────────────────────────────────────────────────
//  We use BasicTextField (so cursor, selection handles, and the system
//  copy/cut/paste toolbar all work natively) wrapped inside:
//
//      InterceptPlatformTextInput { request, nextHandler ->
//          awaitCancellation()   // ← never calls nextHandler
//      }
//
//  InterceptPlatformTextInput sits at the Compose platform-text-input layer.
//  When BasicTextField gains focus it fires a startInputMethod() call upward
//  through this interceptor. Because we never forward to nextHandler, the IME
//  session is never established — the soft keyboard is never shown, on ANY
//  Android/Compose version, with NO race condition.
//
//  Cursor blinking, drag-to-select, long-press selection, and the
//  Copy/Cut/Paste/Select-All action toolbar are all driven by Compose's own
//  TextSelectionBackend, which is completely independent of the IME pipeline —
//  they remain fully functional exactly as in TrueCaller's dialer.
//
//  Paste from the toolbar calls onValueChange with the pasted text, which is
//  then filtered through filterDialChars() — only digits/+/*/#  accepted.
// ═════════════════════════════════════════════════════════════════════════════
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun NumberRow(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onDelete: () -> Unit,
    onDeleteLong: () -> Unit,
) {
    val number = textFieldValue.text

    val deleteAlpha by animateFloatAsState(
        targetValue = if (number.isNotEmpty()) 1f else 0f, animationSpec = tween(200), label = "deleteAlpha"
    )
    val fontSize = when {
        number.length > 12 -> 22.sp
        number.length > 8 -> 28.sp
        else -> 34.sp
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.size(48.dp))

        // ── Swallow IME at the platform layer — keyboard never appears ─────────
        InterceptPlatformTextInput(
            interceptor = { _, _ ->
                // Intentionally do NOT call nextHandler.startInputMethod().
                // This prevents the IME session from ever being established.
                // awaitCancellation() keeps the coroutine alive so Compose
                // doesn't retry the request on recomposition.
                kotlinx.coroutines.awaitCancellation()
            }) {
            BasicTextField(
                value = textFieldValue, onValueChange = { newTfv ->
                    // Filter pasted content to dial-safe chars only
                    val filtered = newTfv.text.filterDialChars()
                    if (filtered == newTfv.text) onValueChange(newTfv)
                    else onValueChange(
                        newTfv.copy(
                            text = filtered, selection = TextRange(
                                newTfv.selection.start.coerceAtMost(filtered.length),
                                newTfv.selection.end.coerceAtMost(filtered.length)
                            )
                        )
                    )
                }, singleLine = true, cursorBrush = SolidColor(CallGreen), textStyle = TextStyle(
                    textAlign = TextAlign.Center,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Light,
                    color = TextPrimary,
                    letterSpacing = 2.sp,
                ), modifier = Modifier.weight(1f), 
                visualTransformation = SimplePhoneVisualTransformation(),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center
                    ) {
                        if (number.isEmpty()) {
                            Text(
                                text = "",
                                color = TextSecondary.copy(alpha = 0.35f),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Light,
                                textAlign = TextAlign.Center,
                                letterSpacing = 8.sp,
                            )
                        }
                        innerTextField()
                    }
                })
        }

        // Delete — tap: delete char, long-press: clear all
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.size(36.dp).alpha(deleteAlpha).clip(CircleShape).combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Color.White.copy(alpha = 0.2f)),
                        enabled = number.isNotEmpty(),
                        onClick = onDelete,
                        onLongClick = onDeleteLong,
                    ), contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Delete",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Contact suggestion row
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun ContactRow(
    contact: ContactSuggestion,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBackground).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(bounded = true, color = Color.White.copy(alpha = 0.15f)),
            onClick = onClick
        ).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center, 
            modifier = Modifier.size(42.dp).clip(CircleShape).background(AvatarBrush)
        ) {
            Text(
                text = contact.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = CallGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.name,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatPhoneDisplay(contact.number),
                color = TextSecondary,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp
            )
        }

        if (contact.numberLabel.isNotEmpty()) {
            Text(
                text = contact.numberLabel,
                color = TextTertiary,
                fontSize = 11.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(SurfaceElevated)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        Icon(
            imageVector = Icons.Filled.Call,
            contentDescription = "Call",
            tint = CallGreen.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
    }
}

// ─── Preview ──────────────────────────────────────────────────────────────────
@Preview(showBackground = true)
@Composable
fun DialpadScreenPreview() {
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                colors = listOf(SurfaceDark, BackgroundDark, BackgroundDark)
            )
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        ) {
            ContactsHeader(onSimulateIncoming = {})
            Spacer(modifier = Modifier.height(8.dp))
            ContactRow(contact = ContactSuggestion("Alice", "+1 234 567 890"), onClick = {})
            Spacer(modifier = Modifier.height(6.dp))
            ContactRow(contact = ContactSuggestion("Bob", "+44 7700 900123", "Work"), onClick = {})
        }

        DialpadPanel(
            modifier = Modifier.align(Alignment.BottomCenter),
            textFieldValue = TextFieldValue("12345"),
            onTextFieldValueChange = {},
            onKeyPress = {},
            onZeroLongPress = {},
            onDelete = {},
            onDeleteLong = {},
            onCall = {},
        )
    }
}