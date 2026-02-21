package com.example.edunet.data.network

import android.util.Log
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

private const val TAG      = "PeerSync"
private const val PEER_UDP = 9879   // separate port from session discovery

data class PeerRequest(val subjectCode: String, val missingDates: List<String>, val senderIp: String)
data class PeerOffer(val subjectCode: String, val availableDates: List<String>, val url: String)

// ── Requester broadcasts ──────────────────────────────────────────────────────

class PeerRequestBroadcaster(private val subjectCode: String, private val missingDates: List<String>) {
    @Volatile private var running = false

    fun start() {
        running = true
        Thread {
            val payload = JSONObject().apply {
                put("type", "REQUEST")
                put("subject_code", subjectCode)
                put("missing_dates", missingDates.joinToString(","))
            }.toString().toByteArray()
            while (running) {
                listOf("255.255.255.255", "192.168.43.255", "192.168.137.255").forEach { addr ->
                    try {
                        DatagramSocket().apply { broadcast = true }.use { sock ->
                            sock.send(DatagramPacket(payload, payload.size, InetAddress.getByName(addr), PEER_UDP))
                        }
                    } catch (_: Exception) {}
                }
                try { Thread.sleep(2000) } catch (_: InterruptedException) { break }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun stop() { running = false }
}

// ── Offerer broadcasts (after accepting) ─────────────────────────────────────

class PeerOfferBroadcaster(
    private val subjectCode: String,
    private val availableDates: List<String>,
    private val url: String
) {
    @Volatile private var running = false

    fun start() {
        running = true
        Thread {
            val payload = JSONObject().apply {
                put("type", "OFFER")
                put("subject_code", subjectCode)
                put("available_dates", availableDates.joinToString(","))
                put("url", url)
            }.toString().toByteArray()
            while (running) {
                listOf("255.255.255.255", "192.168.43.255", "192.168.137.255").forEach { addr ->
                    try {
                        DatagramSocket().apply { broadcast = true }.use { sock ->
                            sock.send(DatagramPacket(payload, payload.size, InetAddress.getByName(addr), PEER_UDP))
                        }
                    } catch (_: Exception) {}
                }
                try { Thread.sleep(2000) } catch (_: InterruptedException) { break }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun stop() { running = false }
}

// ── Listener (either side) ────────────────────────────────────────────────────

/**
 * Listens for a single OFFER matching [subjectCode]. Returns PeerOffer or null on timeout.
 * Call from a coroutine (already on IO dispatcher).
 */
fun listenForOffer(subjectCode: String, timeoutMs: Int = 30_000): PeerOffer? {
    return try {
        DatagramSocket(PEER_UDP).apply {
            soTimeout = timeoutMs; broadcast = true; reuseAddress = true
        }.use { sock ->
            val buf = ByteArray(1024)
            val pkt = DatagramPacket(buf, buf.size)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                try {
                    sock.receive(pkt)
                    val json = JSONObject(String(pkt.data, 0, pkt.length))
                    if (json.optString("type") == "OFFER" &&
                        json.optString("subject_code").equals(subjectCode, ignoreCase = true)
                    ) {
                        return PeerOffer(
                            subjectCode    = subjectCode,
                            availableDates = json.optString("available_dates").split(",").filter { it.isNotBlank() },
                            url            = json.optString("url")
                        )
                    }
                } catch (_: java.net.SocketTimeoutException) { break }
            }
            null
        }
    } catch (e: Exception) { Log.w(TAG, "listenForOffer: ${e.message}"); null }
}

/**
 * Listens for a REQUEST on the network. Returns first matching PeerRequest or null on timeout.
 * Used by the offerer side.
 */
fun listenForRequest(subjectCodes: Set<String>, timeoutMs: Int = 60_000): PeerRequest? {
    return try {
        DatagramSocket(PEER_UDP).apply {
            soTimeout = timeoutMs; broadcast = true; reuseAddress = true
        }.use { sock ->
            val buf = ByteArray(1024)
            val pkt = DatagramPacket(buf, buf.size)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                try {
                    sock.receive(pkt)
                    val json = JSONObject(String(pkt.data, 0, pkt.length))
                    if (json.optString("type") == "REQUEST") {
                        val code = json.optString("subject_code")
                        if (subjectCodes.any { it.equals(code, ignoreCase = true) }) {
                            return PeerRequest(
                                subjectCode  = code,
                                missingDates = json.optString("missing_dates").split(",").filter { it.isNotBlank() },
                                senderIp     = pkt.address.hostAddress ?: ""
                            )
                        }
                    }
                } catch (_: java.net.SocketTimeoutException) { break }
            }
            null
        }
    } catch (e: Exception) { Log.w(TAG, "listenForRequest: ${e.message}"); null }
}
