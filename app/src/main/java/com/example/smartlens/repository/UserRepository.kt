package com.example.smartlens.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_preferences")

@Singleton
class UserRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.authDataStore

    companion object {
        private val USER_EMAIL_KEY = stringPreferencesKey("user_email")
        private val USER_PASSWORD_KEY = stringPreferencesKey("user_password")
        private val LOGGED_IN_KEY = booleanPreferencesKey("logged_in")

        // Lista de usuarios válidos (esto se reemplazaría con una API real)
        private val VALID_USERS = mapOf(
            "admin@smartlens.com" to "admin123",
            "usuario@smartlens.com" to "usuario123",
            "demo@smartlens.com" to "demo123"
        )
    }

    /**
     * Verifica si un usuario está logueado
     */
    suspend fun isUserLoggedIn(): Boolean {
        return dataStore.data.map { preferences ->
            preferences[LOGGED_IN_KEY] ?: false
        }.first()
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
     * Intenta autenticar a un usuario
     */
    suspend fun login(email: String, password: String): Boolean {
        // Simulamos una pequeña demora para que parezca que estamos haciendo una petición
        delay(1000)

        // Verificamos las credenciales
        val isValid = VALID_USERS[email] == password

        if (isValid) {
            // Guardamos los datos del usuario autenticado
            saveUserCredentials(email, password)
        }

        return isValid
    }

    /**
     * Guarda las credenciales del usuario
     */
    private suspend fun saveUserCredentials(email: String, password: String) {
        dataStore.edit { preferences ->
            preferences[USER_EMAIL_KEY] = email
            preferences[USER_PASSWORD_KEY] = password
            preferences[LOGGED_IN_KEY] = true
        }
    }

    /**
     * Cierra la sesión del usuario actual
     */
    suspend fun logout() {
        dataStore.edit { preferences ->
            preferences[LOGGED_IN_KEY] = false
        }
    }
}