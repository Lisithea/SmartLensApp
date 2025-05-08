package com.example.smartlens.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_profile")

/**
 * Gestor del perfil de usuario para almacenar y recuperar información personalizada
 */
@Singleton
class UserProfileManager @Inject constructor(
    private val context: Context
) {
    private val userNameKey = stringPreferencesKey("user_name")
    private val userEmailKey = stringPreferencesKey("user_email")
    private val userJoinDateKey = stringPreferencesKey("user_join_date")
    private val userProfilePictureKey = stringPreferencesKey("user_profile_picture")

    /**
     * Obtiene el nombre del usuario actual
     */
    val userName: Flow<String> = context.userDataStore.data
        .map { preferences ->
            preferences[userNameKey] ?: "Usuario"
        }

    /**
     * Obtiene el correo electrónico del usuario
     */
    val userEmail: Flow<String> = context.userDataStore.data
        .map { preferences ->
            preferences[userEmailKey] ?: ""
        }

    /**
     * Obtiene la fecha de registro del usuario
     */
    val userJoinDate: Flow<String> = context.userDataStore.data
        .map { preferences ->
            preferences[userJoinDateKey] ?: "Mayo 2025"
        }

    /**
     * Obtiene la URI de la imagen de perfil (si existe)
     */
    val userProfilePicture: Flow<String> = context.userDataStore.data
        .map { preferences ->
            preferences[userProfilePictureKey] ?: ""
        }

    /**
     * Guarda la información del perfil del usuario
     */
    suspend fun saveUserProfile(
        name: String,
        email: String,
        joinDate: String,
        profilePicture: String? = null
    ) {
        context.userDataStore.edit { preferences ->
            preferences[userNameKey] = name
            preferences[userEmailKey] = email
            preferences[userJoinDateKey] = joinDate
            if (profilePicture != null) {
                preferences[userProfilePictureKey] = profilePicture
            }
        }
    }

    /**
     * Actualiza solo el nombre del usuario
     */
    suspend fun updateUserName(name: String) {
        context.userDataStore.edit { preferences ->
            preferences[userNameKey] = name
        }
    }

    /**
     * Actualiza solo el correo electrónico del usuario
     */
    suspend fun updateUserEmail(email: String) {
        context.userDataStore.edit { preferences ->
            preferences[userEmailKey] = email
        }
    }

    /**
     * Actualiza solo la imagen de perfil del usuario
     */
    suspend fun updateUserProfilePicture(profilePicture: String) {
        context.userDataStore.edit { preferences ->
            preferences[userProfilePictureKey] = profilePicture
        }
    }

    /**
     * Genera un saludo personalizado basado en la hora del día y el nombre del usuario
     */
    fun getPersonalizedGreeting(userName: String): String {
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

        return when {
            currentHour < 12 -> "¡Buenos días, $userName!"
            currentHour < 19 -> "¡Buenas tardes, $userName!"
            else -> "¡Buenas noches, $userName!"
        }
    }
}