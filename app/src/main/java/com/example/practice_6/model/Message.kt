package com.example.practice_6.model

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Estado de entrega del mensaje dentro del protocolo con confirmación (ACK).
 */
enum class MessageStatus {
    /** Pendiente de envío o esperando confirmación ACK. */
    PENDING,

    /** Confirmado exitosamente por el receptor mediante ACK. */
    SENT,

    /** Falló la transmisión por el socket TCP o expiró el timeout sin ACK. */
    FAILED
}

/**
 * Modelo de datos que representa un mensaje individual en la conversación.
 *
 * @property id Identificador único para confirmación ACK y deduplicación.
 * @property text Contenido del mensaje.
 * @property timestamp Hora de envío en formato HH:mm.
 * @property senderRole Rol del emisor en el momento del envío (Dispositivo A o B).
 * @property status Estado actual de confirmación de entrega (PENDING, SENT, FAILED).
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val timestamp: String = getCurrentFormattedTime(),
    val senderRole: Role,
    val status: MessageStatus = MessageStatus.SENT
) {
    /**
     * Serializa el mensaje a un objeto [JSONObject] para su transmisión TCP.
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
         * Obtiene la hora actual en formato "HH:mm".
         */
        fun getCurrentFormattedTime(): String {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return sdf.format(Date())
        }

        /**
         * Deserializa un mensaje desde un [JSONObject] recibido.
         */
        fun fromJsonObject(json: JSONObject): Message {
            return Message(
                id = json.optString("id", UUID.randomUUID().toString()),
                text = json.optString("text", ""),
                timestamp = json.optString("timestamp", getCurrentFormattedTime()),
                senderRole = Role.fromString(json.optString("senderRole", "NONE")),
                status = MessageStatus.SENT
            )
        }
    }
}
