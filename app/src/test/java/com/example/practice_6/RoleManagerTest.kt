package com.example.practice_6

import com.example.practice_6.domain.RoleManager
import com.example.practice_6.model.Message
import com.example.practice_6.model.Role
import com.example.practice_6.network.NetworkUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias para validar las reglas de negocio de [RoleManager] y [NetworkUtils].
 */
class RoleManagerTest {

    @Test
    fun `primer mensaje asigna rol A al emisor`() {
        val roleManager = RoleManager(Role.NONE)
        val role = roleManager.onSendingFirstMessage()
        assertEquals(Role.A, role)
        assertEquals(Role.A, roleManager.currentRole)
    }

    @Test
    fun `mensaje recibido en estado NONE asigna rol B`() {
        val roleManager = RoleManager(Role.NONE)
        val changed = roleManager.onReceivingMessage(Role.A)
        assertTrue(changed)
        assertEquals(Role.B, roleManager.currentRole)
    }

    @Test
    fun `handshake cuando nadie tiene historial deja rol en NONE`() {
        val roleManager = RoleManager(Role.NONE)
        val result = roleManager.processHandshake(
            remoteRoleStr = "NONE",
            remoteHasHistory = false,
            localHasHistory = false
        )
        assertEquals(Role.NONE, result.assignedRole)
    }

    @Test
    fun `handshake cuando solo remoto tiene historial asigna rol opuesto`() {
        val roleManager = RoleManager(Role.NONE)
        val result = roleManager.processHandshake(
            remoteRoleStr = "A",
            remoteHasHistory = true,
            localHasHistory = false
        )
        assertEquals(Role.B, result.assignedRole)
        assertEquals(Role.B, roleManager.currentRole)
    }

    @Test
    fun `limpiar historial reinicia el rol a NONE`() {
        val roleManager = RoleManager(Role.A)
        roleManager.onHistoryCleared()
        assertEquals(Role.NONE, roleManager.currentRole)
    }

    @Test
    fun `validacion IP rechaza formatos invalidos e IP propia`() {
        val localIp = "192.168.1.10"

        // IP propia
        val resOwn = NetworkUtils.validateIpAddress("192.168.1.10", localIp)
        assertFalse(resOwn.isValid)

        // IP con letras
        val resAlpha = NetworkUtils.validateIpAddress("192.168.1.abc", localIp)
        assertFalse(resAlpha.isValid)

        // IP fuera de rango
        val resRange = NetworkUtils.validateIpAddress("192.168.1.300", localIp)
        assertFalse(resRange.isValid)

        // IP valida
        val resValid = NetworkUtils.validateIpAddress("192.168.1.20", localIp)
        assertTrue(resValid.isValid)
    }

    @Test
    fun `resolucion mensaje simultaneo da victoria a timestamp mas antiguo`() {
        val roleManager = RoleManager(Role.NONE)
        val msgLocal = Message(text = "Hola de A", timestamp = "10:00", senderRole = Role.A)
        val msgRemote = Message(text = "Hola de B", timestamp = "10:05", senderRole = Role.A)

        val keepsA = roleManager.resolveSimultaneousFirstMessage(
            localMsg = msgLocal,
            remoteMsg = msgRemote,
            localIp = "192.168.1.15",
            remoteIp = "192.168.1.20"
        )
        assertTrue(keepsA)
        assertEquals(Role.A, roleManager.currentRole)
    }
}
