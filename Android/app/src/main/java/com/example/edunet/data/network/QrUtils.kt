package com.example.edunet.data.network

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.net.NetworkInterface

object QrUtils {

    /** Returns the device's WiFi/hotspot IPv4 address, or null if not connected */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (addr.isLoopbackAddress) continue
                    val hostAddr = addr.hostAddress ?: continue
                    // IPv4 only, skip link-local (169.x)
                    if (!hostAddr.contains(':') && !hostAddr.startsWith("169.")) {
                        return hostAddr
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /** Generate a QR code Bitmap from the given text */
    fun generateQrBitmap(text: String, sizePx: Int = 512): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    fun sessionUrl(port: Int = 8080): String? {
        val ip = getLocalIpAddress() ?: return null
        return "http://$ip:$port"
    }
}
