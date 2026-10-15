# Guía de Pruebas y Redir de Emuladores - MobileConnect
**Práctica 6: Interacción entre Dispositivos Móviles**

Esta guía explica paso a paso cómo ejecutar y validar la aplicación **MobileConnect** en distintos entornos de prueba, así como los casos de prueba requeridos para la captura de evidencia.

---

## 1. Guía de Configuración y Pruebas de Red

### Escenario A: Dos teléfonos físicos reales en la misma red Wi-Fi
1. Conecta ambos teléfonos a la misma red Wi-Fi local.
2. Abre **MobileConnect** en ambos dispositivos.
3. Observa la "Mi IP (Auto)" detectada automáticamente en la tarjeta de conexión de cada teléfono (por ejemplo, `192.168.1.15` en el Teléfono 1 y `192.168.1.20` en el Teléfono 2).
4. En el Teléfono 1, escribe en el campo "IP del otro dispositivo" la IP del Teléfono 2 (`192.168.1.20`) y presiona **Conectar**.
5. Ambos teléfonos mostrarán una alerta `AlertDialog` confirmando: *"Conexión establecida con 192.168.1.20"*.
6. Escribe un mensaje desde el Teléfono 1 y presiona **Enviar**. Automáticamente el Teléfono 1 se asignará como **Dispositivo A** y el Teléfono 2 recibirá el mensaje asignándose como **Dispositivo B**.

---

### Escenario B: Un teléfono físico real + Un emulador Android en PC
1. Conecta el teléfono físico al Wi-Fi de la misma red local en la que se encuentra la PC con Android Studio.
2. Abre la app en el teléfono físico y anota su IP local (ej: `192.168.1.15`).
3. Inicia la app en el emulador de Android Studio. En los emuladores, la interfaz de red emulada siempre muestra la IP virtual `10.0.2.15`.
4. En el teléfono físico, escribe la IP local de tu computadora en la red Wi-Fi (ej: `192.168.1.10`) o en el emulador escribe la IP del teléfono físico (`192.168.1.15`).
5. Presiona **Conectar** en cualquiera de los dos.

---

### Escenario C: Dos emuladores de Android Studio en la misma PC (Redirección con ADB)
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
   - En el **Emulador 1 (emulator-5554)**: En el campo "IP del otro dispositivo", escribe la dirección especial del host virtual `10.0.2.2` o `127.0.0.1` y presiona **Conectar**.
   - En el **Emulador 2 (emulator-5556)**: Recibirá la conexión entrante automáticamente sin necesidad de escribir la IP, o puede conectar hacia `10.0.2.2`.

---

## 2. Casos de Prueba para Capturas de Pantalla (Evidencia)

A continuación se detallan los 9 casos de prueba para tomar capturas e incluirlas en el reporte de la práctica:

| # | Caso de Prueba | Descripción de la prueba | Resultado Esperado |
|---|---|---|---|
| **1** | **Interfaz ejecutada e IP detectada** | Iniciar la app en un dispositivo sin conexión previa. | Muestra la TopAppBar, la tarjeta de conexión con "Mi IP (Auto)" detectada (ej: `192.168.1.15`) y "Mi rol: Por definir". |
| **2** | **IP inválida rechazada** | Escribir un texto no válido en la IP (ej: `192.168.1.abc` o `192.168.1.300` o la IP propia). | El campo muestra estilo de error (`isError`), mensaje explicativo en `supportingText` y NO intenta conectar. |
| **3** | **Alerta de conexión exitosa** | Ingresar la IP del otro dispositivo y presionar "Conectar". | Aparece un `AlertDialog` en ambos dispositivos: *"Conexión establecida con <IP>"*. |
| **4** | **Mensajes enviados desde ambos** | Enviar un mensaje desde el Dispositivo A y responder desde el Dispositivo B. | Las burbujas se muestran alineadas a la derecha para mensajes propios y a la izquierda para recibidos con colores contrastantes. |
| **5** | **Validación de mensaje vacío** | Intentar presionar "Enviar" con el campo en blanco o solo espacios. | El botón se deshabilita o muestra mensaje de error: *"El mensaje no puede estar vacío"*. |
| **6** | **Contadores actualizados** | Enviar 2 mensajes desde A y 1 desde B. | La tarjeta de contadores refleja exactamente: *"Dispositivo A: 2 | Dispositivo B: 1 | Total: 3"*. |
| **7** | **Limpiar historial** | Presionar el botón "Limpiar historial" y confirmar en el diálogo. | Aparece el `AlertDialog` de confirmación. Al aceptar, la lista de mensajes se vacía y los contadores vuelven a 0. |
| **8** | **Escenario Roles: Ambos limpian** | Ambos dispositivos ejecutan la limpieza de historial. | Los roles de ambos dispositivos vuelven a "Por definir". El siguiente primer mensaje volverá a asignar quien es A. |
| **9** | **Escenario Roles: Solo uno limpia** | Solo el Dispositivo B limpia historial. Al reconectarse (handshake `HELLO`), el Dispositivo A mantiene "Eres el Dispositivo A". | El dispositivo que limpió recibe automáticamente el rol restante ("Eres el Dispositivo B") al conectarse con A. |
