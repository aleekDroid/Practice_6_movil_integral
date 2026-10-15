package com.example.practice_6.network

import com.example.practice_6.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Estado del panel de diagnóstico de red.
 */
data class NetworkDiagnosticState(
    val isServerListening: Boolean = false,
    val serverPort: Int = 8888,
    val connectedRemoteAddress: String? = null,
    val isConnected: Boolean = false,
    val lastError: String? = null,
    val lastActivityTime: String = "Sin actividad"
)

/**
 * Gestor avanzado de comunicación por Sockets TCP para el proyecto MobileConnect.
 *
 * CARACTERÍSTICAS PRINCIPALES:
 * 1. Mantiene una ÚNICA conexión TCP persistente y bidireccional entre dispositivos.
 * 2. Extrae la IP remota directamente de `socket.remoteSocketAddress` (sin depender de campos declarados).
 * 3. Implementa confirmación de entrega confiable (ACK) y deduplicación por ID.
 * 4. Envía PING/PONG keepalive cada 15s y detecta caídas tras 40s de inactividad.
 * 5. Traduce excepciones de red a mensajes explicativos en español.
 */
class SocketManager {

    companion object {
        const val DEFAULT_PORT = 8888
        const val CONNECT_TIMEOUT_MS = 5000
        const val PING_INTERVAL_MS = 15000L
        const val KEEPALIVE_TIMEOUT_MS = 40000L
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var readerJob: Job? = null
    private var keepaliveJob: Job? = null

    // Socket TCP persistente único
    private var activeSocket: Socket? = null
    private var activeWriter: PrintWriter? = null
    private var activeReader: BufferedReader? = null
    private val socketMutex = Mutex()

    // Diagnóstico
    private val _diagnosticState = MutableStateFlow(NetworkDiagnosticState())
    val diagnosticState: StateFlow<NetworkDiagnosticState> = _diagnosticState.asStateFlow()

    // Manejo de confirmaciones ACK suspendidas
    private val pendingAcks = ConcurrentHashMap<String, (Boolean) -> Unit>()

    // Registro de IDs de mensajes recibidos para deduplicación
    private val receivedMessageIds = ConcurrentHashMap.newKeySet<String>()

    private var lastActivityMs = System.currentTimeMillis()

    // Callbacks hacia el ViewModel
    var onHelloReceived: ((remoteIp: String, remoteRole: String, hasHistory: Boolean) -> Unit)? = null
    var onMessageReceived: ((message: Message) -> Unit)? = null
    var onClearReceived: (() -> Unit)? = null
    var onConnectionDropped: ((reason: String) -> Unit)? = null
    var onErrorOccurred: ((errorMsg: String) -> Unit)? = null

    /**
     * Inicia el servidor TCP escuchando en 0.0.0.0 en el puerto especificado.
     */
    fun startServer(scope: CoroutineScope, localPort: Int = DEFAULT_PORT) {
        if (serverJob?.isActive == true && _diagnosticState.value.serverPort == localPort) return

        stopServer()

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                // Escuchar en 0.0.0.0:<localPort> con reuseAddress
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), localPort))
                serverSocket = ss

                _diagnosticState.update {
                    it.copy(
                        isServerListening = true,
                        serverPort = localPort,
                        lastError = null
                    )
                }

                while (isActive && !ss.isClosed) {
                    try {
                        val incomingSocket = ss.accept()
                        incomingSocket.keepAlive = true

                        val remoteHost = incomingSocket.inetAddress.hostAddress ?: "Desconocido"

                        // Si ya tenemos una conexión activa, aplicar regla de desempate por IP menor
                        socketMutex.withLock {
                            val currentRemote = activeSocket?.inetAddress?.hostAddress
                            if (activeSocket?.isConnected == true && currentRemote != null) {
                                val currentIpNum = ipToLong(currentRemote)
                                val incomingIpNum = ipToLong(remoteHost)

                                if (incomingIpNum < currentIpNum) {
                                    // La nueva conexión entrante tiene menor IP -> Cerramos la actual y aceptamos la nueva
                                    closeActiveSocketLocked()
                                    attachActiveSocketLocked(incomingSocket, scope)
                                } else {
                                    // La conexión actual se conserva -> Cerramos la entrante redundante
                                    incomingSocket.close()
                                }
                            } else {
                                attachActiveSocketLocked(incomingSocket, scope)
                            }
                        }
                    } catch (e: Exception) {
                        if (isActive && !ss.isClosed) {
                            val translated = translateNetworkError(e)
                            updateLastError(translated)
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    val translated = "Error al iniciar el servidor en el puerto $localPort: ${translateNetworkError(e)}"
                    updateLastError(translated)
                    _diagnosticState.update { it.copy(isServerListening = false) }
                }
            }
        }
    }

    /**
     * Establece la conexión persistente con el dispositivo remoto en la IP y puerto dados.
     */
    suspend fun connectToRemote(
        scope: CoroutineScope,
        remoteIp: String,
        remotePort: Int,
        localIp: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val clientSocket = Socket()
            clientSocket.keepAlive = true
            clientSocket.connect(InetSocketAddress(remoteIp, remotePort), CONNECT_TIMEOUT_MS)

            val actualRemoteIp = clientSocket.inetAddress.hostAddress ?: remoteIp

            socketMutex.withLock {
                closeActiveSocketLocked()
                attachActiveSocketLocked(clientSocket, scope)
            }

            Result.success(actualRemoteIp)
        } catch (e: Exception) {
            val errorMsg = translateNetworkError(e)
            updateLastError(errorMsg)
            Result.failure(Exception(errorMsg))
        }
    }

    /**
     * Asigna el socket TCP como la conexión persistente activa y lanza la lectura continua y Keepalive.
     */
    private fun attachActiveSocketLocked(socket: Socket, scope: CoroutineScope) {
        activeSocket = socket
        activeWriter = PrintWriter(socket.getOutputStream(), true)
        activeReader = BufferedReader(InputStreamReader(socket.inputStream, "UTF-8"))
        lastActivityMs = System.currentTimeMillis()

        val remoteIp = socket.inetAddress.hostAddress ?: "Desconocido"
        val remotePort = socket.port

        _diagnosticState.update {
            it.copy(
                isConnected = true,
                connectedRemoteAddress = "$remoteIp:$remotePort",
                lastActivityTime = Message.getCurrentFormattedTime()
            )
        }

        // Bucle de lectura de datos
        readerJob?.cancel()
        readerJob = scope.launch(Dispatchers.IO) {
            listenToIncomingLines(scope)
        }

        // Bucle de Keepalive (PING / PONG)
        keepaliveJob?.cancel()
        keepaliveJob = scope.launch(Dispatchers.IO) {
            runKeepaliveLoop(scope)
        }
    }

    /**
     * Lee continuamente las líneas JSON recibidas a través de la conexión persistente.
     */
    private suspend fun listenToIncomingLines(scope: CoroutineScope) {
        val reader = activeReader ?: return
        val currentSocket = activeSocket ?: return
        val remoteIp = currentSocket.inetAddress.hostAddress ?: "Desconocido"

        try {
            while (scope.isActive && currentSocket.isConnected && !currentSocket.isClosed) {
                val line = reader.readLine() ?: break // Fin de flujo (socket cerrado remotamente)
                updateActivity()

                if (line.isBlank()) continue
                val json = JSONObject(line)

                when (json.optString("type")) {
                    "HELLO" -> {
                        val role = json.optString("role", "NONE")
                        val hasHistory = json.optBoolean("hasHistory", false)
                        onHelloReceived?.invoke(remoteIp, role, hasHistory)
                    }
                    "MESSAGE" -> {
                        val message = Message.fromJsonObject(json)
                        // 1. Responder inmediatamente ACK
                        sendRawPayloadLocked("{\"type\":\"ACK\",\"id\":\"${message.id}\"}")

                        // 2. Procesar deduplicación
                        if (receivedMessageIds.add(message.id)) {
                            onMessageReceived?.invoke(message)
                        }
                    }
                    "ACK" -> {
                        val ackId = json.optString("id")
                        pendingAcks.remove(ackId)?.invoke(true)
                    }
                    "CLEAR" -> {
                        onClearReceived?.invoke()
                    }
                    "PING" -> {
                        sendRawPayloadLocked("{\"type\":\"PONG\"}")
                    }
                    "PONG" -> {
                        // Confirmación de latencia activa
                    }
                }
            }
        } catch (e: Exception) {
            if (scope.isActive && currentSocket.isClosed) {
                // Cierre esperado
            } else {
                updateLastError("Conexión perdida con $remoteIp: ${translateNetworkError(e)}")
            }
        } finally {
            socketMutex.withLock {
                if (activeSocket == currentSocket) {
                    closeActiveSocketLocked()
                    _diagnosticState.update {
                        it.copy(
                            isConnected = false,
                            connectedRemoteAddress = null
                        )
                    }
                    onConnectionDropped?.invoke("Conexión finalizada o interrumpida por la red.")
                }
            }
        }
    }

    /**
     * Envía un paquete `HELLO` mediante el socket persistente activo.
     */
    suspend fun sendHello(role: String, hasHistory: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("type", "HELLO")
            put("role", role)
            put("hasHistory", hasHistory)
        }
        sendPayloadAndFlush(payload.toString())
    }

    /**
     * Envía un mensaje y espera la confirmación `ACK` de entrega del receptor.
     */
    suspend fun sendMessageWithAck(message: Message, timeoutMs: Long = 5000L): Result<Unit> =
        withContext(Dispatchers.IO) {
            val jsonPayload = message.toJsonObject().toString()
            val sentSuccessfully = sendPayloadAndFlush(jsonPayload)

            if (sentSuccessfully.isFailure) {
                return@withContext sentSuccessfully
            }

            // Esperar ACK del receptor
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                val timerJob = CoroutineScope(Dispatchers.IO).launch {
                    delay(timeoutMs)
                    if (pendingAcks.remove(message.id) != null) {
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(Exception("No se recibió confirmación (ACK) del dispositivo receptor dentro de 5 s.")))
                        }
                    }
                }

                pendingAcks[message.id] = { ackSuccess ->
                    timerJob.cancel()
                    if (continuation.isActive) {
                        if (ackSuccess) {
                            continuation.resume(Result.success(Unit))
                        } else {
                            continuation.resume(Result.failure(Exception("Entrega de mensaje no confirmada.")))
                        }
                    }
                }
            }
        }

    /**
     * Envía la orden `CLEAR` para notificar la limpieza del historial.
     */
    suspend fun sendClear(): Result<Unit> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("type", "CLEAR")
        }
        sendPayloadAndFlush(payload.toString())
    }

    /**
     * Escribe una cadena JSON terminada en `\n` y ejecuta `flush()`.
     */
    private suspend fun sendPayloadAndFlush(jsonString: String): Result<Unit> = socketMutex.withLock {
        val writer = activeWriter
        val socket = activeSocket

        if (socket == null || !socket.isConnected || socket.isClosed || writer == null) {
            return Result.failure(Exception("No hay una conexión TCP activa. Ingresa la IP y presiona Conectar."))
        }

        return try {
            writer.println(jsonString)
            updateActivity()
            Result.success(Unit)
        } catch (e: Exception) {
            val errorMsg = translateNetworkError(e)
            updateLastError(errorMsg)
            Result.failure(Exception(errorMsg))
        }
    }

    private fun sendRawPayloadLocked(jsonString: String) {
        try {
            activeWriter?.println(jsonString)
            updateActivity()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Bucle Keepalive que transmite `PING` cada 15 s y verifica inactividad mayor a 40 s.
     */
    private suspend fun runKeepaliveLoop(scope: CoroutineScope) {
        while (scope.isActive) {
            delay(PING_INTERVAL_MS)
            val currentSocket = activeSocket ?: break
            if (!currentSocket.isConnected || currentSocket.isClosed) break

            val now = System.currentTimeMillis()
            if (now - lastActivityMs > KEEPALIVE_TIMEOUT_MS) {
                // Inactividad prolongada sin respuesta
                socketMutex.withLock {
                    closeActiveSocketLocked()
                    _diagnosticState.update {
                        it.copy(
                            isConnected = false,
                            connectedRemoteAddress = null,
                            lastError = "Conexión caída por inactividad (más de 40 s sin respuesta)."
                        )
                    }
                    onConnectionDropped?.invoke("Conexión caída por inactividad prolongada.")
                }
                break
            }

            // Enviar PING
            socketMutex.withLock {
                sendRawPayloadLocked("{\"type\":\"PING\"}")
            }
        }
    }

    private fun updateActivity() {
        lastActivityMs = System.currentTimeMillis()
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastActivityMs))
        _diagnosticState.update { it.copy(lastActivityTime = timeStr) }
    }

    private fun updateLastError(msg: String) {
        _diagnosticState.update { it.copy(lastError = msg) }
        onErrorOccurred?.invoke(msg)
    }

    private fun closeActiveSocketLocked() {
        try {
            activeWriter?.close()
            activeReader?.close()
            activeSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            activeWriter = null
            activeReader = null
            activeSocket = null
        }
    }

    /**
     * Cierra el servidor TCP y la conexión activa de forma limpia.
     */
    fun stopServer() {
        try {
            serverSocket?.close()
            serverJob?.cancel()
            readerJob?.cancel()
            keepaliveJob?.cancel()
            closeActiveSocketLocked()
            _diagnosticState.update {
                it.copy(
                    isServerListening = false,
                    isConnected = false,
                    connectedRemoteAddress = null
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Convierte una dirección IPv4 a un valor numérico para desempate de conexiones simultáneas.
     */
    private fun ipToLong(ip: String): Long {
        return try {
            val parts = ip.split(".").map { it.toLong() }
            (parts[0] shl 24) + (parts[1] shl 16) + (parts[2] shl 8) + parts[3]
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Traduce las excepciones de red Java a mensajes en español orientativos.
     */
    private fun translateNetworkError(e: Exception): String {
        return when (e) {
            is SocketTimeoutException -> "Tiempo agotado (5 s): revisa que ambos dispositivos estén en la misma red Wi-Fi y con la app abierta."
            is ConnectException -> "Conexión rechazada: la otra app no está escuchando en ese puerto o la IP es incorrecta."
            is NoRouteToHostException -> "Sin ruta al host: la IP no es alcanzable. Verifica si hay aislamiento de clientes activado en la red Wi-Fi."
            is SocketException -> "Error de socket TCP (${e.localizedMessage}): conexión interrumpida o reiniciada."
            else -> e.localizedMessage ?: "Error de comunicación por red."
        }
    }
}
