package com.bitchat.android.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bitchat.android.model.PeerDot
import com.bitchat.android.model.PeerStatus
import kotlin.math.*

// ── Radar colour palette ─────────────────────────────────────────────────────
private val RadarBg       = Color(0xFF040D06)   // deep night-green background
private val RadarGreen    = Color(0xFF00FF41)   // classic military radar green
private val RadarDim      = Color(0xFF0A3B12)   // dim ring lines
private val RadarSweep    = Color(0xFF00FF41)   // sweep line colour
private val RadarAmber    = Color(0xFFFFAA00)   // mesh-hop peer dot
private val RadarHeader   = Color(0xFF141F15)   // header bar background
private val RadarTextMuted = Color(0xFF447744)

/**
 * Improvement 6 — Offline Radar Map with Peer Dots
 *
 * Draws a full-screen classic radar (CRT green-on-black) that shows
 * every detected BLE peer as a dot, sized and coloured by signal strength.
 *
 * Data sources already in the app (zero new permissions needed):
 *  - peerRSSI:      Map<peerID, Int>   — live BLE signal strength
 *  - peerNicknames: Map<peerID, String> — nicknames for labels
 *  - peerDirect:    Map<peerID, Bool>   — direct vs relayed
 *  - connectedPeers: List<String>       — all known peers
 *
 * Position mapping:
 *  - RSSI -30 dBm → distance ratio 0.05 (almost at centre, very close)
 *  - RSSI -100 dBm → distance ratio 0.95 (near edge, far away)
 *  - Angle : deterministic from peer ID hash → stable, no jitter
 *
 * Shown as a Dialog overlay so no navigation stack change is needed.
 */
@Composable
fun RadarMapScreen(
    viewModel: ChatViewModel,
    onClose: () -> Unit
) {
    val connectedPeers  by viewModel.connectedPeers.observeAsState(emptyList())
    val peerNicknames   by viewModel.peerNicknames.observeAsState(emptyMap())
    val peerRSSI        by viewModel.peerRSSI.observeAsState(emptyMap())
    val peerDirect      by viewModel.peerDirect.observeAsState(emptyMap())

    // Build PeerDot list from live data
    val peerDots = remember(connectedPeers, peerNicknames, peerRSSI, peerDirect) {
        buildPeerDots(connectedPeers, peerNicknames, peerRSSI, peerDirect)
    }

    // Sweeping radar arm animation — full 360° every 3 seconds
    val infiniteTransition = rememberInfiniteTransition(label = "radar_sweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    // Blip fade — dots "light up" as the sweep passes over them
    val blipAlphaMap = remember(sweepAngle, peerDots) {
        buildBlipAlphas(sweepAngle, peerDots)
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(RadarBg)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Header ─────────────────────────────────────────────────
                RadarHeader(peerCount = peerDots.size, onClose = onClose)

                // ── Radar canvas ────────────────────────────────────────────
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val maxR = minOf(cx, cy) * 0.95f

                        drawRadarRings(cx, cy, maxR)
                        drawCrosshairs(cx, cy, maxR)
                        drawSweepArm(cx, cy, maxR, sweepAngle)
                        drawPeerDots(peerDots, blipAlphaMap, cx, cy, maxR)
                        drawSelfDot(cx, cy)
                    }
                }

                // ── Legend ──────────────────────────────────────────────────
                RadarLegend(peerDots = peerDots)
            }
        }
    }
}

// ── Canvas drawing functions ─────────────────────────────────────────────────

private fun DrawScope.drawRadarRings(cx: Float, cy: Float, maxR: Float) {
    // 3 concentric rings at 33%, 66%, 100%
    val ringAlphas = listOf(0.25f, 0.35f, 0.5f)
    listOf(0.33f, 0.66f, 1.0f).forEachIndexed { i, ratio ->
        drawCircle(
            color  = RadarDim.copy(alpha = ringAlphas[i]),
            radius = maxR * ratio,
            center = Offset(cx, cy),
            style  = Stroke(width = if (i == 2) 1.5f else 1f)
        )
    }

    // Distance labels on outer ring
    // (text drawn in Compose below; Canvas text is API 34+ only)
}

