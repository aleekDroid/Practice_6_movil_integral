package com.example.practice_6.domain

import com.example.practice_6.model.Message
import com.example.practice_6.model.Role

/**
 * Resultado devuelto al procesar un saludo handshake HELLO de red.
 *
 * @property assignedRole Rol final que debe adoptar el dispositivo local.
 * @property shouldNotifyRemote `true` si se requirió una reasignación por conflicto y se debe notificar al remoto.
 * @property explanation Comentario explicativo de la decisión tomada por la regla de negocio.
 */
data class HandshakeRoleResult(
    val assignedRole: Role,
    val shouldNotifyRemote: Boolean = false,
    val explanation: String
)

/**
 * Clase de lógica pura de dominio encargada de la asignación y gestión automática
 * de los roles (Dispositivo A y Dispositivo B) para la práctica MobileConnect.
 *
 * REGLAS FUNDAMENTALES DE NEGOCIO:
 * 1. El usuario NUNCA selecciona su rol manualmente.
 * 2. Al conectar, NO se asigna rol si ninguno tiene historial ([Role.NONE]).
 * 3. El primer dispositivo en enviar un mensaje en una sesión sin roles se convierte en Dispositivo A.
 *    El receptor se convierte automáticamente en Dispositivo B.
 * 4. Un rol solo se mantiene mientras exista historial guardado. Si se limpia el historial,
 *    el rol vuelve a [Role.NONE].
 * 5. Si solo uno limpia su historial, el otro mantiene su rol. Al reconectar mediante HELLO,
 *    el que limpió adopta automáticamente el rol complementario (el que queda libre).
 * 6. Resolución de conflictos (si ambos reportan el mismo rol por concurrencia):
 *    Se compara el primer mensaje del historial (el de fecha/timestamp más antiguo gana A).
 *    Si hay empate simultáneo, gana A la IP numéricamente menor.
 */
class RoleManager(initialRole: Role = Role.NONE) {

    /** Rol actual del dispositivo local. */
    var currentRole: Role = initialRole
        private set

    /**
     * Actualiza directamente el rol actual del dispositivo local.
     */
    fun setRole(newRole: Role) {
        currentRole = newRole
    }

    /**
     * Determina el rol a usar al enviar el primer mensaje.
     * Si no hay rol asignado ([Role.NONE]), este dispositivo pasa a ser el **Dispositivo A**.
     *
     * @return El [Role] asignado para el envío.
     */
    fun onSendingFirstMessage(): Role {
        if (currentRole == Role.NONE) {
            currentRole = Role.A
        }
        return currentRole
    }

    /**
     * Procesa un mensaje entrante de red y actualiza el rol local si aún no estaba definido.
     *
     * @param incomingSenderRole Rol del emisor del mensaje recibido.
     * @return `true` si el rol local cambió como consecuencia de la recepción.
     */
    fun onReceivingMessage(incomingSenderRole: Role): Boolean {
        if (currentRole == Role.NONE && incomingSenderRole != Role.NONE) {
            currentRole = incomingSenderRole.opposite()
            return true
        }
        return false
    }

