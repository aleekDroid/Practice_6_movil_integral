package com.example.practice_6.network

import com.example.practice_6.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Gestor de comunicación por sockets TCP para el proyecto MobileConnect.
 *
 * Mantiene un [ServerSocket] escuchando conexiones entrantes en el puerto constante **8888**,
 * y proporciona funciones asíncronas para enviar mensajes y paquetes de control
 * mediante sockets clientes hacia la IP del dispositivo remoto.
 */
class SocketManager {

    companion object {
        /** Puerto fijo TCP definido para la práctica universitaria. */
        const val PORT = 8888

        /** Tiempo de espera máximo en milisegundos para conectar un socket cliente. */
        const val CONNECTION_TIMEOUT_MS = 3000
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    // Callbacks para notificar eventos al ViewModel
    var onHelloReceived: ((remoteIp: String, remoteRole: String, hasHistory: Boolean) -> Unit)? = null
    var onMessageReceived: ((message: Message) -> Unit)? = null
    var onClearReceived: (() -> Unit)? = null
    var onErrorOccurred: ((errorMsg: String) -> Unit)? = null

    /**
     * Inicia el servidor TCP local en un hilo secundario ([Dispatchers.IO]).
     * Escucha conexiones entrantes indefinidamente mientras el alcance ([CoroutineScope]) esté activo.
     */
    fun startServer(scope: CoroutineScope) {
        if (serverJob?.isActive == true) return

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(PORT)
                while (isActive && serverSocket?.isClosed == false) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        // Procesar la conexión entrante en una corrutina independiente
                        launch(Dispatchers.IO) {
                            handleIncomingConnection(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (isActive && serverSocket?.isClosed == false) {
                            onErrorOccurred?.invoke("Error en conexión entrante: ${e.localizedMessage}")
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    onErrorOccurred?.invoke("No se pudo iniciar el servidor en el puerto $PORT: ${e.localizedMessage}")
                }
            }
        }
    }

    /**
     * Procesa los datos recibidos desde una conexión de socket entrante.
     */
    private fun handleIncomingConnection(socket: Socket) {
        socket.use { s ->
            try {
                val reader = BufferedReader(InputStreamReader(s.inputStream, "UTF-8"))
                val line = reader.readLine() ?: return
                val json = JSONObject(line)

                when (json.optString("type")) {
                    "HELLO" -> {
                        val ip = json.optString("ip")
                        val role = json.optString("role", "NONE")
                        val hasHistory = json.optBoolean("hasHistory", false)
                        onHelloReceived?.invoke(ip, role, hasHistory)
                    }
                    "MESSAGE" -> {
                        val message = Message.fromJsonObject(json)
                        onMessageReceived?.invoke(message)
                    }
                    "CLEAR" -> {
                        onClearReceived?.invoke()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Envía un paquete de protocolo `HELLO` para iniciar el saludo manual entre dispositivos.
     *
     * @param targetIp IP del dispositivo remoto al que se enviará la solicitud.
     * @param localIp IP propia que se adjunta para que el receptor nos registre automáticamente.
     * @param role Rol actual de este dispositivo ("A", "B" o "NONE").
     * @param hasHistory `true` si este dispositivo tiene mensajes guardados.
     */
    suspend fun sendHello(
        targetIp: String,
        localIp: String,
        role: String,
        hasHistory: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("type", "HELLO")
            put("ip", localIp)
            put("role", role)
            put("hasHistory", hasHistory)
        }
        sendPayload(targetIp, payload)
    }

    /**
     * Envía un objeto [Message] al dispositivo remoto.
     */
    suspend fun sendMessage(targetIp: String, message: Message): Result<Unit> =
        withContext(Dispatchers.IO) {
            sendPayload(targetIp, message.toJsonObject())
        }

    /**
     * Envía una notificación `CLEAR` para avisar al otro dispositivo que se limpió el historial.
     */
    suspend fun sendClear(targetIp: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().apply {
                put("type", "CLEAR")
            }
            sendPayload(targetIp, payload)
        }

    /**
     * Envía una línea de texto JSON hacia la IP de destino dada.
     */
    private fun sendPayload(targetIp: String, json: JSONObject): Result<Unit> {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(targetIp, PORT), CONNECTION_TIMEOUT_MS)
            socket.use { s ->
                val writer = PrintWriter(s.getOutputStream(), true)
                writer.println(json.toString())
            }
            Result.success(Unit)
        } catch (_: SocketTimeoutException) {
            Result.failure(Exception("Tiempo de espera agotado (Timeout) al conectar con $targetIp en puerto $PORT."))
        } catch (e: Exception) {
            Result.failure(Exception("Error de red con $targetIp: ${e.localizedMessage ?: "Conexión rechazada o red no disponible"}"))
        }
    }

    /**
     * Cierra el socket servidor y detiene la escucha activa.
     */
    fun stopServer() {
        try {
            serverSocket?.close()
            serverJob?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