private fun DrawScope.drawCrosshairs(cx: Float, cy: Float, maxR: Float) {
    val color = RadarDim.copy(alpha = 0.4f)
    // Horizontal
    drawLine(color, Offset(cx - maxR, cy), Offset(cx + maxR, cy), strokeWidth = 1f)
    // Vertical
    drawLine(color, Offset(cx, cy - maxR), Offset(cx, cy + maxR), strokeWidth = 1f)
    // 45° diagonals
    val d = maxR * 0.707f
    drawLine(color, Offset(cx - d, cy - d), Offset(cx + d, cy + d), strokeWidth = 0.5f)
    drawLine(color, Offset(cx + d, cy - d), Offset(cx - d, cy + d), strokeWidth = 0.5f)
}

private fun DrawScope.drawSweepArm(cx: Float, cy: Float, maxR: Float, angleDeg: Float) {
    val rad = Math.toRadians(angleDeg.toDouble())
    val endX = cx + (maxR * cos(rad)).toFloat()
    val endY = cy + (maxR * sin(rad)).toFloat()

    // Main sweep line
    drawLine(
        color       = RadarSweep.copy(alpha = 0.9f),
        start       = Offset(cx, cy),
        end         = Offset(endX, endY),
        strokeWidth = 2f
    )

    // Trailing glow — 4 ghost lines with decreasing alpha
    (1..4).forEach { trail ->
        val trailAngle = Math.toRadians((angleDeg - trail * 15.0))
        val tx = cx + (maxR * cos(trailAngle)).toFloat()
        val ty = cy + (maxR * sin(trailAngle)).toFloat()
        drawLine(
            color       = RadarSweep.copy(alpha = 0.18f / trail),
            start       = Offset(cx, cy),
            end         = Offset(tx, ty),
            strokeWidth = 2f
        )
    }
}

private fun DrawScope.drawPeerDots(
    peers: List<PeerDot>,
    blipAlpha: Map<String, Float>,
    cx: Float, cy: Float, maxR: Float
) {
    peers.forEach { peer ->
        val rad   = peer.angle.toDouble()
        val r     = peer.distanceRatio * maxR
        val px    = cx + (r * cos(rad)).toFloat()
        val py    = cy + (r * sin(rad)).toFloat()

        // Dot size based on signal strength: -30 → 14dp, -100 → 6dp
        val dotRadius = lerp(14f, 6f, peer.distanceRatio)

        val alpha = blipAlpha[peer.id] ?: 0.3f

        // Colour: green = direct, amber = mesh hop
        val dotColor = when (peer.status) {
            PeerStatus.DIRECT -> RadarGreen
            PeerStatus.MESH   -> RadarAmber
            PeerStatus.STALE  -> Color(0xFF666644)
        }

        // Outer glow ring
        drawCircle(
            color  = dotColor.copy(alpha = alpha * 0.35f),
            radius = dotRadius * 2.2f,
            center = Offset(px, py)
        )
        // Core dot
        drawCircle(
            color  = dotColor.copy(alpha = alpha),
            radius = dotRadius,
            center = Offset(px, py)
        )
        // Bright inner highlight
        drawCircle(
            color  = Color.White.copy(alpha = alpha * 0.5f),
            radius = dotRadius * 0.35f,
            center = Offset(px - dotRadius * 0.2f, py - dotRadius * 0.2f)
        )
    }
}

private fun DrawScope.drawSelfDot(cx: Float, cy: Float) {
    // YOU — always at centre, pulsing white
    drawCircle(color = Color.White.copy(alpha = 0.18f), radius = 22f, center = Offset(cx, cy))
    drawCircle(color = Color.White, radius = 8f, center = Offset(cx, cy))
    drawCircle(color = Color(0xFF00FF41), radius = 4f, center = Offset(cx, cy))
}

// ── Sweep-based blip alpha (dots brighten as sweep passes over) ──────────────

private fun buildBlipAlphas(sweepDeg: Float, peers: List<PeerDot>): Map<String, Float> {
    return peers.associate { peer ->
        val peerDegrees = Math.toDegrees(peer.angle.toDouble()).toFloat()
        val diff = ((sweepDeg - peerDegrees + 360f) % 360f)
        // Bright for 0-20° after sweep passes, fades over next 100°
        val alpha = when {
            diff < 20f  -> 1.0f
            diff < 120f -> 1.0f - ((diff - 20f) / 100f) * 0.7f   // fade from 1.0 → 0.3
            else        -> 0.3f
        }
        peer.id to alpha
    }
}

