package com.bitchat.android.model

/**
 * Improvement 6 — Offline Radar Map
 * Represents a single peer displayed as a dot on the radar screen.
 *
 * @param id           Peer ID (used for keying)
 * @param nickname     Display nickname
 * @param distanceRatio 0.0 = at centre (strongest signal), 1.0 = at edge (weakest)
 * @param angle        Angle in radians — deterministically derived from peerID hash
 * @param rssi         Raw BLE RSSI value (e.g. -65 dBm)
 * @param isDirect     True if we have a direct BLE link (not relayed through mesh hops)
 * @param status       Current connectivity status
 */
data class PeerDot(
    val id: String,
    val nickname: String,
    val distanceRatio: Float,   // 0.0 (centre) to 1.0 (edge)
    val angle: Float,           // Radians, 0 to 2π
    val rssi: Int,
    val isDirect: Boolean,
    val status: PeerStatus
)

enum class PeerStatus { DIRECT, MESH, STALE }
