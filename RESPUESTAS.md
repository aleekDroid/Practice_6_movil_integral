# Cuestionario de Evaluación - MobileConnect
**Práctica 6: Interacción entre dispositivos móviles**

---

### 1. ¿Qué significa la interacción entre dispositivos móviles?
La interacción entre dispositivos móviles hace referencia a la capacidad de dos o más dispositivos independientes (smartphones o emuladores) para intercambiar datos, eventos y estados a través de un medio de transmisión físico o inalámbrico. En **MobileConnect**, esto se traduce en la capacidad de dos instancias de la app para comunicarse de punto a punto (peer-to-peer) sobre la red local IPv4, sincronizando mensajes de chat y asignando roles automáticamente sin intervención humana.

---

### 2. ¿Qué tecnologías permiten intercambiar información entre dispositivos?
Existen diversas tecnologías de comunicación según el alcance y arquitectura:
- **Redes TCP/IP (Sockets):** Comunicación bidireccional punto a punto en redes LAN/Wi-Fi mediante sockets TCP (`ServerSocket` / `Socket`), como la empleada en esta práctica en el puerto 8888.
- **Bluetooth / Bluetooth Low Energy (BLE):** Conexión directa de corto alcance sin requerir infraestructura de red.
- **Wi-Fi Direct / Nearby Connections:** Transferencia directa de alta velocidad entre dispositivos cercanos sin punto de acceso central.
- **WebSockets / HTTP REST API:** Comunicación clientes-servidor a través de un servidor web intermediario en la nube.

---

### 3. ¿Qué diferencia existe entre una simulación local y una comunicación real?
En una **simulación local**, el intercambio de datos ocurre dentro de un mismo proceso o memoria en una sola pantalla, alternando manualmente entre remitentes mediante variables de estado. En cambio, en una **comunicación real** (como MobileConnect), cada dispositivo ejecuta su propio proceso independiente en hardware o emulador distinto, transmitiendo paquetes de red reales (JSON sobre TCP) mediante direcciones IP únicas, lo que requiere manejar latencias, timeouts, pérdidas de conexión y sincronización de estado distribuido.

---

### 4. ¿Qué función cumple el estado de la aplicación en Jetpack Compose?
En Jetpack Compose, el estado (`UiState` expuesto como `StateFlow` desde el `ViewModel`) representa la "fuente única de la verdad" de la interfaz gráfica. Compose utiliza un enfoque declarativo donde la UI reacciona automáticamente y se recompone únicamente en los elementos afectados cuando el estado cambia (por ejemplo, al recibir un nuevo mensaje por el socket TCP o actualizar el rol a "Dispositivo A"). Esto garantiza la preservación de la interfaz frente a cambios de configuración como la rotación de pantalla.

---

### 5. ¿Por qué es importante validar los mensajes e IP antes de enviarlos?
La validación previa (verificar formatos IPv4 de 4 octetos de 0 a 255, descartar la IP propia y bloquear cadenas vacías o con puros espacios mediante `isBlank()`) es fundamental para mantener la integridad y estabilidad de la aplicación. Previene intentos infructuosos de conexión, evita fallos inesperados de red o excepciones `SocketException`, ahorra ancho de banda y recursos del sistema, y proporciona al usuario una retroalimentación visual clara mediante mensajes de error e indicadores visuales.

---

### 6. ¿Qué cambios serían necesarios para implementar comunicación real mediante Bluetooth o Wi-Fi Direct?
Para migrar de sockets TCP por IP local a **Bluetooth**, se debería reemplazar `ServerSocket` por `BluetoothServerSocket` y `BluetoothSocket`, solicitando los permisos `BLUETOOTH_CONNECT` y `BLUETOOTH_SCAN`, además de agregar un flujo de descubrimiento de dispositivos mediante direcciones MAC. Para **Wi-Fi Direct**, se utilizaría la API `WifiP2pManager` para descubrir pares y establecer el grupo P2P, tras lo cual se obtendría la IP del propietario del grupo para abrir los sockets TCP existentes. La capa de presentación y la lógica de roles (`RoleManager`) se mantendrían idénticas.

---

### 7. ¿Qué ventajas tendría utilizar un servidor intermediario para intercambiar mensajes?
Un **servidor intermediario** (arquitectura Cliente-Servidor o MQTT/Broker) ofrece ventajas clave respecto a la comunicación Socket direct P2P:
1. **Superación de NAT y Firewalls:** Permite a los dispositivos comunicarse sin importar si están en redes Wi-Fi o de datos móviles distintas.
2. **Mensajería Asíncrona (Offline Storage):** Guarda mensajes en base de datos cuando el destinatario está desconectado y se los entrega al reconectarse.
3. **Escalabilidad y Seguridad:** Facilita autenticación de usuarios, encriptación centralizada y chats grupales masivos.
