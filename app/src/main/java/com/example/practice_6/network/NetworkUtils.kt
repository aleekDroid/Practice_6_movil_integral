package com.example.practice_6.network

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * Resultado de la validación de una dirección IP remota.
 */
data class IpValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

/**
 * Resultado de la validación del puerto TCP opcional.
 */
data class PortValidationResult(
    val isValid: Boolean,
    val port: Int = 8888,
    val errorMessage: String? = null
)

/**
 * Utilidades de red mejoradas para detección de IP propia (incluyendo Hotspot) y validación.
 */
object NetworkUtils {

    /**
     * Detecta la IP propia del dispositivo priorizando redes Wi-Fi y Hotspot/Punto de acceso.
     */
    fun getLocalIpAddress(context: Context): String {
        try {
            // Intentar primero con ConnectivityManager y LinkProperties
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val activeNetwork = cm.activeNetwork
                if (activeNetwork != null) {
                    val linkProps = cm.getLinkProperties(activeNetwork)
                    if (linkProps != null) {
                        for (linkAddress in linkProps.linkAddresses) {
                            val addr = linkAddress.address
                            if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                                val host = addr.hostAddress
                                if (!host.isNullOrEmpty() && host != "127.0.0.1") {
                                    return host
                                }
                            }
                        }
                    }
                }
            }

            // Recorrido de interfaces reservadas de respaldo (priorizando interfaces de hotspot)
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            var candidateIp: String? = null

            for (networkInterface in interfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                val name = networkInterface.name.lowercase()

                // Omitir VPN y datos móviles a menos que no haya otra opción
                val isVpnOrCellular = name.startsWith("tun") || name.startsWith("rmnet") || name.startsWith("ppp")

                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address && !address.isLinkLocalAddress) {
                        val hostAddress = address.hostAddress ?: continue
                        if (hostAddress == "127.0.0.1" || hostAddress.startsWith("169.254.")) continue

                        // Si es interfaz de hotspot (ap0, swlan, wlan), retornar inmediatamente
                        if (name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("wlan")) {
                            return hostAddress
                        }

                        if (!isVpnOrCellular && candidateIp == null) {
                            candidateIp = hostAddress
                        } else if (candidateIp == null) {
                            candidateIp = hostAddress
                        }
                    }
                }
            }

            if (!candidateIp.isNullOrEmpty()) {
                return candidateIp
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "Sin conexión"
    }

    /**
     * Valida si una cadena es una dirección IPv4 sintácticamente correcta y válida.
     */
    fun validateIpAddress(remoteIp: String, localIp: String): IpValidationResult {
        val trimmed = remoteIp.trim()

        if (trimmed.isEmpty()) {
            return IpValidationResult(false, "Ingresa la IP del otro dispositivo.")
        }

        val parts = trimmed.split(".")
        if (parts.size != 4) {
            return IpValidationResult(false, "La IP debe tener exactamente 4 octetos (ej: 192.168.1.10).")
        }

        for (part in parts) {
            if (part.isEmpty()) {
                return IpValidationResult(false, "No se permiten octetos vacíos.")
            }
            val num = part.toIntOrNull()
            if (num == null) {
                return IpValidationResult(false, "La IP solo debe contener números y puntos.")
            }
            if (num < 0 || num > 255) {
                return IpValidationResult(false, "Cada octeto debe estar entre 0 y 255 (octeto inválido: $part).")
            }
            if (part.length > 1 && part.startsWith("0")) {
                return IpValidationResult(false, "Los octetos no deben comenzar con cero a la izquierda.")
            }
        }

        if (trimmed == "0.0.0.0" || trimmed.startsWith("127.")) {
            return IpValidationResult(false, "No puedes usar la dirección 0.0.0.0 ni de loopback 127.x.x.x.")
        }

        if (trimmed == localIp && localIp != "Sin conexión") {
            return IpValidationResult(false, "No puedes conectar a tu propia IP local.")
        }

        return IpValidationResult(true, null)
    }

    /**
     * Valida el campo opcional del puerto TCP (vacío = 8888, entero entre 1024 y 65535).
     */
    fun validatePort(portStr: String): PortValidationResult {
        val trimmed = portStr.trim()
        if (trimmed.isEmpty()) {
            return PortValidationResult(true, 8888)
        }

        val parsedPort = trimmed.toIntOrNull()
        if (parsedPort == null) {
            return PortValidationResult(false, 8888, "El puerto debe ser un número entero válido.")
        }

        if (parsedPort !in 1024..65535) {
            return PortValidationResult(false, 8888, "El puerto debe estar en el rango de 1024 a 65535.")
        }

        return PortValidationResult(true, parsedPort, null)
    }
}
