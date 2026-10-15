package com.example.practice_6.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.practice_6.data.PreferencesManager
import com.example.practice_6.domain.RoleManager
import com.example.practice_6.model.Message
import com.example.practice_6.model.MessageStatus
import com.example.practice_6.model.Role
import com.example.practice_6.network.NetworkDiagnosticState
import com.example.practice_6.network.NetworkUtils
import com.example.practice_6.network.SocketManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado completo inmutable de la interfaz gráfica y diagnóstico de red.
 */
data class UiState(
    val localIp: String = "Detectando...",
    val remoteIpInput: String = "",
    val remotePortInput: String = "8888",
    val connectedRemoteIp: String? = null,
    val isConnected: Boolean = false,
    val myRole: Role = Role.NONE,
    val messages: List<Message> = emptyList(),
    val counterA: Int = 0,
    val counterB: Int = 0,
    val ipError: String? = null,
    val portError: String? = null,
    val messageInput: String = "",
    val messageInputError: String? = null,
    val isConnecting: Boolean = false,
    val showConnectionDialog: Boolean = false,
    val connectionDialogTitle: String = "",
    val connectionDialogText: String = "",
    val showClearConfirmDialog: Boolean = false,
    val showDiagnosticPanel: Boolean = false,
    val diagnosticState: NetworkDiagnosticState = NetworkDiagnosticState(),
    val snackbarMessage: String? = null
)

/**
 * ViewModel principal de MobileConnect.
 *
 * Mantiene la conexión TCP persistente activa, gestiona el estado de entrega (ACK),
 * reintentos de mensajes no entregados y expone información de diagnóstico.
 */
