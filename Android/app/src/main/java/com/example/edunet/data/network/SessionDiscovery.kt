package com.example.edunet.data.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

private const val TAG        = "SessionDiscovery"
private const val UDP_PORT   = 9877
private const val HTTP_PORT  = 8080

// Common Android / Windows hotspot gateway IPs to probe directly
private val KNOWN_HOTSPOT_IPS = listOf(
    "192.168.43.1",   // Android hotspot default gateway
    "192.168.137.1",  // Windows hotspot default gateway
    "192.168.1.1",    // router
    "192.168.0.1",    // router alt
    "10.0.0.1",       // some Android hotspots
    "192.168.49.1",   // Wi-Fi Direct
)

// ─── Broadcaster (Teacher side) ───────────────────────────────────────────────

/**
 * Broadcasts "{subject_code, url}" over UDP on every discovered broadcast address
 * every 3 seconds so students can discover the session even without an exact IP.
 */
class SessionBroadcaster(
    context: Context,
    private val subjectCode: String,
    private val url: String
) {
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    @Volatile private var running = false
    private val sockets = mutableListOf<DatagramSocket>()

    private fun broadcastAddresses(): List<InetAddress> {
        val addrs = mutableListOf<InetAddress>()

        // 1. Subnet broadcast from WifiManager DHCP info
        try {
            val dhcp = wifiManager.dhcpInfo
            val host = dhcp.ipAddress
            val mask = dhcp.netmask
            if (host != 0) {
                val bcast = (host and mask) or mask.inv()
                val b = byteArrayOf(
                    (bcast and 0xFF).toByte(),
                    ((bcast shr 8) and 0xFF).toByte(),
                    ((bcast shr 16) and 0xFF).toByte(),
                    ((bcast shr 24) and 0xFF).toByte()
                )
                addrs.add(InetAddress.getByAddress(b))
            }
        } catch (e: Exception) {
            Log.w(TAG, "DHCP broadcast addr failed: ${e.message}")
        }

        // 2. Always include global broadcast
        addrs.add(InetAddress.getByName("255.255.255.255"))

        // 3. Common subnet broadcasts
        addrs.addAll(listOf(
            InetAddress.getByName("192.168.43.255"),
            InetAddress.getByName("192.168.137.255"),
            InetAddress.getByName("192.168.1.255"),
            InetAddress.getByName("192.168.0.255"),
        ))
        return addrs.distinct()
    }

    fun start() {
        running = true
        Thread {
            val payload = JSONObject().apply {
                put("subject_code", subjectCode.uppercase())
                put("url", url)
            }.toString().toByteArray()

            val targets = try { broadcastAddresses() } catch (e: Exception) {
                listOf(InetAddress.getByName("255.255.255.255"))
            }
            Log.d(TAG, "Broadcasting to: ${targets.map { it.hostAddress }}")

            while (running) {
                for (target in targets) {
                    try {
                        val sock = DatagramSocket().apply { broadcast = true }
                        val pkt  = DatagramPacket(payload, payload.size, target, UDP_PORT)
                        sock.send(pkt)
                        sock.close()
                        Log.v(TAG, "Sent → ${target.hostAddress}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Send to ${target.hostAddress} failed: ${e.message}")
                    }
                }
                try { Thread.sleep(3000) } catch (_: InterruptedException) { break }
            }
            Log.d(TAG, "Broadcaster stopped")
        }.also { it.isDaemon = true; it.start() }
    }

    fun stop() { running = false }
}

// ─── Discovery (Student side) ─────────────────────────────────────────────────

/**
 * Tries to discover the teacher's session in parallel:
 *  1. Listens for UDP broadcasts (works when teacher is nearby on same network).
 *  2. Directly probes a list of known hotspot gateway IPs on port 8080.
 *
 * Returns the first matching base URL or null if none found within [timeoutMs].
 */
suspend fun discoverSession(subjectCode: String, timeoutMs: Int = 15_000): String? =
    withTimeoutOrNull(timeoutMs.toLong()) {
        coroutineScope {
            val code = subjectCode.uppercase()
            Log.d(TAG, "Starting discovery for $code (timeout=${timeoutMs}ms)")

            // Job 1 — UDP listener
            val udpJob = async(Dispatchers.IO) { listenUdp(code, timeoutMs) }

            // Job 2 — Direct HTTP probes (runs in parallel)
            val probeJob = async(Dispatchers.IO) { probeKnownHosts(code) }

            // Return whichever finds first
            val results = awaitAll(udpJob, probeJob)
            results.firstOrNull { it != null }
        }
    }

// UDP listen loop — returns URL or null on timeout
private fun listenUdp(code: String, timeoutMs: Int): String? {
    return try {
        val socket = DatagramSocket(UDP_PORT).apply {
            soTimeout = timeoutMs
            broadcast = true
            reuseAddress = true
        }
        val buf = ByteArray(512)
        val pkt = DatagramPacket(buf, buf.size)
        val deadline = System.currentTimeMillis() + timeoutMs
        socket.use {
            while (System.currentTimeMillis() < deadline) {
                try {
                    it.receive(pkt)
                    val msg  = String(pkt.data, 0, pkt.length)
                    val json = JSONObject(msg)
                    if (json.optString("subject_code").equals(code, ignoreCase = true)) {
                        val url = json.optString("url")
                        Log.d(TAG, "UDP discovered $code at $url")
                        return url
                    }
                } catch (_: java.net.SocketTimeoutException) { break }
            }
        }
        null
    } catch (e: Exception) {
        Log.w(TAG, "UDP listen error: ${e.message}")
        null
    }
}

// Direct HTTP probe — checks known hotspot IPs for a matching session
private fun probeKnownHosts(code: String): String? {
    for (ip in KNOWN_HOTSPOT_IPS) {
        val base = "http://$ip:$HTTP_PORT"
        try {
            val conn = URL("$base/info").openConnection() as HttpURLConnection
            conn.connectTimeout = 1500
            conn.readTimeout    = 1500
            conn.requestMethod  = "GET"
            val resp = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val json = JSONObject(resp)
            if (json.optString("subject_code").equals(code, ignoreCase = true)) {
                Log.d(TAG, "Direct probe found $code at $base")
                return base
            }
        } catch (_: Exception) {}
    }
    return null
}
