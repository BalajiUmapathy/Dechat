package com.bitchat.android.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Improvement 5 — Push-to-Talk (PTT) Walkie-Talkie Button
 *
 * Hold to record → release to send.
 * Shows animated pulsing ring while transmitting.
 * Placed in the input bar next to SOS button when the text field is empty.
 */
@Composable
fun PTTButton(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val isTransmitting by viewModel.isPTTTransmitting.observeAsState(false)

    // Pulse animation while active
    val infiniteTransition = rememberInfiniteTransition(label = "ptt_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val scale = if (isTransmitting) pulseScale else 1f
    val bgColor = if (isTransmitting) Color(0xFFCC0000) else Color(0xFFFF6700)  // Red when live, orange idle

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .scale(scale)
                .background(bgColor, CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            viewModel.startPTT()       // Hold: begin recording
                            tryAwaitRelease()
                            viewModel.stopPTT()        // Release: encode + send
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isTransmitting) Icons.Filled.Mic else Icons.Filled.MicNone,
                contentDescription = if (isTransmitting) "Transmitting PTT" else "Push to Talk",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        // Label underneath
        Text(
            text = if (isTransmitting) "LIVE" else "PTT",
            color = if (isTransmitting) Color(0xFFCC0000) else Color(0xFFFF6700),
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
