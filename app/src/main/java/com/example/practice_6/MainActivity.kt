package com.example.practice_6

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.practice_6.model.Message
import com.example.practice_6.model.MessageStatus
import com.example.practice_6.model.Role
import com.example.practice_6.network.NetworkDiagnosticState
import com.example.practice_6.ui.MainViewModel
import com.example.practice_6.ui.UiState

/**
 * ====================================================================================
 * PRÁCTICA 6: Interacción entre Dispositivos Móviles - MobileConnect
 * ====================================================================================
 * Aplicación Android desarrollada en Kotlin y Jetpack Compose con arquitectura MVVM.
 * Permite el intercambio bidireccional y persistente de mensajes por sockets TCP en red local
 * con confirmación de entrega (ACK), monitoreo keepalive (PING/PONG) y panel de diagnóstico.
 * ====================================================================================
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current

                    LaunchedEffect(Unit) {
                        viewModel.init(context)
                    }

                    val uiState by viewModel.uiState.collectAsState()

                    MobileConnectApp(
                        uiState = uiState,
                        onRemoteIpChanged = viewModel::onRemoteIpInputChanged,
                        onRemotePortChanged = viewModel::onRemotePortInputChanged,
                        onConnectClicked = viewModel::onConnectClicked,
                        onMessageInputChanged = viewModel::onMessageInputChanged,
                        onSendMessageClicked = viewModel::onSendMessageClicked,
                        onRetrySendMessage = viewModel::retrySendMessage,
                        onClearHistoryClicked = viewModel::onClearHistoryRequested,
                        onConfirmClearHistory = viewModel::confirmClearHistory,
                        onDismissClearConfirm = viewModel::dismissClearConfirmDialog,
                        onDismissConnectionDialog = viewModel::dismissConnectionDialog,
                        onToggleDiagnosticPanel = viewModel::toggleDiagnosticPanel,
                        onRefreshLocalIp = { viewModel.refreshLocalIp(context) },
                        onConsumeSnackbar = viewModel::consumeSnackbarMessage
                    )
                }
            }
        }
    }
}

/**
 * COMPOSABLE PRINCIPAL: MobileConnectApp
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileConnectApp(
    uiState: UiState,
    onRemoteIpChanged: (String) -> Unit,
    onRemotePortChanged: (String) -> Unit,
    onConnectClicked: () -> Unit,
    onMessageInputChanged: (String) -> Unit,
    onSendMessageClicked: () -> Unit,
    onRetrySendMessage: (Message) -> Unit,
    onClearHistoryClicked: () -> Unit,
    onConfirmClearHistory: () -> Unit,
    onDismissClearConfirm: () -> Unit,
    onDismissConnectionDialog: () -> Unit,
    onToggleDiagnosticPanel: () -> Unit,
    onRefreshLocalIp: () -> Unit,
    onConsumeSnackbar: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    uiState.snackbarMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            onConsumeSnackbar()
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBarSection(
                isConnected = uiState.isConnected,
                connectedRemoteIp = uiState.connectedRemoteIp,
                onToggleDiagnosticPanel = onToggleDiagnosticPanel
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // PANEL DE DIAGNÓSTICO COLAPSABLE
            if (uiState.showDiagnosticPanel) {
                DiagnosticPanelCard(diagState = uiState.diagnosticState)
            }

            // 1. TARJETA DE CONEXIÓN POR IP Y PUERTO
            ConnectionCardSection(
                localIp = uiState.localIp,
                remoteIpInput = uiState.remoteIpInput,
                remotePortInput = uiState.remotePortInput,
                ipError = uiState.ipError,
                portError = uiState.portError,
                isConnecting = uiState.isConnecting,
                myRole = uiState.myRole,
                onRemoteIpChanged = onRemoteIpChanged,
                onRemotePortChanged = onRemotePortChanged,
                onConnectClicked = onConnectClicked,
                onRefreshLocalIp = onRefreshLocalIp
            )

            // 2. CONTADORES DE MENSAJES
            CountersCardSection(
                counterA = uiState.counterA,
                counterB = uiState.counterB,
                totalMessages = uiState.messages.size
            )

            // 3. ENCABEZADO Y BOTÓN DE LIMPIAR HISTORIAL
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Conversación (${uiState.messages.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedButton(
                    onClick = onClearHistoryClicked,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Limpiar Historial",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Limpiar historial", fontSize = 12.sp)
                }
            }

            HorizontalDivider()

            // 4. LISTA DE CONVERSACIÓN
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (uiState.messages.isEmpty()) {
                    EmptyConversationView()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(
                            items = uiState.messages,
                            key = { message -> message.id }
                        ) { message ->
                            androidx.compose.animation.AnimatedVisibility(
                                visible = true,
                                enter = fadeIn() + slideInVertically()
                            ) {
                                MessageBubbleView(
                                    message = message,
                                    myRole = uiState.myRole,
                                    onRetrySendMessage = onRetrySendMessage
                                )
                            }
                        }
                    }
                }
            }

            // 5. BARRA INFERIOR DE REDACCIÓN Y ENVÍO DE MENSAJE
            MessageInputSection(
                messageInput = uiState.messageInput,
                messageInputError = uiState.messageInputError,
                isConnected = uiState.isConnected,
                onMessageInputChanged = onMessageInputChanged,
                onSendMessageClicked = onSendMessageClicked
            )
        }
    }

    // DIÁLOGOS DE ALERTA
    if (uiState.showConnectionDialog) {
        AlertDialog(
            onDismissRequest = onDismissConnectionDialog,
            title = {
                Text(
                    text = uiState.connectionDialogTitle,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = uiState.connectionDialogText)
            },
            confirmButton = {
                Button(onClick = onDismissConnectionDialog) {
                    Text("Aceptar")
                }
            }
        )
    }

    if (uiState.showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = onDismissClearConfirm,
            title = {
                Text("Confirmar Limpieza", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("¿Estás seguro de que deseas borrar todo el historial de conversación?\n\nEsta acción reiniciará los contadores y liberará tu rol asignado a 'Por definir'.")
            },
            confirmButton = {
                Button(
                    onClick = onConfirmClearHistory,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Sí, borrar historial")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissClearConfirm) {
                    Text("Cancelar")
                }
            }
        )
    }
}

/**
 * COMPOSABLE: TopAppBar con botón de acceso al panel de diagnóstico.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarSection(
    isConnected: Boolean,
    connectedRemoteIp: String?,
    onToggleDiagnosticPanel: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "MobileConnect",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) Color(0xFF4CAF50) else Color(0xFFE53935))
                    )
                    Text(
                        text = if (isConnected && !connectedRemoteIp.isNullOrBlank()) {
                            "Conectado a $connectedRemoteIp"
                        } else {
                            "Desconectado"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onToggleDiagnosticPanel) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = "Panel de Diagnóstico",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}

/**
 * COMPOSABLE: Panel de diagnóstico técnico colapsable.
 */
