package com.wheelsort.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.wheelsort.app.ui.navigation.WheelSortNavHost
import com.wheelsort.app.ui.permission.PermissionScreen
import com.wheelsort.app.ui.theme.WheelSortTheme
import com.wheelsort.app.util.readImagesPermission

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WheelSortTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PermissionGate()
                }
            }
        }
    }
}

@Composable
private fun PermissionGate() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val permission = remember { readImagesPermission() }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            val activity = context as? ComponentActivity
            permanentlyDenied = activity?.shouldShowRequestPermissionRationale(permission) == false
        }
    }

    if (hasPermission) {
        WheelSortNavHost()
    } else {
        PermissionScreen(
            permanentlyDenied = permanentlyDenied,
            onRequestPermission = { launcher.launch(permission) },
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
        )
    }
}
