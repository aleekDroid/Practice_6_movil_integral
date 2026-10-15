# Guía de Pruebas y Redir de Emuladores - MobileConnect
**Práctica 6: Interacción entre Dispositivos Móviles**

Esta guía explica paso a paso cómo ejecutar y validar la aplicación **MobileConnect** en distintos entornos de prueba, así como los casos de prueba requeridos para la captura de evidencia.

---

## 1. Guía de Configuración y Pruebas de Red

### Escenario A: Dos teléfonos físicos reales en la misma red Wi-Fi
1. Conecta ambos teléfonos a la misma red Wi-Fi local.
2. Abre **MobileConnect** en ambos dispositivos.
3. Observa la "Mi IP (Auto)" detectada automáticamente en la tarjeta de conexión de cada teléfono (por ejemplo, `192.168.1.15` en el Teléfono 1 y `192.168.1.20` en el Teléfono 2).
4. En el Teléfono 1, escribe en el campo "IP remota" la IP del Teléfono 2 (`192.168.1.20`) y el puerto `8888` (por defecto), luego presiona **Conectar**.
5. Ambos teléfonos mostrarán una alerta `AlertDialog` confirmando: *"Conexión establecida con 192.168.1.20"*.
6. Escribe un mensaje desde el Teléfono 1 y presiona **Enviar**. Automáticamente el Teléfono 1 se asignará como **Dispositivo A** y el Teléfono 2 recibirá el mensaje asignándose como **Dispositivo B**.

---

### Escenario B: Conexión mediante Hotspot / Punto de Acceso Móvil (Sin router Wi-Fi)
1. En el Teléfono 1, activa la opción **Punto de acceso Wi-Fi / Hotspot móvil**.
2. En el Teléfono 2, conéctate a la red Wi-Fi generada por el Teléfono 1.
3. Abre **MobileConnect** en ambos dispositivos.
   - El Teléfono 1 (Punto de acceso) detectará una IP de interfaz de hotspot (por ejemplo: `192.168.43.1` o `192.168.49.1`).
   - El Teléfono 2 detectará una IP asignada por DHCP (por ejemplo: `192.168.43.100`).
4. En el Teléfono 2, ingresa la IP del Teléfono 1 (`192.168.43.1`) y presiona **Conectar**.
5. ¡Listo! La conexión persistente TCP se establecerá inmediatamente sobre el hotspot personal.

---

### Escenario C: Dos emuladores de Android Studio en la misma PC (Redirección ADB)
Debido a la arquitectura de aislamiento de red de Android Studio, cada emulador se ejecuta detrás de un enrutador virtual y ve su propia dirección IP como `10.0.2.15`. Para permitir que dos emuladores se comuniquen directamente en el puerto TCP **8888**, se debe configurar un reenvío de puertos mediante herramientas ADB.

#### Pasos de redirección TCP con ADB:
1. Abre una consola de comandos (PowerShell / CMD) e identifica los emuladores activos:
   ```bash
   adb devices
   ```
   *Salida típica:*
   ```text
   List of devices attached
   emulator-5554    device
   emulator-5556    device
   ```

2. Configura la redirección del puerto TCP 8888 desde el emulador 1 hacia el emulador 2:
   ```bash
   adb -s emulator-5554 forward tcp:8888 tcp:8888
   adb -s emulator-5556 emu redir add tcp:8888:8888
   ```

3. **Direcciones IP a escribir en la app:**
   - En el **Emulador 1 (emulator-5554)**: En el campo "IP remota", escribe la dirección del host virtual `10.0.2.2` o `127.0.0.1` y presiona **Conectar**.
   - En el **Emulador 2 (emulator-5556)**: Recibirá la conexión entrante automáticamente sin necesidad de escribir la IP.

---

## 2. Casos de Prueba para Capturas de Pantalla (Evidencia)

A continuación se detallan los 10 casos de prueba para tomar capturas e incluirlas en el reporte de la práctica:

| # | Caso de Prueba | Descripción de la prueba | Resultado Esperado |
|---|---|---|---|
| **1** | **Interfaz ejecutada e IP detectada** | Iniciar la app en un dispositivo sin conexión previa. | Muestra la TopAppBar, la tarjeta de conexión con "Mi IP (Auto)" detectada (ej: `192.168.1.15`) y "Mi rol: Por definir". |
| **2** | **IP inválida rechazada** | Escribir un texto no válido en la IP (ej: `192.168.1.abc`, `192.168.1.300`, `0.0.0.0` o la IP propia). | El campo muestra estilo de error (`isError`), mensaje explicativo en `supportingText` y NO intenta conectar. |
| **3** | **Alerta de conexión exitosa** | Ingresar la IP del otro dispositivo y presionar "Conectar". | Aparece un `AlertDialog` en ambos dispositivos: *"Conexión establecida con <IP>"*. |
| **4** | **Panel de diagnóstico técnico** | Presionar el ícono del escarabajo (Bug) en la TopAppBar. | Se despliega el panel mostrando el estado del servidor TCP en `0.0.0.0:8888`, socket remoto activo, hora de última actividad y errores. |
| **5** | **Mensajes con confirmación ACK** | Enviar un mensaje desde el Dispositivo A y responder desde el Dispositivo B. | Muestra una palomita verde de entrega confirmada (ACK). Si falla la red, muestra un ícono rojo con opción "Reintentar". |
| **6** | **Validación de mensaje vacío** | Intentar presionar "Enviar" con el campo en blanco o solo espacios. | El botón permanece deshabilitado o muestra mensaje de error: *"El mensaje no puede estar vacío"*. |
| **7** | **Contadores de entregados** | Enviar 2 mensajes desde A y 1 desde B (ambos confirmados por ACK). | La tarjeta de contadores refleja exactamente: *"Dispositivo A: 2 \| Dispositivo B: 1 \| Total entregados: 3"*. |
| **8** | **Limpiar historial** | Presionar el botón "Limpiar historial" y confirmar en el diálogo. | Aparece el `AlertDialog` de confirmación. Al aceptar, la lista de mensajes se vacía y los contadores vuelven a 0. |
| **9** | **Escenario Roles: Ambos limpian** | Ambos dispositivos ejecutan la limpieza de historial. | Los roles de ambos dispositivos vuelven a "Por definir". El siguiente primer mensaje volverá a asignar quién es A. |
| **10** | **Escenario Roles: Solo uno limpia** | Solo el Dispositivo B limpia historial. Al reconectarse (handshake `HELLO`), el Dispositivo A mantiene "Eres el Dispositivo A". | El dispositivo que limpió recibe automáticamente el rol restante ("Eres el Dispositivo B") al conectarse con A. |