class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val socketManager = SocketManager()
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var roleManager: RoleManager

    private var isInitialized = false

    /**
     * Inicializa la app, detecta la IP propia (incluyendo Hotspot) y arranca el servidor TCP.
     */
    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        preferencesManager = PreferencesManager(context.applicationContext)

        val detectedLocalIp = NetworkUtils.getLocalIpAddress(context.applicationContext)
        val savedHistory = preferencesManager.loadHistory()
        val savedRole = preferencesManager.loadRole()
        val savedRemoteIp = preferencesManager.loadRemoteIp()
        val savedPort = preferencesManager.loadPort()

        roleManager = RoleManager(savedRole)

        // Contar únicamente mensajes confirmados como ENVIADOS o recibidos
        val countA = savedHistory.count { it.senderRole == Role.A && it.status == MessageStatus.SENT }
        val countB = savedHistory.count { it.senderRole == Role.B && it.status == MessageStatus.SENT }

        _uiState.update { state ->
            state.copy(
                localIp = detectedLocalIp,
                remoteIpInput = savedRemoteIp,
                remotePortInput = savedPort.toString(),
                myRole = savedRole,
                messages = savedHistory,
                counterA = countA,
                counterB = countB
            )
        }

        // Observar diagnóstico del SocketManager
        viewModelScope.launch {
            socketManager.diagnosticState.collect { diag ->
                _uiState.update { it.copy(diagnosticState = diag, isConnected = diag.isConnected) }
            }
        }

        setupSocketCallbacks()
        socketManager.startServer(viewModelScope, savedPort)
    }

    private fun setupSocketCallbacks() {
        socketManager.onHelloReceived = { remoteIp, remoteRoleStr, remoteHasHistory ->
            viewModelScope.launch {
                val localHasHistory = _uiState.value.messages.isNotEmpty()

                val result = roleManager.processHandshake(
                    remoteRoleStr = remoteRoleStr,
                    remoteHasHistory = remoteHasHistory,
                    localHasHistory = localHasHistory
                )

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
                val roleChanged = roleManager.onReceivingMessage(message.senderRole)
                val currentRole = roleManager.currentRole

                if (roleChanged) {
                    preferencesManager.saveRole(currentRole)
                }

                val confirmedMessage = message.copy(status = MessageStatus.SENT)
                val updatedList = _uiState.value.messages + confirmedMessage
                preferencesManager.saveHistory(updatedList)

                val newCountA = updatedList.count { it.senderRole == Role.A && it.status == MessageStatus.SENT }
                val newCountB = updatedList.count { it.senderRole == Role.B && it.status == MessageStatus.SENT }

                _uiState.update { state ->
                    state.copy(
                        messages = updatedList,
                        counterA = newCountA,
                        counterB = newCountB,
                        myRole = currentRole,
                        snackbarMessage = "📥 Nuevo mensaje recibido de ${message.senderRole.shortName()}"
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

        socketManager.onConnectionDropped = { reason ->
            viewModelScope.launch {
                _uiState.update { state ->
                    state.copy(
                        isConnected = false,
                        connectedRemoteIp = null,
                        snackbarMessage = "⚠️ Conexión caída: $reason"
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

    fun onRemoteIpInputChanged(input: String) {
        _uiState.update { it.copy(remoteIpInput = input, ipError = null) }
    }

    fun onRemotePortInputChanged(input: String) {
        _uiState.update { it.copy(remotePortInput = input, portError = null) }
    }

    fun onConnectClicked() {
        val currentState = _uiState.value
        val ipValidation = NetworkUtils.validateIpAddress(currentState.remoteIpInput, currentState.localIp)
        val portValidation = NetworkUtils.validatePort(currentState.remotePortInput)

        if (!ipValidation.isValid) {
            _uiState.update { it.copy(ipError = ipValidation.errorMessage) }
            return
        }

        if (!portValidation.isValid) {
            _uiState.update { it.copy(portError = portValidation.errorMessage) }
            return
        }

        val targetIp = currentState.remoteIpInput.trim()
        val targetPort = portValidation.port

        _uiState.update { it.copy(isConnecting = true, ipError = null, portError = null) }

        viewModelScope.launch {
            val result = socketManager.connectToRemote(
                scope = viewModelScope,
                remoteIp = targetIp,
                remotePort = targetPort,
                localIp = currentState.localIp
            )

            result.onSuccess { actualRemoteIp ->
                preferencesManager.saveRemoteIp(actualRemoteIp)
                preferencesManager.savePort(targetPort)

                // Enviar HELLO por la conexión persistente recíproca
                val hasHistory = currentState.messages.isNotEmpty()
                socketManager.sendHello(
                    role = roleManager.currentRole.name,
                    hasHistory = hasHistory
                )

                val roleMsg = if (roleManager.currentRole != Role.NONE) {
                    "\n\n${roleManager.currentRole.displayName()}"
                } else ""

                _uiState.update { state ->
                    state.copy(
                        isConnecting = false,
                        isConnected = true,
                        connectedRemoteIp = actualRemoteIp,
                        showConnectionDialog = true,
                        connectionDialogTitle = "Conexión establecida",
                        connectionDialogText = "Conexión establecida con $actualRemoteIp en puerto $targetPort.$roleMsg",
                        snackbarMessage = "Conectado exitosamente con $actualRemoteIp"
                    )
                }
            }.onFailure { exception ->
                _uiState.update { state ->
                    state.copy(
                        isConnecting = false,
                        showConnectionDialog = true,
                        connectionDialogTitle = "Error de conexión",
                        connectionDialogText = exception.localizedMessage ?: "No se pudo establecer la conexión TCP.",
                        snackbarMessage = "Error al intentar conectar."
                    )
                }
            }
        }
    }

    fun onMessageInputChanged(input: String) {
        if (input.length <= 200) {
            _uiState.update { it.copy(messageInput = input, messageInputError = null) }
        }
    }

    /**
     * Envía un mensaje con confirmación de entrega (ACK).
     * Solo se marca como "enviado" y suma al contador si se recibe el ACK.
     */
    fun onSendMessageClicked() {
        val currentState = _uiState.value
        val trimmedText = currentState.messageInput.trim()

        if (trimmedText.isBlank()) {
            _uiState.update {
                it.copy(
                    messageInputError = "El mensaje no puede estar vacío.",
                    snackbarMessage = "⚠️ Escribe un mensaje antes de enviar."
                )
            }
            return
        }

        val assignedRole = roleManager.onSendingFirstMessage()
        preferencesManager.saveRole(assignedRole)

        val pendingMessage = Message(
            text = trimmedText,
            senderRole = assignedRole,
            status = MessageStatus.PENDING
        )

        // Agregar mensaje en estado PENDING a la vista
        val initialList = currentState.messages + pendingMessage
        _uiState.update { it.copy(messageInput = "", messages = initialList, myRole = assignedRole) }

        sendMessageWithAckInternal(pendingMessage)
    }

    /**
     * Reintenta el envío de un mensaje que previamente falló.
     */
    fun retrySendMessage(message: Message) {
        sendMessageWithAckInternal(message)
    }

    private fun sendMessageWithAckInternal(message: Message) {
        viewModelScope.launch {
            val result = socketManager.sendMessageWithAck(message)

            result.onSuccess {
                // Marcar mensaje como SENT
                val currentList = _uiState.value.messages
                val updatedList = currentList.map { msg ->
                    if (msg.id == message.id) msg.copy(status = MessageStatus.SENT) else msg
                }
                preferencesManager.saveHistory(updatedList)

                val newCountA = updatedList.count { it.senderRole == Role.A && it.status == MessageStatus.SENT }
                val newCountB = updatedList.count { it.senderRole == Role.B && it.status == MessageStatus.SENT }

                _uiState.update { state ->
                    state.copy(
                        messages = updatedList,
                        counterA = newCountA,
                        counterB = newCountB,
                        snackbarMessage = "✅ Mensaje entregado correctamente"
                    )
                }
            }.onFailure { exception ->
                // Marcar mensaje como FAILED
                val currentList = _uiState.value.messages
                val updatedList = currentList.map { msg ->
                    if (msg.id == message.id) msg.copy(status = MessageStatus.FAILED) else msg
                }
                preferencesManager.saveHistory(updatedList)

                _uiState.update { state ->
                    state.copy(
                        messages = updatedList,
                        snackbarMessage = "❌ Entrega no confirmada: ${exception.localizedMessage}"
                    )
                }
            }
        }
    }

    fun toggleDiagnosticPanel() {
        _uiState.update { it.copy(showDiagnosticPanel = !it.showDiagnosticPanel) }
    }

    fun onClearHistoryRequested() {
        _uiState.update { it.copy(showClearConfirmDialog = true) }
    }

    fun confirmClearHistory() {
        preferencesManager.clearHistoryAndRole()
        roleManager.onHistoryCleared()

        _uiState.update { state ->
            state.copy(
                messages = emptyList(),
                counterA = 0,
                counterB = 0,
                myRole = Role.NONE,
                showClearConfirmDialog = false,
                snackbarMessage = "🧹 Historial y rol limpiados."
            )
        }

        viewModelScope.launch {
            socketManager.sendClear()
        }
    }

    fun dismissConnectionDialog() {
        _uiState.update { it.copy(showConnectionDialog = false) }
    }

    fun dismissClearConfirmDialog() {
        _uiState.update { it.copy(showClearConfirmDialog = false) }
    }

    fun consumeSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun refreshLocalIp(context: Context) {
        val currentIp = NetworkUtils.getLocalIpAddress(context.applicationContext)
        _uiState.update { it.copy(localIp = currentIp) }
    }

    override fun onCleared() {
        super.onCleared()
        socketManager.stopServer()
    }
}
