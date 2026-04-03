package com.bitchat.android.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ── Colour palette ──────────────────────────────────────────────────────────
private val WTBg          = Color(0xFF0A0A0A)           // near-black background
private val WTSurface     = Color(0xFF141414)            // card surfaces
private val WTAmber       = Color(0xFFFFAA00)            // amber accent (header)
private val WTGreen       = Color(0xFF00FF41)            // LCD green
private val WTRed         = Color(0xFFFF1A1A)            // transmit / alert red
private val WTOrange      = Color(0xFFFF6600)            // PTT button idle
private val WTDim         = Color(0xFF444444)
private val WTTextMuted   = Color(0xFF888888)

/**
 * Improvement 5 — Dedicated Walkie-Talkie / Disaster Radio Screen
 *
 * Full-screen modal overlay with military/emergency radio aesthetic.
 * Completely separate from the voice-note recording in the chat input.
 *
 * Features:
 *  - Large hold-to-talk PTT button with pulsing ring animation
 *  - ON AIR / STANDBY status indicator
 *  - Connected-units list (live peer list)
 *  - Animated signal bars while PTT is active
 *  - PRIORITY mode toggle (Guardian)
 *  - Clock + elapsed transmit timer
 */
@Composable
fun WalkieTalkieScreen(
    viewModel: ChatViewModel,
    onClose: () -> Unit
) {
    val connectedPeers  by viewModel.connectedPeers.observeAsState(emptyList())
    val peerNicknames   by viewModel.peerNicknames.observeAsState(emptyMap())
    val isGuardian      by viewModel.isGuardianMode.observeAsState(false)
    val isTransmitting  by viewModel.isPTTTransmitting.observeAsState(false)

    val scope = rememberCoroutineScope()
    var elapsedSec by remember { mutableStateOf(0) }
    var clockStr   by remember { mutableStateOf("") }
    var priorityOn by remember { mutableStateOf(isGuardian) }

    // Clock ticker
    LaunchedEffect(Unit) {
        while (true) {
            clockStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            delay(1000)
        }
    }

    // Elapsed transmit timer
    LaunchedEffect(isTransmitting) {
        if (isTransmitting) {
            elapsedSec = 0
            while (isTransmitting) {
                delay(1000)
                elapsedSec++
            }
        } else {
            elapsedSec = 0
        }
    }

    // PTT button pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "ptt_pulse")
    val pulseRing by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.45f,
        animationSpec = infiniteRepeatable(
            animation   = tween(700, easing = FastOutSlowInEasing),
            repeatMode  = RepeatMode.Reverse
        ),
        label = "ring"
    )
    val pttScale = if (isTransmitting) 1.06f else 1f

    // Signal bar heights while transmitting
    val barHeights = (0 until 5).map { i ->
        val speed = 350 + i * 80
        val bar by infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue  = 1f,
            animationSpec = infiniteRepeatable(
                animation  = tween(speed, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar$i"
        )
        bar
    }

    Dialog(
        onDismissRequest = {
            // stop PTT if somehow dismissed while transmitting
            if (isTransmitting) viewModel.stopPTT()
            onClose()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WTBg)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {

                // ── TOP BAR ─────────────────────────────────────────────────
                WTTopBar(
                    clockStr    = clockStr,
                    peerCount   = connectedPeers.size,
                    priorityOn  = priorityOn,
                    onPriority  = { priorityOn = !priorityOn },
                    onClose     = {
                        if (isTransmitting) viewModel.stopPTT()
                        onClose()
                    }
                )

                // ── STATUS STRIPE ────────────────────────────────────────────
                WTStatusStripe(
                    isTransmitting = isTransmitting,
                    elapsedSec     = elapsedSec,
                    priorityOn     = priorityOn
                )

                // ── SIGNAL VISUALISER ────────────────────────────────────────
                WTSignalBars(
                    isTransmitting = isTransmitting,
                    barHeights     = barHeights
                )

                Spacer(Modifier.weight(1f))

                // ── PTT BUTTON ───────────────────────────────────────────────
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    // Outer pulsing ring — only visible when transmitting
                    if (isTransmitting) {
                        Box(
                            modifier = Modifier
                                .size(210.dp)
                                .scale(pulseRing)
                                .background(
                                    WTRed.copy(alpha = 0.18f),
                                    CircleShape
                                )
                                .border(1.dp, WTRed.copy(alpha = 0.5f), CircleShape)
                        )
                    }

                    // Main PTT button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(190.dp)
                            .scale(pttScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = if (isTransmitting)
                                        listOf(WTRed, Color(0xFF880000))
                                    else
                                        listOf(WTOrange, Color(0xFF993D00))
                                )
                            )
                            .border(
                                3.dp,
                                if (isTransmitting) WTRed else WTOrange,
                                CircleShape
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        viewModel.startPTT()
                                        tryAwaitRelease()
                                        viewModel.stopPTT()
                                    }
                                )
                            }
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (isTransmitting) Icons.Filled.Mic else Icons.Filled.MicNone,
                                contentDescription = "PTT",
                                tint = Color.White,
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (isTransmitting) "RELEASE TO SEND" else "PUSH\nTO TALK",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // ── CONNECTED UNITS ──────────────────────────────────────────
                WTUnitList(
                    peers         = connectedPeers,
                    peerNicknames = peerNicknames
                )

                // ── BOTTOM INFO BAR ──────────────────────────────────────────
                WTBottomBar(priorityOn = priorityOn)
            }
        }
    }
}

