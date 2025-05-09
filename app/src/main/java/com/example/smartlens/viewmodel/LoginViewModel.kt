package com.example.smartlens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartlens.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {
    private val TAG = "LoginViewModel"

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage

    // Estado para mantener el email del usuario actual
    private val _currentUserEmail = MutableStateFlow("")
    val currentUserEmail: StateFlow<String> = _currentUserEmail

    init {
        // Verificar si el usuario ya está autenticado
        viewModelScope.launch {
            try {
                Log.d(TAG, "Verificando autenticación...")
                val isLoggedIn = userRepository.isUserLoggedIn()
                _isAuthenticated.value = isLoggedIn

                if (isLoggedIn) {
                    _currentUserEmail.value = userRepository.getCurrentUserEmail()
                    Log.d(TAG, "Usuario autenticado: ${_currentUserEmail.value}")
                } else {
                    Log.d(TAG, "Usuario no autenticado")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al verificar autenticación: ${e.message}", e)
                _errorMessage.value = "Error al verificar la sesión: ${e.message}"
            }
        }
    }

    /**
     * Función para iniciar sesión
     */
    suspend fun login(email: String, password: String) {
        try {
            _isLoading.value = true
            _errorMessage.value = ""

            Log.d(TAG, "Iniciando sesión con email: $email")

            // Aquí llamamos al repositorio para autenticar
            val result = userRepository.login(email, password)

            if (result) {
                _isAuthenticated.value = true
                _currentUserEmail.value = email
                Log.d(TAG, "Inicio de sesión exitoso")
            } else {
                _errorMessage.value = "Credenciales incorrectas"
                Log.d(TAG, "Inicio de sesión fallido: credenciales incorrectas")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en inicio de sesión: ${e.message}", e)
            _errorMessage.value = "Error de conexión: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Función para cerrar sesión
     */
    fun logout() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Cerrando sesión...")
                userRepository.logout()
                _isAuthenticated.value = false
                _currentUserEmail.value = ""
                _errorMessage.value = ""
                Log.d(TAG, "Sesión cerrada correctamente")
            } catch (e: Exception) {
                Log.e(TAG, "Error al cerrar sesión: ${e.message}", e)
                _errorMessage.value = "Error al cerrar sesión: ${e.message}"
            }
        }
    }

    /**
     * Verifica si hay una sesión activa
     */
    fun checkAuthentication() {
        viewModelScope.launch {
            try {
                val isLoggedIn = userRepository.isUserLoggedIn()
                _isAuthenticated.value = isLoggedIn

                if (isLoggedIn) {
                    _currentUserEmail.value = userRepository.getCurrentUserEmail()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al verificar autenticación: ${e.message}", e)
            }
        }
    }

    /**
     * Limpia los mensajes de error
     */
    fun clearErrorMessage() {
        _errorMessage.value = ""
    }

    /**
     * Función para login rápido en modo demostración
     */
    fun loginDemo() {
        viewModelScope.launch {
            login("demo@smartlens.com", "demo123")
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "LoginViewModel destruido")
    }
}