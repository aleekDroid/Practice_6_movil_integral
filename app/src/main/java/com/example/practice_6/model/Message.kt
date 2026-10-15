package com.example.practice_6.model

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Modelo de datos que representa un mensaje individual en la conversación.
 *
 * Incluye utilidades para la serialización y deserialización a formato JSON
 * requeridas para el protocolo de red TCP de MobileConnect.
 *
 * @property id Identificador único para evitar duplicados de entrega.
 * @property text Contenido textual del mensaje (máximo 200 caracteres).
 * @property timestamp Hora de envío en formato legible HH:mm.
 * @property senderRole Rol del emisor en el momento de enviar (Dispositivo A o B).
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val timestamp: String = getCurrentFormattedTime(),
    val senderRole: Role
) {
    /**
     * Convierte el mensaje a un objeto [JSONObject] para su transmisión por socket.
     */
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("type", "MESSAGE")
            put("id", id)
            put("text", text)
            put("timestamp", timestamp)
            put("senderRole", senderRole.name)
        }
    }

    companion object {
        /**
         * Obtiene la hora actual del sistema en formato "HH:mm".
         */
        fun getCurrentFormattedTime(): String {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return sdf.format(Date())
        }

        /**
         * Reconstruye un objeto [Message] a partir de un [JSONObject] recibido por red.
         *
         * @param json Objeto JSON recibido por el socket TCP.
         * @return Instancia de [Message] parseada.
         */
        fun fromJsonObject(json: JSONObject): Message {
            return Message(
                id = json.optString("id", UUID.randomUUID().toString()),
                text = json.optString("text", ""),
                timestamp = json.optString("timestamp", getCurrentFormattedTime()),
                senderRole = Role.fromString(json.optString("senderRole", "NONE"))
            )
        }
    }
}
