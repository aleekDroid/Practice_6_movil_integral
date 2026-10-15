package com.example.practice_6

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
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
import com.example.practice_6.model.Role
import com.example.practice_6.ui.MainViewModel
import com.example.practice_6.ui.UiState

/**
 * ====================================================================================
 * PRÁCTICA 6: Interacción entre Dispositivos Móviles - MobileConnect
 * ====================================================================================
 * Aplicación Android desarrollada en Kotlin y Jetpack Compose con arquitectura MVVM.
 * Permite el intercambio de mensajes breves en red local usando sockets TCP directos
 * (ServerSocket/Socket en puerto 8888) e IP local entre dos dispositivos.
 *
 * La asignación de roles (Dispositivo A / Dispositivo B) es 100% automática y transparente.
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

                    // Inicializar el ViewModel (red, base de datos SharedPreferences y servidor TCP)
                    LaunchedEffect(Unit) {
                        viewModel.init(context)
                    }

                    // Colección del estado reactivo mediante Flow
                    val uiState by viewModel.uiState.collectAsState()

                    MobileConnectApp(
                        uiState = uiState,
                        onRemoteIpChanged = viewModel::onRemoteIpInputChanged,
                        onConnectClicked = viewModel::onConnectClicked,
                        onMessageInputChanged = viewModel::onMessageInputChanged,
                        onSendMessageClicked = viewModel::onSendMessageClicked,
                        onClearHistoryClicked = viewModel::onClearHistoryRequested,
                        onConfirmClearHistory = viewModel::confirmClearHistory,
                        onDismissClearConfirm = viewModel::dismissClearConfirmDialog,
                        onDismissConnectionDialog = viewModel::dismissConnectionDialog,
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
 *
 * Renderiza la interfaz gráfica completa dividida en 5 secciones verticales
 * siguiendo las especificaciones del diseño funcional.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileConnectApp(
    uiState: UiState,
    onRemoteIpChanged: (String) -> Unit,
    onConnectClicked: () -> Unit,
    onMessageInputChanged: (String) -> Unit,
    onSendMessageClicked: () -> Unit,
    onClearHistoryClicked: () -> Unit,
    onConfirmClearHistory: () -> Unit,
    onDismissClearConfirm: () -> Unit,
    onDismissConnectionDialog: () -> Unit,
    onRefreshLocalIp: () -> Unit,
    onConsumeSnackbar: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // Mostrar Snackbar cuando cambia el mensaje en el UiState
    uiState.snackbarMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            onConsumeSnackbar()
        }
    }

    // Desplazamiento automático al último mensaje enviado o recibido
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBarSection(
                isConnected = uiState.isConnected,
                connectedRemoteIp = uiState.connectedRemoteIp
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // 1. TARJETA DE CONEXIÓN POR IP Y MOSTRADOR DE ROL
            ConnectionCardSection(
                localIp = uiState.localIp,
                remoteIpInput = uiState.remoteIpInput,
                ipError = uiState.ipError,
                isConnecting = uiState.isConnecting,
                myRole = uiState.myRole,
                onRemoteIpChanged = onRemoteIpChanged,
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
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Limpiar Historial",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Limpiar historial", fontSize = 13.sp)
                }
            }

            HorizontalDivider()

            // 4. LISTA DE CONVERSACIÓN (LazyColumn)
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
                                    myRole = uiState.myRole
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

    // --------------------------------------------------------------------------------
    // DIÁLOGOS DE ALERTA DE LA APLICACIÓN
    // --------------------------------------------------------------------------------

    // Diálogo 1: Alerta de Conexión Establecida o Error de Red
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

    // Diálogo 2: Confirmación antes de limpiar el historial
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
 * COMPOSABLE: Barra superior (TopAppBar) con el título de la app e indicador de estado de red.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarSection(
    isConnected: Boolean,
    connectedRemoteIp: String?
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
                    // Círculo de estado verde (conectado) o rojo (desconectado)
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
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}

/**
 * COMPOSABLE: Tarjeta de conexión por IP con detección automática e ingreso de la IP remota.
 */
@Composable
fun ConnectionCardSection(
    localIp: String,
    remoteIpInput: String,
    ipError: String?,
    isConnecting: Boolean,
    myRole: Role,
    onRemoteIpChanged: (String) -> Unit,
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
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // Mi IP local detectada de forma automática
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
                        modifier = Modifier.size(20.dp)
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
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refrescar IP local",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Campo de texto para ingresar la IP del otro dispositivo
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = remoteIpInput,
                    onValueChange = onRemoteIpChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text("IP del otro dispositivo") },
                    placeholder = { Text("Ej: 192.168.1.15") },
                    singleLine = true,
                    isError = ipError != null,
                    supportingText = if (ipError != null) {
                        { Text(text = ipError, color = MaterialTheme.colorScheme.error) }
                    } else null,
                    shape = RoundedCornerShape(10.dp)
                )

                Button(
                    onClick = onConnectClicked,
                    enabled = !isConnecting,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(56.dp)
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Conectar")
                    }
                }
            }

            // Muestra del rol del dispositivo
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
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (myRole == Role.NONE) Icons.Default.Info else Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
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

/**
 * COMPOSABLE: Muestra los contadores globales de mensajes enviados por cada dispositivo.
 */
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
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DeviceCounterBadge(
                deviceName = "Dispositivo A",
                count = counterA,
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )

            Text(
                text = "Total: $totalMessages",
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

/**
 * COMPOSABLE: Insignia de conteo individual de mensajes.
 */
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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

/**
 * COMPOSABLE: Vista vacía cuando no hay mensajes en la conversación.
 */
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
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No hay mensajes en el historial.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            Text(
                text = "Ingresa la IP del otro dispositivo, conecta y escribe un mensaje para iniciar.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

/**
 * COMPOSABLE: Burbuja de conversación individual.
 * Mis mensajes se posicionan a la derecha y los remotos a la izquierda.
 */
@Composable
fun MessageBubbleView(
    message: Message,
    myRole: Role
) {
    // Si mi rol coincide con el senderRole del mensaje, el mensaje es propio (se dibuja a la derecha)
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
                .padding(12.dp)
        ) {
            // Emisor (Dispositivo A o Dispositivo B)
            Text(
                text = message.senderRole.shortName(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = textColor.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Contenido del mensaje
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Hora de envío en formato HH:mm
            Text(
                text = message.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

/**
 * COMPOSABLE: Barra inferior de redacción y envío de mensajes con validación.
 */
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

        // Indicador de caracteres restantes (máximo 200)
        Text(
            text = "${messageInput.length} / 200 caracteres",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.align(Alignment.End)
        )
    }
}
