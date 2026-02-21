package com.example.edunet.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

private const val TAG = "SessionDiscovery"
private const val PORT = 9877    // UDP discovery port
private const val BROADCAST_INTERVAL_MS = 3000L

/**
 * Teacher side — broadcasts "{subject_code, url}" every 3 seconds on the LAN.
 * Call [start] in a coroutine; cancel the job to stop.
 */
class SessionBroadcaster(
    private val subjectCode: String,
    private val url: String
) {
    @Volatile private var running = false
    private var socket: DatagramSocket? = null

    fun start() {
        running = true
        Thread {
            try {
                socket = DatagramSocket().apply { broadcast = true }
                val payload = JSONObject().apply {
                    put("subject_code", subjectCode.uppercase())
                    put("url", url)
                }.toString().toByteArray()
                val broadcast = InetAddress.getByName("255.255.255.255")
                while (running) {
                    try {
                        val pkt = DatagramPacket(payload, payload.size, broadcast, PORT)
                        socket?.send(pkt)
                        Log.d(TAG, "Broadcast → $subjectCode @ $url")
                    } catch (e: Exception) {
                        Log.e(TAG, "Broadcast error: ${e.message}")
                    }
                    Thread.sleep(BROADCAST_INTERVAL_MS)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Broadcaster crashed: ${e.message}")
            } finally {
                socket?.close()
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running = false
        socket?.close()
    }
}

/**
 * Student side — listens for UDP broadcasts matching [subjectCode].
 * Returns the teacher's base URL when found, or null on timeout.
 */
suspend fun discoverSession(subjectCode: String, timeoutMs: Int = 15_000): String? =
    withContext(Dispatchers.IO) {
        try {
            val socket = DatagramSocket(PORT).apply {
                soTimeout = timeoutMs
                broadcast = true
            }
            val buf = ByteArray(512)
            val pkt = DatagramPacket(buf, buf.size)
            val deadline = System.currentTimeMillis() + timeoutMs
            socket.use {
                while (System.currentTimeMillis() < deadline) {
                    try {
                        it.receive(pkt)
                        val msg = String(pkt.data, 0, pkt.length)
                        val json = JSONObject(msg)
                        if (json.optString("subject_code").equals(subjectCode, ignoreCase = true)) {
                            val url = json.optString("url")
                            Log.d(TAG, "Discovered session for $subjectCode at $url")
                            return@withContext url
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Discovery error: ${e.message}")
        }
        null
    }
