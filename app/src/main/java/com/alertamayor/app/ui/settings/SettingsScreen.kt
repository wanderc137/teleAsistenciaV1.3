package com.alertamayor.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alertamayor.app.R
import com.alertamayor.app.domain.model.AppMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var serverUrl by remember { mutableStateOf(state.serverUrl) }
    var turnUrl by remember { mutableStateOf(state.turnUrl) }
    var turnUsername by remember { mutableStateOf(state.turnUsername) }
    var turnPassword by remember { mutableStateOf(state.turnPassword) }
    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // V1.3: selector de idioma Español ⇄ Inglés (al inicio de la configuración)
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            ModeRadio(
                label = stringResource(R.string.settings_language_spanish),
                selected = state.language == "es",
                onClick = { viewModel.saveLanguage("es") }
            )
            ModeRadio(
                label = stringResource(R.string.settings_language_english),
                selected = state.language == "en",
                onClick = { viewModel.saveLanguage("en") }
            )

            Spacer(Modifier.height(24.dp))

            Text(stringResource(R.string.settings_mode), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            ModeRadio(
                label = stringResource(R.string.mode_supervised_title),
                selected = state.mode == AppMode.SUPERVISED,
                onClick = { viewModel.saveMode(AppMode.SUPERVISED) }
            )
            ModeRadio(
                label = stringResource(R.string.mode_supervisor_title),
                selected = state.mode == AppMode.SUPERVISOR,
                onClick = { viewModel.saveMode(AppMode.SUPERVISOR) }
            )

            Spacer(Modifier.height(24.dp))

            // M-001 (ciclo 6): selector de tema dark/light
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            ModeRadio(
                label = stringResource(R.string.theme_system),
                selected = state.theme == "system",
                onClick = { viewModel.saveTheme("system") }
            )
            ModeRadio(
                label = stringResource(R.string.theme_light),
                selected = state.theme == "light",
                onClick = { viewModel.saveTheme("light") }
            )
            ModeRadio(
                label = stringResource(R.string.theme_dark),
                selected = state.theme == "dark",
                onClick = { viewModel.saveTheme("dark") }
            )

            Spacer(Modifier.height(24.dp))

            Text(stringResource(R.string.settings_server_url), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text(stringResource(R.string.settings_server_url_hint)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            // I-003 (ciclo 3): aviso de que la IP debe estar en la allowlist cleartext.
            Text(
                text = stringResource(R.string.settings_server_url_cleartext_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.saveServerUrl(serverUrl.trim()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_save))
            }

            Spacer(Modifier.height(24.dp))

            // Servidor TURN (necesario para videollamadas en redes móviles / 4G)
            Text(stringResource(R.string.settings_turn_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_turn_help),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = turnUrl,
                onValueChange = { turnUrl = it },
                label = { Text(stringResource(R.string.settings_turn_url_hint)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = turnUsername,
                onValueChange = { turnUsername = it },
                label = { Text(stringResource(R.string.settings_turn_username_hint)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = turnPassword,
                onValueChange = { turnPassword = it },
                label = { Text(stringResource(R.string.settings_turn_password_hint)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.saveTurnConfig(turnUrl, turnUsername, turnPassword) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_turn_save))
            }

            Spacer(Modifier.height(24.dp))

            Text(stringResource(R.string.settings_link), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_device_id, state.deviceId),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )
            if (state.linkedPeerId.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.settings_linked_to,
                        "${state.linkedPeerName} (${state.linkedPeerId})"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_reset_link))
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.settings_reset_link)) },
            text = { Text(stringResource(R.string.settings_reset_link_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetLinkAndDeviceId()
                    showResetDialog = false
                }) { Text(stringResource(R.string.settings_reset_link)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.settings_back)) }
            }
        )
    }
}

@Composable
private fun ModeRadio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