// ── Sub-composables ─────────────────────────────────────────────────────────

@Composable
private fun WTTopBar(
    clockStr:   String,
    peerCount:  Int,
    priorityOn: Boolean,
    onPriority: () -> Unit,
    onClose:    () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WTSurface)
            .border(BorderStroke(0.5.dp, WTAmber.copy(alpha = 0.4f)))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Close button
        androidx.compose.material3.IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Close, "Close", tint = WTAmber, modifier = Modifier.size(22.dp))
        }

        Spacer(Modifier.width(8.dp))

        // Title
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "▲ DECHAT RADIO",
                color = WTAmber,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            Text(
                text = "WALKIE-TALKIE MODE",
                color = WTAmber.copy(alpha = 0.6f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 3.sp
            )
        }

        // Clock
        Text(
            text = clockStr,
            color = WTGreen,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.width(12.dp))

        // Peer count chip
        Box(
            modifier = Modifier
                .background(WTGreen.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                .border(0.5.dp, WTGreen.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "$peerCount UNIT${if (peerCount != 1) "S" else ""}",
                color = WTGreen,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun WTStatusStripe(
    isTransmitting: Boolean,
    elapsedSec:     Int,
    priorityOn:     Boolean
) {
    val bgColor = if (isTransmitting) WTRed.copy(alpha = 0.15f) else WTSurface
    val statusText = if (isTransmitting) "● ON AIR" else "◎ STANDBY"
    val statusColor = if (isTransmitting) WTRed else WTGreen.copy(alpha = 0.6f)

    // ON AIR blink
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 0.3f,
        animationSpec = infiniteRepeatable(
            animation  = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink"
    )
    val dotAlpha = if (isTransmitting) alpha else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .border(BorderStroke(0.5.dp, if (isTransmitting) WTRed.copy(0.4f) else WTDim))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = statusText,
            color = statusColor.copy(alpha = if (isTransmitting) dotAlpha else 1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )

        // Elapsed timer
        if (isTransmitting) {
            val mm = elapsedSec / 60
            val ss = elapsedSec % 60
            Text(
                text = "TX  %02d:%02d / 00:10".format(mm, ss),
                color = WTRed,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                SignalStrengthBars(level = 4)
                Text(
                    text = "MESH",
                    color = WTGreen,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Priority badge
        if (priorityOn) {
            Box(
                modifier = Modifier
                    .background(WTAmber.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                    .border(0.5.dp, WTAmber.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "🛡 GUARDIAN",
                    color = WTAmber,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun WTSignalBars(
    isTransmitting: Boolean,
    barHeights: List<Float>
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(WTBg)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (isTransmitting) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(18) { i ->
                    val barIndex = i % 5
                    val heightFraction = barHeights[barIndex]
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(heightFraction)
                            .background(
                                WTGreen.copy(alpha = 0.6f + heightFraction * 0.4f),
                                RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                            )
                    )
                }
            }
        } else {
            // Static minimal bars
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(18) { i ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(0.15f)
                            .background(WTDim, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun WTUnitList(
    peers:         List<String>,
    peerNicknames: Map<String, String>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WTSurface)
            .border(BorderStroke(0.5.dp, WTDim))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = "CONNECTED UNITS",
            color = WTAmber.copy(alpha = 0.8f),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (peers.isEmpty()) {
            Text(
                text = "NO UNITS IN RANGE — SCAN FOR MESH PEERS",
                color = WTTextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 120.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(peers) { peerID ->
                    val nick = peerNicknames[peerID] ?: peerID.take(8)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Active dot
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(WTGreen, CircleShape)
                        )
                        Text(
                            text = "@$nick",
                            color = WTGreen,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "▸ ONLINE",
                            color = WTGreen.copy(alpha = 0.5f),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WTBottomBar(priorityOn: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WTSurface)
            .border(BorderStroke(0.5.dp, WTAmber.copy(alpha = 0.2f)))
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        WTInfoChip(label = "CH", value = "MESH-1")
        WTInfoChip(label = "ENC", value = "NOISE")
        WTInfoChip(label = "SQUELCH", value = "OFF")
        WTInfoChip(label = "MAX TX", value = "10 SEC")
    }
}

@Composable
private fun WTInfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = WTTextMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
        Text(text = value, color = WTAmber, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SignalStrengthBars(level: Int) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..5).forEach { i ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((4 + i * 3).dp)
                    .background(
                        if (i <= level) WTGreen else WTDim,
                        RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp)
                    )
            )
        }
    }
}
