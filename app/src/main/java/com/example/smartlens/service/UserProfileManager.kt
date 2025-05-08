package com.example.smartlens.service

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestiona el perfil del usuario actual
 */
@Singleton
class UserProfileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val preferences: SharedPreferences = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)

    // Estado del nombre del usuario
    private val _userName = MutableStateFlow(getUserName())
    val userName: StateFlow<String> = _userName

    // Estado de la fecha de ingreso
    private val _joinDate = MutableStateFlow(getJoinDate())
    val joinDate: StateFlow<String> = _joinDate

    // Estado del correo electrónico
    private val _email = MutableStateFlow(getEmail())
    val email: StateFlow<String> = _email

    /**
     * Obtiene el nombre del usuario guardado
     */
    fun getUserName(): String {
        return preferences.getString("user_name", "Usuario") ?: "Usuario"
    }

    /**
     * Guarda el nombre del usuario
     */
    fun saveUserName(name: String) {
        preferences.edit().putString("user_name", name).apply()
        _userName.value = name
    }

    /**
     * Obtiene la fecha de ingreso
     */
    fun getJoinDate(): String {
        return preferences.getString("join_date", "01/01/2024") ?: "01/01/2024"
    }

    /**
     * Guarda la fecha de ingreso
     */
    fun saveJoinDate(date: String) {
        preferences.edit().putString("join_date", date).apply()
        _joinDate.value = date
    }

    /**
     * Obtiene el correo del usuario
     */
    fun getEmail(): String {
        return preferences.getString("email", "") ?: ""
    }

    /**
     * Guarda el correo del usuario
     */
    fun saveEmail(email: String) {
        preferences.edit().putString("email", email).apply()
        _email.value = email
    }

    /**
     * Retorna un saludo personalizado según la hora del día
     */
    fun getGreeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

        return when {
            hour < 12 -> "¡Buenos días, ${getUserName()}!"
            hour < 20 -> "¡Buenas tardes, ${getUserName()}!"
            else -> "¡Buenas noches, ${getUserName()}!"
        }
    }
}