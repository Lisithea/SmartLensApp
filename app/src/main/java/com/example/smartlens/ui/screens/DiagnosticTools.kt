package com.example.smartlens.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.smartlens.R
import com.example.smartlens.ui.navigation.Screen
import com.example.smartlens.ui.navigation.navigateToAdvancedDiagnostic

/**
 * Componente de herramientas de diagnóstico para la pantalla de Configuración
 */
@Composable
fun DiagnosticTools(navController: NavController) {
    // Sección de herramientas de diagnóstico
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Encabezado
            Text(
                text = "Herramientas de Diagnóstico",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Diagnóstico básico
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(Screen.Diagnostic.route) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Diagnóstico Básico",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Pruebas simples de OCR",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null
                )
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // Diagnóstico avanzado
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigateToAdvancedDiagnostic() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Diagnóstico Avanzado",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Clasificación y análisis inteligente",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null
                )
            }
        }
    }
}