package com.example.practice_6.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.practice_6.data.PreferencesManager
import com.example.practice_6.domain.RoleManager
import com.example.practice_6.model.Message
import com.example.practice_6.model.Role
import com.example.practice_6.network.NetworkUtils
import com.example.practice_6.network.SocketManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado inmutable de la interfaz de usuario para la pantalla principal [MobileConnectApp].
 */
data class UiState(
    val localIp: String = "Detectando...",
    val remoteIpInput: String = "",
    val connectedRemoteIp: String? = null,
    val isConnected: Boolean = false,
    val myRole: Role = Role.NONE,
    val messages: List<Message> = emptyList(),
    val counterA: Int = 0,
    val counterB: Int = 0,
    val ipError: String? = null,
    val messageInput: String = "",
    val messageInputError: String? = null,
    val isConnecting: Boolean = false,
    val showConnectionDialog: Boolean = false,
    val connectionDialogTitle: String = "",
    val connectionDialogText: String = "",
    val showClearConfirmDialog: Boolean = false,
    val snackbarMessage: String? = null
)

/**
 * ViewModel principal de la arquitectura MVVM para MobileConnect.
 *
 * Mantiene y sobrevive a rotaciones de pantalla, gestionando las operaciones
 * de red por sockets, persistencia y lógica de asignación automática de roles.
 */
