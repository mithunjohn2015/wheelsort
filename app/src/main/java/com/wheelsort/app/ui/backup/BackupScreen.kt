package com.wheelsort.app.ui.backup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.wheelsort.app.ui.theme.ActionKeep

@Composable
fun BackupScreen(
    onExit: () -> Unit,
    viewModel: BackupViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup status") },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Checks which photos already exist on your own Immich server, using the same checksum comparison Immich's own apps use before uploading. Nothing is ever uploaded from here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = uiState.serverUrl,
                onValueChange = viewModel::updateServerUrl,
                label = { Text("Server URL") },
                placeholder = { Text("https://immich.example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = viewModel::updateApiKey,
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { viewModel.saveAndTestConnection() },
                    enabled = uiState.serverUrl.isNotBlank() && uiState.apiKey.isNotBlank()
                ) {
                    Text("Save & test connection")
                }
                Spacer(Modifier.width(12.dp))
                ConnectionStatusBadge(uiState.connectionStatus)
            }

            if (uiState.connectionStatus == ConnectionStatus.FAILURE && uiState.errorMessage != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    uiState.errorMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(20.dp))

            if (uiState.isChecking) {
                Column {
                    Text(
                        "Checking \u2014 this reads every photo, so it can take a while for large libraries.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    val progress = if (uiState.totalCount > 0) uiState.checkedCount / uiState.totalCount.toFloat() else 0f
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${uiState.checkedCount} of ${uiState.totalCount} checked",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Button(
                    onClick = { viewModel.startBackupCheck() },
                    enabled = uiState.isConfigured,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Check backup status")
                }
            }

            if (uiState.hasResults) {
                Spacer(Modifier.height(20.dp))
                val notBackedUpCount = uiState.notBackedUp.size
                Text(
                    "${uiState.backedUpCount} of ${uiState.checkedCount} photos are backed up",
                    style = MaterialTheme.typography.titleMedium
                )
                if (notBackedUpCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$notBackedUpCount not backed up yet:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(uiState.notBackedUp, key = { it.id }) { photo ->
                            AsyncImage(
                                model = photo.uri,
                                contentDescription = photo.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .padding(3.dp)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = ActionKeep)
                        Spacer(Modifier.width(8.dp))
                        Text("Everything is backed up.", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ConnectionStatusBadge(status: ConnectionStatus) {
    when (status) {
        ConnectionStatus.TESTING -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        ConnectionStatus.SUCCESS -> Icon(Icons.Filled.CheckCircle, contentDescription = "Connected", tint = ActionKeep)
        ConnectionStatus.FAILURE -> Icon(Icons.Filled.CloudOff, contentDescription = "Failed", tint = MaterialTheme.colorScheme.error)
        ConnectionStatus.UNKNOWN -> {}
    }
}
