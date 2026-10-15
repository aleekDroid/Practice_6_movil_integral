package com.example.practice_6.network

import android.content.Context
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * Resultado de la validación de una dirección IP ingresada por el usuario.
 *
 * @property isValid `true` si la IP cumple con todos los criterios de formato y red.
 * @property errorMessage Mensaje explicativo en español en caso de que la IP no sea válida.
 */
data class IpValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

/**
 * Utilidades de red para la detección de la IP local e inspección de direcciones IPv4.
 */
object NetworkUtils {

    /**
     * Detecta y devuelve la primera dirección IPv4 local activa diferente de loopback (127.0.0.1).
     *
     * @param context Contexto de la aplicación Android.
     * @return Cadena con la dirección IPv4 detectada (ej: "192.168.1.15") o "Sin conexión" si no hay red.
     */
    fun getLocalIpAddress(context: Context): String {
        try {
            // Recorremos las interfaces de red activas del dispositivo
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                // Ignoramos interfaces inactivas o de loopback
                if (!networkInterface.isUp || networkInterface.isLoopback) continue

                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    // Filtrar solo direcciones IPv4 válidas y no de loopback
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val hostAddress = address.hostAddress
                        if (!hostAddress.isNullOrEmpty() && hostAddress != "127.0.0.1") {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "Sin conexión"
    }

    /**
     * Valida si una cadena es una dirección IPv4 sintácticamente correcta y
     * asegura que no coincida con la propia IP local del dispositivo.
     *
     * @param remoteIp Cadena con la IP ingresada por el usuario para el dispositivo remoto.
     * @param localIp Cadena con la IP propia detectada en este dispositivo.
     * @return [IpValidationResult] indicando si es válida y la causa de rechazo si no lo es.
     */
    fun validateIpAddress(remoteIp: String, localIp: String): IpValidationResult {
        val trimmed = remoteIp.trim()

        if (trimmed.isEmpty()) {
            return IpValidationResult(false, "Ingresa la IP del otro dispositivo.")
        }

        // Dividir por puntos y verificar que tenga exactamente 4 octetos
        val parts = trimmed.split(".")
        if (parts.size != 4) {
            return IpValidationResult(false, "La IP debe tener exactamente 4 octetos (ej: 192.168.1.10).")
        }

        // Validar que cada octeto sea un número entre 0 y 255
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
            // Evitar ceros a la izquierda inconsistentes (ej. "01" salvo "0")
            if (part.length > 1 && part.startsWith("0")) {
                return IpValidationResult(false, "Los octetos no deben comenzar con cero a la izquierda.")
            }
        }

        // Verificar que la IP remota no sea igual a la IP propia
        if (trimmed == localIp && localIp != "Sin conexión") {
            return IpValidationResult(false, "No puedes conectar a tu propia IP local.")
        }

        return IpValidationResult(true, null)
    }
}