class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val socketManager = SocketManager()
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var roleManager: RoleManager

    private var isInitialized = false

    /**
     * Inicializa las utilidades de red, carga el historial guardado y arranca el servidor TCP.
     */
    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        preferencesManager = PreferencesManager(context.applicationContext)

        // 1. Detectar IP local
        val detectedLocalIp = NetworkUtils.getLocalIpAddress(context.applicationContext)

        // 2. Cargar historial y rol persistido
        val savedHistory = preferencesManager.loadHistory()
        val savedRole = preferencesManager.loadRole()
        val savedRemoteIp = preferencesManager.loadRemoteIp()

        roleManager = RoleManager(savedRole)

        // Calcular contadores iniciales basados en el historial guardado
        val countA = savedHistory.count { it.senderRole == Role.A }
        val countB = savedHistory.count { it.senderRole == Role.B }

        _uiState.update { state ->
            state.copy(
                localIp = detectedLocalIp,
                remoteIpInput = savedRemoteIp,
                myRole = savedRole,
                messages = savedHistory,
                counterA = countA,
                counterB = countB
            )
        }

        // 3. Configurar callbacks del servidor socket
        setupSocketCallbacks()

        // 4. Iniciar servidor TCP en segundo plano
        socketManager.startServer(viewModelScope)
    }

    /**
     * Registra las acciones a ejecutar cuando se reciben mensajes o eventos por el socket.
     */
    private fun setupSocketCallbacks() {
        socketManager.onHelloReceived = { remoteIp, remoteRoleStr, remoteHasHistory ->
            viewModelScope.launch {
                val localHasHistory = _uiState.value.messages.isNotEmpty()

                // Procesar regla de roles mediante el RoleManager
                val result = roleManager.processHandshake(
                    remoteRoleStr = remoteRoleStr,
                    remoteHasHistory = remoteHasHistory,
                    localHasHistory = localHasHistory
                )

                // Guardar nuevo rol y la IP del emisor automáticamente
                preferencesManager.saveRole(result.assignedRole)
                preferencesManager.saveRemoteIp(remoteIp)

                _uiState.update { state ->
                    val roleMsg = if (result.assignedRole != Role.NONE) {
                        "\n\n${result.assignedRole.displayName()}"
                    } else ""

                    state.copy(
                        remoteIpInput = if (state.remoteIpInput.isBlank()) remoteIp else state.remoteIpInput,
                        connectedRemoteIp = remoteIp,
                        isConnected = true,
                        myRole = result.assignedRole,
                        showConnectionDialog = true,
                        connectionDialogTitle = "Conexión establecida",
                        connectionDialogText = "Se ha conectado exitosamente con el dispositivo en la IP $remoteIp.$roleMsg",
                        snackbarMessage = "Conectado automáticamente con $remoteIp"
                    )
                }
            }
        }

        socketManager.onMessageReceived = { message ->
            viewModelScope.launch {
                // Si el rol local no estaba definido, se actualiza automáticamente al complementario
                val roleChanged = roleManager.onReceivingMessage(message.senderRole)
                val currentRole = roleManager.currentRole

                if (roleChanged) {
                    preferencesManager.saveRole(currentRole)
                }

                // Agregar mensaje al historial local
                val updatedList = _uiState.value.messages + message
                preferencesManager.saveHistory(updatedList)

                val newCountA = updatedList.count { it.senderRole == Role.A }
                val newCountB = updatedList.count { it.senderRole == Role.B }

                _uiState.update { state ->
                    state.copy(
                        messages = updatedList,
                        counterA = newCountA,
                        counterB = newCountB,
                        myRole = currentRole,
                        snackbarMessage = "📥 Nuevo mensaje de ${message.senderRole.shortName()}"
                    )
                }
            }
        }

        socketManager.onClearReceived = {
            viewModelScope.launch {
                _uiState.update { state ->
                    state.copy(
                        snackbarMessage = "ℹ️ El dispositivo remoto ha limpiado su historial."
                    )
                }
            }
        }

        socketManager.onErrorOccurred = { errorMsg ->
            viewModelScope.launch {
                _uiState.update { state ->
                    state.copy(
                        snackbarMessage = "⚠️ $errorMsg"
                    )
                }
            }
        }
    }

    /**
     * Actualiza la entrada de texto de la IP del dispositivo remoto.
     */
    fun onRemoteIpInputChanged(input: String) {
        _uiState.update { it.copy(remoteIpInput = input, ipError = null) }
    }

    /**
     * Realiza la validación de la IP e intenta establecer conexión enviando un paquete `HELLO`.
     */
    fun onConnectClicked() {
        val currentState = _uiState.value
        val validation = NetworkUtils.validateIpAddress(currentState.remoteIpInput, currentState.localIp)

        if (!validation.isValid) {
            _uiState.update { it.copy(ipError = validation.errorMessage) }
            return
        }

        val targetIp = currentState.remoteIpInput.trim()

        _uiState.update { it.copy(isConnecting = true, ipError = null) }

        viewModelScope.launch {
            val hasHistory = currentState.messages.isNotEmpty()
            val result = socketManager.sendHello(
                targetIp = targetIp,
                localIp = currentState.localIp,
                role = roleManager.currentRole.name,
                hasHistory = hasHistory
            )

            result.onSuccess {
                preferencesManager.saveRemoteIp(targetIp)

                val roleMsg = if (roleManager.currentRole != Role.NONE) {
                    "\n\n${roleManager.currentRole.displayName()}"
                } else ""

                _uiState.update { state ->
                    state.copy(
                        isConnecting = false,
                        isConnected = true,
                        connectedRemoteIp = targetIp,
                        showConnectionDialog = true,
                        connectionDialogTitle = "Conexión establecida",
                        connectionDialogText = "Conexión establecida con $targetIp.$roleMsg",
                        snackbarMessage = "Conexión con $targetIp exitosa."
                    )
                }
            }.onFailure { exception ->
                _uiState.update { state ->
                    state.copy(
                        isConnecting = false,
                        showConnectionDialog = true,
                        connectionDialogTitle = "Error de conexión",
                        connectionDialogText = "No se pudo conectar con $targetIp:\n${exception.localizedMessage}",
                        snackbarMessage = "Error al intentar conectar."
                    )
                }
            }
        }
    }

    /**
     * Actualiza el texto del mensaje en redacción (con límite de 200 caracteres).
     */
    fun onMessageInputChanged(input: String) {
        if (input.length <= 200) {
            _uiState.update { it.copy(messageInput = input, messageInputError = null) }
        }
    }

    /**
     * Valida y envía el mensaje redactado por el socket al dispositivo remoto.
     */
    fun onSendMessageClicked() {
        val currentState = _uiState.value
        val trimmedText = currentState.messageInput.trim()

        // VALIDACIÓN: No enviar mensajes vacíos o solo espacios
        if (trimmedText.isBlank()) {
            _uiState.update {
                it.copy(
                    messageInputError = "El mensaje no puede estar vacío.",
                    snackbarMessage = "⚠️ Escribe un mensaje antes de enviar."
                )
            }
            return
        }

        val targetIp = currentState.connectedRemoteIp ?: currentState.remoteIpInput.trim()
        if (targetIp.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "⚠️ No hay IP remota configurada para enviar.") }
            return
        }

        // Determinar rol del emisor (si es el primer mensaje, pasa a ser A)
        val assignedRole = roleManager.onSendingFirstMessage()
        preferencesManager.saveRole(assignedRole)

        val newMessage = Message(
            text = trimmedText,
            senderRole = assignedRole
        )

        viewModelScope.launch {
            val result = socketManager.sendMessage(targetIp, newMessage)

            result.onSuccess {
                val updatedList = currentState.messages + newMessage
                preferencesManager.saveHistory(updatedList)

                val newCountA = updatedList.count { it.senderRole == Role.A }
                val newCountB = updatedList.count { it.senderRole == Role.B }

                _uiState.update { state ->
                    state.copy(
                        messageInput = "",
                        messages = updatedList,
                        counterA = newCountA,
                        counterB = newCountB,
                        myRole = assignedRole,
                        snackbarMessage = "✅ Mensaje enviado"
                    )
                }
            }.onFailure { exception ->
                _uiState.update { state ->
                    state.copy(
                        snackbarMessage = "❌ Error al enviar mensaje: ${exception.localizedMessage}"
                    )
                }
            }
        }
    }

    /**
     * Muestra el diálogo de confirmación para limpiar el historial.
     */
    fun onClearHistoryRequested() {
        _uiState.update { it.copy(showClearConfirmDialog = true) }
    }

    /**
     * Borra el historial de conversación, reinicia contadores y resetea el rol local.
     * Avisa al otro dispositivo enviando un paquete `CLEAR`.
     */
    fun confirmClearHistory() {
        val currentState = _uiState.value
        val targetIp = currentState.connectedRemoteIp ?: currentState.remoteIpInput.trim()

        // Limpiar datos locales y resetear rol a NONE
        preferencesManager.clearHistoryAndRole()
        roleManager.onHistoryCleared()

        _uiState.update { state ->
            state.copy(
                messages = emptyList(),
                counterA = 0,
                counterB = 0,
                myRole = Role.NONE,
                showClearConfirmDialog = false,
                snackbarMessage = "🧹 Historial y rol limpiados correctamente."
            )
        }

        // Avisar al otro dispositivo si hay una IP conectada
        if (targetIp.isNotBlank()) {
            viewModelScope.launch {
                socketManager.sendClear(targetIp)
            }
        }
    }

    /**
     * Oculta el diálogo de alerta de conexión.
     */
    fun dismissConnectionDialog() {
        _uiState.update { it.copy(showConnectionDialog = false) }
    }

    /**
     * Oculta el diálogo de confirmación de limpieza de historial.
     */
    fun dismissClearConfirmDialog() {
        _uiState.update { it.copy(showClearConfirmDialog = false) }
    }

    /**
     * Consume el mensaje del Snackbar después de mostrarlo en la UI.
     */
    fun consumeSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    /**
     * Recarga la IP local manualmente (útil si cambió la conexión Wi-Fi).
     */
    fun refreshLocalIp(context: Context) {
        val currentIp = NetworkUtils.getLocalIpAddress(context.applicationContext)
        _uiState.update { it.copy(localIp = currentIp) }
    }

    override fun onCleared() {
        super.onCleared()
        socketManager.stopServer()
    }
}