// ── PeerDot builder ──────────────────────────────────────────────────────────

private fun buildPeerDots(
    connectedPeers: List<String>,
    peerNicknames:  Map<String, String>,
    peerRSSI:       Map<String, Int>,
    peerDirect:     Map<String, Boolean>
): List<PeerDot> {
    return connectedPeers.map { id ->
        val nick   = peerNicknames[id] ?: id.take(8)
        val rssi   = peerRSSI[id] ?: -80
        val direct = peerDirect[id] ?: false

        // Distance ratio: -30 → 0.05 (very close), -100 → 0.92 (far)
        val distRatio = rssiToDistanceRatio(rssi)

        // Deterministic angle from peer ID hash (stable, no jitter)
        val angleRad = deterministicAngle(id)

        val status = when {
            direct              -> PeerStatus.DIRECT
            rssi > -90          -> PeerStatus.MESH
            else                -> PeerStatus.STALE
        }

        PeerDot(
            id            = id,
            nickname      = nick,
            distanceRatio = distRatio,
            angle         = angleRad,
            rssi          = rssi,
            isDirect      = direct,
            status        = status
        )
    }
}

/** Maps RSSI to a [0.05, 0.92] distance ratio for radar placement */
private fun rssiToDistanceRatio(rssi: Int): Float {
    // Clamp to realistic BLE range
    val clamped = rssi.coerceIn(-100, -30)
    // Linear map: -30 → 0.05, -100 → 0.92
    val normalized = (clamped + 100) / 70f   // 0.0 (far) .. 1.0 (close)
    return lerp(0.92f, 0.05f, normalized)
}

/** Stable angle derived from peer ID hashCode — same peer always at same angle */
private fun deterministicAngle(peerID: String): Float {
    val hash = peerID.hashCode().toLong() and 0xFFFFFFFFL
    return ((hash % 360) * PI / 180.0).toFloat()
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

// ── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun RadarHeader(peerCount: Int, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RadarHeader)
            .border(androidx.compose.foundation.BorderStroke(0.5.dp, RadarGreen.copy(alpha = 0.3f)))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Close, "Close", tint = RadarGreen, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "◎ PROXIMITY RADAR",
                color = RadarGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            Text(
                text = "BLE MESH · OFFLINE · NO GPS NEEDED",
                color = RadarGreen.copy(alpha = 0.5f),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
        }
        // Peer count chip
        Box(
            modifier = Modifier
                .background(RadarGreen.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                .border(0.5.dp, RadarGreen.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "$peerCount PEER${if (peerCount != 1) "S" else ""}",
                color = RadarGreen,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RadarLegend(peerDots: List<PeerDot>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RadarHeader)
            .border(androidx.compose.foundation.BorderStroke(0.5.dp, RadarGreen.copy(alpha = 0.2f)))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Legend row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendDot(color = Color.White, label = "YOU (centre)")
            LegendDot(color = RadarGreen, label = "DIRECT link")
            LegendDot(color = RadarAmber, label = "MESH hop")
            LegendDot(color = Color(0xFF666644), label = "WEAK signal")
        }

        if (peerDots.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            // Top 3 nearest peers by rssi
            Text(
                text = "NEAREST UNITS",
                color = RadarTextMuted,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            peerDots.sortedBy { it.rssi }.reversed().take(4).forEach { peer ->
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(
                                when (peer.status) {
                                    PeerStatus.DIRECT -> RadarGreen
                                    PeerStatus.MESH   -> RadarAmber
                                    PeerStatus.STALE  -> Color(0xFF666644)
                                },
                                CircleShape
                            )
                    )
                    Text(
                        text = "@${peer.nickname}",
                        color = RadarGreen,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${peer.rssi} dBm",
                        color = RadarTextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = when (peer.status) {
                            PeerStatus.DIRECT -> "▸ DIRECT"
                            PeerStatus.MESH   -> "▸ MESH"
                            PeerStatus.STALE  -> "▸ WEAK"
                        },
                        color = when (peer.status) {
                            PeerStatus.DIRECT -> RadarGreen.copy(alpha = 0.7f)
                            PeerStatus.MESH   -> RadarAmber.copy(alpha = 0.7f)
                            PeerStatus.STALE  -> Color(0xFF666644)
                        },
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "NO PEERS DETECTED — SCANNING MESH...",
                color = RadarTextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(text = label, color = RadarTextMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
    }
}
