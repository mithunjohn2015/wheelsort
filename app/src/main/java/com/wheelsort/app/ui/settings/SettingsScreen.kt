package com.wheelsort.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wheelsort.app.util.hasMediaAccess
import com.wheelsort.app.util.hasPartialMediaAccess
import com.wheelsort.app.util.requiredMediaPermissions

@Composable
fun SettingsScreen(
    onExit: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val interactionSource = remember { MutableInteractionSource() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wheel settings") },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.resetToDefaults() }) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Tip: open settings from inside a review session (the tune icon at the top of the wheel) to see your actual photos respond live while you drag.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                item { PermissionSection() }
                item { SectionHeader("Wheel") }
                wheelSettingsSliderItems(
                    settings = settings,
                    interactionSource = interactionSource,
                    onUpdate = viewModel::update
                )
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun PermissionSection() {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(hasMediaAccess(context)) }
    var isPartial by remember { mutableStateOf(hasPartialMediaAccess(context)) }

    // Re-check whenever this screen regains focus - covers coming back from the system
    // permission dialog, the "select more photos" picker, or the app's own settings page.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = hasMediaAccess(context)
                isPartial = hasPartialMediaAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasAccess = hasMediaAccess(context)
        isPartial = hasPartialMediaAccess(context)
    }
    val selectMoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasAccess = hasMediaAccess(context)
        isPartial = hasPartialMediaAccess(context)
    }

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        SectionHeader("Photo access")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (hasAccess) Icons.Filled.CheckCircle else Icons.Filled.PhotoLibrary,
                contentDescription = null,
                tint = if (hasAccess) com.wheelsort.app.ui.theme.ActionKeep else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    !hasAccess -> "No access granted"
                    isPartial -> "Access to selected photos only"
                    else -> "Full access granted"
                },
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!hasAccess) {
                Button(onClick = { permissionLauncher.launch(requiredMediaPermissions()) }) {
                    Text("Grant access")
                }
            }
            if (isPartial && Build.VERSION.SDK_INT >= 34) {
                OutlinedButton(onClick = {
                    val intent = Intent(MediaStore.ACTION_USER_SELECT_IMAGES_FOR_APP).apply {
                        putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
                    }
                    selectMoreLauncher.launch(intent)
                }) {
                    Text("Add more photos")
                }
            }
            OutlinedButton(onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }) {
                Text("App settings")
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}
