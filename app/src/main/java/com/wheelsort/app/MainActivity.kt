package com.wheelsort.app

import android.content.Intent
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.wheelsort.app.ui.navigation.WheelSortNavHost
import com.wheelsort.app.ui.permission.PermissionScreen
import com.wheelsort.app.ui.splash.AnimatedSplashScreen
import com.wheelsort.app.ui.theme.WheelSortTheme
import com.wheelsort.app.util.hasMediaAccess
import com.wheelsort.app.util.requiredMediaPermissions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Without this, the system splash can hand off before Compose has produced its actual
        // first frame - especially likely on a genuinely cold start right after install, where
        // class loading and the first composition pass take longer than usual.
        //
        // Deliberately NOT using a custom setOnExitAnimationListener here (ObjectAnimator, view
        // references, etc.) - that was added proactively, not requested, and stacking a second
        // hand-built animated transition on top of the system splash, immediately followed by
        // this app's own AnimatedSplashScreen, meant two separate animated handoffs running back
        // to back during the most fragile part of a cold start: initial process/class loading,
        // before Compose has even produced a frame. Simplifying to the system's default exit
        // (a plain fade) removes that extra complexity from exactly the place it's riskiest,
        // without losing the feature actually asked for - the tile-based splash below, which is
        // the one screen actually customized.
        var contentReady = false
        splashScreen.setKeepOnScreenCondition { !contentReady }

        setContent {
            WheelSortTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var showAnimatedSplash by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) { contentReady = true }
                    if (showAnimatedSplash) {
                        AnimatedSplashScreen(onFinished = { showAnimatedSplash = false })
                    } else {
                        PermissionGate()
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionGate() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissions = remember { requiredMediaPermissions() }

    var hasPermission by remember { mutableStateOf(hasMediaAccess(context)) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Don't trust the callback's own granted-map directly - on 34+, choosing "Select photos"
        // reports the requested permissions as NOT granted even though access was granted via a
        // different permission (READ_MEDIA_VISUAL_USER_SELECTED). Always re-check the real state.
        val granted = hasMediaAccess(context)
        hasPermission = granted
        if (!granted) {
            val activity = context as? ComponentActivity
            permanentlyDenied = permissions.none {
                activity?.shouldShowRequestPermissionRationale(it) == true
            }
        }
    }

    // Fixes "grant access from Settings, then have to restart the app": without this, coming
    // back from Settings never re-checks the permission, so the app stays stuck on this screen
    // even though access was actually granted.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = hasMediaAccess(context)
                if (granted != hasPermission) hasPermission = granted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (hasPermission) {
        WheelSortNavHost()
    } else {
        PermissionScreen(
            permanentlyDenied = permanentlyDenied,
            onRequestPermission = { launcher.launch(permissions) },
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
        )
    }
}
