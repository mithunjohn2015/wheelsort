package com.wheelsort.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.wheelsort.app.ui.navigation.WheelSortNavHost
import com.wheelsort.app.ui.permission.PermissionScreen
import com.wheelsort.app.ui.theme.WheelSortTheme
import com.wheelsort.app.util.readImagesPermission

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // A small custom exit: the icon punches up and fades as the splash hands off to the
        // app content, instead of the system's default instant cut.
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val iconScaleX = ObjectAnimator.ofFloat(splashScreenView.iconView, View.SCALE_X, 1f, 1.15f, 0f)
            val iconScaleY = ObjectAnimator.ofFloat(splashScreenView.iconView, View.SCALE_Y, 1f, 1.15f, 0f)
            val viewFade = ObjectAnimator.ofFloat(splashScreenView.view, View.ALPHA, 1f, 0f)

            listOf(iconScaleX, iconScaleY).forEach {
                it.duration = 360
                it.interpolator = AccelerateInterpolator()
            }
            viewFade.duration = 260
            viewFade.startDelay = 140

            iconScaleX.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    splashScreenView.remove()
                }
            })

            iconScaleX.start()
            iconScaleY.start()
            viewFade.start()
        }

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

    fun currentlyGranted() =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var hasPermission by remember { mutableStateOf(currentlyGranted()) }
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

    // Fixes "grant access from Settings, then have to restart the app": without this, coming
    // back from Settings never re-checks the permission, so the app stays stuck on this screen
    // even though access was actually granted.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = currentlyGranted()
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
