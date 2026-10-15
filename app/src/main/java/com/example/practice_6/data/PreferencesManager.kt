package com.example.practice_6.data

import android.content.Context
import android.content.SharedPreferences
import com.example.practice_6.model.Message
import com.example.practice_6.model.Role
import org.json.JSONArray

/**
 * Gestor de persistencia local mediante [SharedPreferences].
 *
 * Almacena el historial de mensajes en formato JSON, el rol asignado
 * y la última IP registrada del dispositivo remoto sin recurrir a Room.
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mobile_connect_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ROLE = "key_assigned_role"
        private const val KEY_REMOTE_IP = "key_remote_ip"
        private const val KEY_HISTORY = "key_message_history"
    }

    /**
     * Guarda el rol actual en almacenamiento local.
     */
    fun saveRole(role: Role) {
        prefs.edit().putString(KEY_ROLE, role.name).apply()
    }

    /**
     * Carga el rol persistido localmente. Devuelve [Role.NONE] si no hay ninguno.
     */
    fun loadRole(): Role {
        val roleStr = prefs.getString(KEY_ROLE, Role.NONE.name)
        return Role.fromString(roleStr)
    }

    /**
     * Guarda la última IP registrada del dispositivo remoto.
     */
    fun saveRemoteIp(ip: String) {
        prefs.edit().putString(KEY_REMOTE_IP, ip).apply()
    }

    /**
     * Carga la última IP remota guardada.
     */
    fun loadRemoteIp(): String {
        return prefs.getString(KEY_REMOTE_IP, "") ?: ""
    }

    /**
     * Guarda la lista completa de mensajes de la conversación serializada como un [JSONArray].
     */
    fun saveHistory(messages: List<Message>) {
        val jsonArray = JSONArray()
        for (message in messages) {
            jsonArray.put(message.toJsonObject())
        }
        prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }

    /**
     * Carga el historial de conversación guardado previamente.
     */
    fun loadHistory(): List<Message> {
        val jsonStr = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val list = mutableListOf<Message>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(Message.fromJsonObject(obj))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    /**
     * Limpia el historial de mensajes y reinicia el rol a [Role.NONE].
     * Se mantiene la IP remota registrada para conveniencia del usuario.
     */
    fun clearHistoryAndRole() {
        prefs.edit()
            .remove(KEY_HISTORY)
            .putString(KEY_ROLE, Role.NONE.name)
            .apply()
    }
}