@Composable
fun DiagnosticPanelCard(diagState: NetworkDiagnosticState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "🛠️ Panel de Diagnóstico de Red TCP",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text = "• Servidor TCP: ${if (diagState.isServerListening) "Escuchando en 0.0.0.0:${diagState.serverPort}" else "Detenido"}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "• Socket Remoto: ${diagState.connectedRemoteAddress ?: "Ninguno (Desconectado)"}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "• Última Actividad: ${diagState.lastActivityTime}",
                style = MaterialTheme.typography.bodySmall
            )
            if (!diagState.lastError.isNullOrBlank()) {
                Text(
                    text = "• Último Error: ${diagState.lastError}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * COMPOSABLE: Tarjeta de conexión con IP y Puerto configurable.
 */
@Composable
fun ConnectionCardSection(
    localIp: String,
    remoteIpInput: String,
    remotePortInput: String,
    ipError: String?,
    portError: String?,
    isConnecting: Boolean,
    myRole: Role,
    onRemoteIpChanged: (String) -> Unit,
    onRemotePortChanged: (String) -> Unit,
    onConnectClicked: () -> Unit,
    onRefreshLocalIp: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Mi IP (Auto): ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = localIp,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(
                    onClick = onRefreshLocalIp,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refrescar IP local",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Campos de entrada: IP + Puerto
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = remoteIpInput,
                    onValueChange = onRemoteIpChanged,
                    modifier = Modifier.weight(1.8f),
                    label = { Text("IP remota") },
                    placeholder = { Text("Ej: 192.168.1.15") },
                    singleLine = true,
                    isError = ipError != null,
                    supportingText = if (ipError != null) {
                        { Text(text = ipError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                    } else null,
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = remotePortInput,
                    onValueChange = onRemotePortChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text("Puerto") },
                    placeholder = { Text("8888") },
                    singleLine = true,
                    isError = portError != null,
                    supportingText = if (portError != null) {
                        { Text(text = portError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                    } else null,
                    shape = RoundedCornerShape(10.dp)
                )

                Button(
                    onClick = onConnectClicked,
                    enabled = !isConnecting,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(56.dp)
                        .padding(top = 4.dp)
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Conectar", fontSize = 12.sp)
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when (myRole) {
                    Role.A -> MaterialTheme.colorScheme.primaryContainer
                    Role.B -> MaterialTheme.colorScheme.secondaryContainer
                    Role.NONE -> MaterialTheme.colorScheme.surface
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (myRole == Role.NONE) Icons.Default.Info else Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Text(
                            text = "Mi rol:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = myRole.displayName(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CountersCardSection(
    counterA: Int,
    counterB: Int,
    totalMessages: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DeviceCounterBadge(
                deviceName = "Dispositivo A",
                count = counterA,
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )

            Text(
                text = "Total entregados: $totalMessages",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            DeviceCounterBadge(
                deviceName = "Dispositivo B",
                count = counterB,
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        }
    }
}

@Composable
fun DeviceCounterBadge(
    deviceName: String,
    count: Int,
    containerColor: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PhoneAndroid,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "$deviceName: ",
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EmptyConversationView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No hay mensajes en el historial.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            Text(
                text = "Ingresa la IP del otro dispositivo, conecta y envía un mensaje.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

/**
 * COMPOSABLE: Burbuja de conversación con estado de entrega (ACK) y botón de reintento.
 */
@Composable
fun MessageBubbleView(
    message: Message,
    myRole: Role,
    onRetrySendMessage: (Message) -> Unit
) {
    val isMyMessage = (myRole != Role.NONE && message.senderRole == myRole)

    val alignment = if (isMyMessage) Alignment.End else Alignment.Start
    val bubbleColor = if (isMyMessage) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val textColor = if (isMyMessage) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isMyMessage) 16.dp else 2.dp,
                        bottomEnd = if (isMyMessage) 2.dp else 16.dp
                    )
                )
                .background(bubbleColor)
                .padding(10.dp)
        ) {
            Text(
                text = message.senderRole.shortName(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = textColor.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = message.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.6f)
                )

                // Indicador visual de confirmación ACK
                if (isMyMessage) {
                    when (message.status) {
                        MessageStatus.PENDING -> {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Enviando...",
                                modifier = Modifier.size(12.dp),
                                tint = textColor.copy(alpha = 0.5f)
                            )
                        }
                        MessageStatus.SENT -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Entregado (ACK)",
                                modifier = Modifier.size(12.dp),
                                tint = Color(0xFF4CAF50)
                            )
                        }
                        MessageStatus.FAILED -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onRetrySendMessage(message) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "No enviado",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = " Reintentar",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageInputSection(
    messageInput: String,
    messageInputError: String?,
    isConnected: Boolean,
    onMessageInputChanged: (String) -> Unit,
    onSendMessageClicked: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = messageInput,
                onValueChange = onMessageInputChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Escribe un mensaje aquí...") },
                singleLine = false,
                maxLines = 3,
                isError = messageInputError != null,
                supportingText = if (messageInputError != null) {
                    { Text(text = messageInputError, color = MaterialTheme.colorScheme.error) }
                } else null,
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = onSendMessageClicked,
                enabled = isConnected && messageInput.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(56.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Enviar mensaje"
                )
            }
        }

        Text(
            text = "${messageInput.length} / 200 caracteres",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.align(Alignment.End)
        )
    }
}
