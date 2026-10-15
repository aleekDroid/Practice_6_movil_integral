package com.example.practice_6.model

/**
 * Enumeración que representa el rol asignado a cada dispositivo en la comunicación.
 *
 * Los roles NO son seleccionados manualmente por el usuario; la aplicación los asigna
 * de forma automática según la interacción (el primer emisor es A, el receptor es B).
 */
enum class Role {
    /**
     * Rol no definido aún. Ocurre al iniciar la app por primera vez o cuando
     * se ha limpiado el historial de conversación en ambos dispositivos.
     */
    NONE,

    /**
     * Dispositivo A: Asignado automáticamente al dispositivo que envía el
     * primer mensaje en una sesión sin roles definidos.
     */
    A,

    /**
     * Dispositivo B: Asignado automáticamente al dispositivo que recibe el
     * primer mensaje o cuando el otro dispositivo ya tiene el rol A.
     */
    B;

    /**
     * Retorna el texto legible para mostrar en la interfaz de usuario.
     */
    fun displayName(): String {
        return when (this) {
            NONE -> "Por definir (el primero en enviar un mensaje será A)"
            A -> "Eres el Dispositivo A"
            B -> "Eres el Dispositivo B"
        }
    }

    /**
     * Retorna el nombre corto del rol para los mensajes de chat ("Dispositivo A" o "Dispositivo B").
     */
    fun shortName(): String {
        return when (this) {
            NONE -> "Por definir"
            A -> "Dispositivo A"
            B -> "Dispositivo B"
        }
    }

    /**
     * Devuelve el rol complementario opuesto.
     * Si este es A, el opuesto es B. Si es B, el opuesto es A.
     */
    fun opposite(): Role {
        return when (this) {
            A -> B
            B -> A
            NONE -> NONE
        }
    }

    companion object {
        /**
         * Convierte una cadena de texto al tipo [Role] correspondiente.
         *
         * @param name Nombre del rol ("A", "B" o cualquier otro valor para NONE).
         * @return Objeto [Role] correspondiente.
         */
        fun fromString(name: String?): Role {
            return when (name?.uppercase()) {
                "A" -> A
                "B" -> B
                else -> NONE
            }
        }
    }
}