    /**
     * Procesa el apretón de manos (handshake `HELLO`) recibido de otro dispositivo
     * y aplica las reglas de asignación y resolución de conflictos.
     *
     * @param remoteRoleStr Rol que reporta el dispositivo remoto ("A", "B", "NONE").
     * @param remoteHasHistory Indicador de si el remoto conserva historial.
     * @param localHasHistory Indicador de si el dispositivo local conserva historial.
     * @return Objeto [HandshakeRoleResult] con el rol asignado y el detalle de la decisión.
     */
    fun processHandshake(
        remoteRoleStr: String,
        remoteHasHistory: Boolean,
        localHasHistory: Boolean
    ): HandshakeRoleResult {
        val remoteRole = Role.fromString(remoteRoleStr)

        // CASO 1: Ninguno tiene historial guardado.
        if (!localHasHistory && !remoteHasHistory) {
            currentRole = Role.NONE
            return HandshakeRoleResult(
                assignedRole = Role.NONE,
                explanation = "Conexión establecida. Rol por definir (el primero en enviar un mensaje será Dispositivo A)."
            )
        }

        // CASO 2: Este dispositivo limpió historial (no tiene historial) pero el remoto sí tiene.
        if (!localHasHistory && remoteRole != Role.NONE) {
            currentRole = remoteRole.opposite()
            return HandshakeRoleResult(
                assignedRole = currentRole,
                explanation = "El remoto conserva historial como ${remoteRole.shortName()}. Se te asigna automáticamente ${currentRole.shortName()}."
            )
        }

        // CASO 3: Este dispositivo conserva historial y el remoto no tiene historial.
        if (localHasHistory && !remoteHasHistory && currentRole != Role.NONE) {
            return HandshakeRoleResult(
                assignedRole = currentRole,
                explanation = "Conservas tu rol ${currentRole.shortName()} previo."
            )
        }

        // CASO 4: Ambos conservan historial. Verificar conflicto de roles.
        if (currentRole != Role.NONE && remoteRole != Role.NONE) {
            if (currentRole == remoteRole) {
                // Hay un conflicto de rol (ambos dicen ser A o ambos dicen ser B)
                // Se debe resolver dando prioridad al rol opuesto según sea necesario
                // Por defecto el que recibe la conexión de handshake conserva o se ajusta.
                currentRole = remoteRole.opposite()
                return HandshakeRoleResult(
                    assignedRole = currentRole,
                    shouldNotifyRemote = false,
                    explanation = "Conflicto de roles detectado. Reasignado automáticamente a ${currentRole.shortName()}."
                )
            }
        }

        return HandshakeRoleResult(
            assignedRole = currentRole,
            explanation = "Roles sincronizados correctamente."
        )
    }

    /**
     * Resuelve el conflicto cuando ambos dispositivos envían su primer mensaje casi al mismo tiempo
     * y los dos asumieron el rol **Dispositivo A**.
     *
     * REGLA:
     * - Gana A el mensaje con timestamp más antiguo.
     * - En caso de empate exacto, gana A la IP numéricamente menor.
     *
     * @param localMsg Mensaje enviado localmente.
     * @param remoteMsg Mensaje recibido del remoto.
     * @param localIp IP local de este dispositivo.
     * @param remoteIp IP del dispositivo remoto.
     * @return `true` si el dispositivo local conserva el rol A; `false` si pasa a ser B.
     */
    fun resolveSimultaneousFirstMessage(
        localMsg: Message,
        remoteMsg: Message,
        localIp: String,
        remoteIp: String
    ): Boolean {
        // Comparación de timestamp de envío
        val timeComparison = localMsg.timestamp.compareTo(remoteMsg.timestamp)
        if (timeComparison < 0) {
            // El mensaje local es más antiguo -> local mantiene A
            currentRole = Role.A
            return true
        } else if (timeComparison > 0) {
            // El mensaje remoto es más antiguo -> local cede y pasa a B
            currentRole = Role.B
            return false
        }

        // Empate exacto en timestamp -> Comparación numérica de la IP
        val ipComparison = compareIpAddresses(localIp, remoteIp)
        if (ipComparison < 0) {
            currentRole = Role.A
            return true
        } else {
            currentRole = Role.B
            return false
        }
    }

    /**
     * Reinicia el rol local cuando el usuario borra el historial de conversación.
     */
    fun onHistoryCleared() {
        currentRole = Role.NONE
    }

    /**
     * Compara sintácticamente dos direcciones IPv4.
     */
    private fun compareIpAddresses(ip1: String, ip2: String): Int {
        val parts1 = ip1.split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = ip2.split(".").mapNotNull { it.toIntOrNull() }

        for (i in 0 until minOf(parts1.size, parts2.size)) {
            if (parts1[i] != parts2[i]) {
                return parts1[i].compareTo(parts2[i])
            }
        }
        return parts1.size.compareTo(parts2.size)
    }
}
