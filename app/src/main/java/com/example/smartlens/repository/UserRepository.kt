package com.example.smartlens.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_preferences")

@Singleton
class UserRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "UserRepository"
    private val dataStore = context.authDataStore

    companion object {
        private val USER_EMAIL_KEY = stringPreferencesKey("user_email")
        private val USER_PASSWORD_KEY = stringPreferencesKey("user_password")
        private val LOGGED_IN_KEY = booleanPreferencesKey("logged_in")
        private val SESSION_TIMESTAMP_KEY = stringPreferencesKey("session_timestamp")
        private val USER_DISPLAY_NAME_KEY = stringPreferencesKey("user_display_name")
        private val SESSION_TOKEN_KEY = stringPreferencesKey("session_token")

        // Lista de usuarios válidos (esto se reemplazaría con una API real)
        private val VALID_USERS = mapOf(
            "admin@smartlens.com" to Pair("admin123", "Administrador"),
            "usuario@smartlens.com" to Pair("usuario123", "Usuario"),
            "demo@smartlens.com" to Pair("demo123", "Usuario Demo")
        )

        // Tiempo de expiración de sesión: 24 horas
        private const val SESSION_EXPIRATION_TIME = 24 * 60 * 60 * 1000L // 24 horas en milisegundos
    }

    /**
     * Verifica si un usuario está logueado y la sesión está activa
     */
    suspend fun isUserLoggedIn(): Boolean {
        try {
            val preferences = dataStore.data.first()
            val isLoggedIn = preferences[LOGGED_IN_KEY] ?: false

            if (!isLoggedIn) {
                return false
            }

            // Verificar si la sesión ha expirado
            val sessionTimestamp = preferences[SESSION_TIMESTAMP_KEY]?.toLongOrNull() ?: 0L
            val currentTime = System.currentTimeMillis()

            if (currentTime - sessionTimestamp > SESSION_EXPIRATION_TIME) {
                // La sesión ha expirado, cerrar sesión silenciosamente
                Log.d(TAG, "Sesión expirada, cerrando sesión")
                logout()
                return false
            }

            // Sesión válida
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error al verificar estado de sesión: ${e.message}", e)
            return false
        }
    }

    /**
     * Obtiene el email del usuario actual
     */
    suspend fun getCurrentUserEmail(): String {
        return dataStore.data.map { preferences ->
            preferences[USER_EMAIL_KEY] ?: ""
        }.first()
    }

    /**
     * Obtiene el nombre de visualización del usuario actual
     */
    suspend fun getCurrentUserDisplayName(): String {
        return dataStore.data.map { preferences ->
            preferences[USER_DISPLAY_NAME_KEY] ?: "Usuario"
        }.first()
    }

    /**
     * Obtiene el token de sesión
     */
    suspend fun getSessionToken(): String? {
        return dataStore.data.map { preferences ->
            preferences[SESSION_TOKEN_KEY]
        }.firstOrNull()
    }

    /**
     * Intenta autenticar a un usuario
     */
    suspend fun login(email: String, password: String): Boolean {
        // Simulamos una pequeña demora para que parezca que estamos haciendo una petición
        delay(1000)

        // Verificamos las credenciales
        val userInfo = VALID_USERS[email]
        val isValid = userInfo != null && userInfo.first == password

        if (isValid && userInfo != null) {
            // Guardamos los datos del usuario autenticado
            saveUserCredentials(email, password, userInfo.second)
            return true
        }

        return false
    }

    /**
     * Guarda las credenciales del usuario
     */
    private suspend fun saveUserCredentials(email: String, password: String, displayName: String) {
        val sessionToken = generateSessionToken(email)
        val timestamp = System.currentTimeMillis()

        dataStore.edit { preferences ->
            preferences[USER_EMAIL_KEY] = email
            preferences[USER_PASSWORD_KEY] = password // En un sistema real no guardaríamos passwords
            preferences[LOGGED_IN_KEY] = true
            preferences[SESSION_TIMESTAMP_KEY] = timestamp.toString()
            preferences[USER_DISPLAY_NAME_KEY] = displayName
            preferences[SESSION_TOKEN_KEY] = sessionToken
        }

        Log.d(TAG, "Credenciales guardadas para: $email")
    }

    /**
     * Cierra la sesión del usuario actual
     */
    suspend fun logout() {
        dataStore.edit { preferences ->
            preferences[LOGGED_IN_KEY] = false
            preferences[SESSION_TOKEN_KEY] = ""
        }

        Log.d(TAG, "Sesión cerrada")
    }

    /**
     * Actualiza el timestamp de la sesión para mantenerla activa
     */
    suspend fun refreshSession() {
        if (isUserLoggedIn()) {
            val timestamp = System.currentTimeMillis()
            dataStore.edit { preferences ->
                preferences[SESSION_TIMESTAMP_KEY] = timestamp.toString()
            }
            Log.d(TAG, "Sesión actualizada: $timestamp")
        }
    }

    /**
     * Genera un token de sesión simple (en un sistema real sería más complejo)
     */
    private fun generateSessionToken(email: String): String {
        val timestamp = System.currentTimeMillis()
        val random = (0..9999).random()
        return "$email-$timestamp-$random"
    }
}