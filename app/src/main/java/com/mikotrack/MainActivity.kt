package com.mikotrack

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.mikotrack.data.prefs.ThemeMode
import com.mikotrack.ui.common.SplashScreen
import com.mikotrack.ui.navigation.MycoTrackNavHost
import com.mikotrack.ui.theme.MycoTrackTheme
import com.mikotrack.ui.update.AppUpdateHost

/**
 * The single activity of the application.
 *
 * It is an [AppCompatActivity] so that the language chosen in the settings is applied on
 * every Android version, not only on the ones with per-app languages.
 */
class MainActivity : AppCompatActivity() {

    /** Whether the loading screen has already been shown (once per activity). */
    private val bootedFlow = kotlinx.coroutines.flow.MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The application draws behind the system bars from Android 15 onwards, so the
        // edge to edge mode is enabled explicitly to get the same layout on every
        // version. The screens apply the window insets to their own content.
        enableEdgeToEdge()
        setContent {
            val container = (application as MycoTrackApp).container
            val themeMode by container.settingsStore.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            val notificationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { }

            LaunchedEffect(Unit) {
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                // The loading screen stays a short, fixed moment while the interface
                // behind it composes and the map style starts loading.
                kotlinx.coroutines.delay(SPLASH_MILLIS)
                bootedFlow.value = true
                // The silent update check runs once the loading screen is gone, so
                // that the dialog is never hidden behind it: at most once a day, and
                // only when the setting is on. Failures stay invisible.
                container.updateManager.check(force = false)
            }

            MycoTrackTheme(darkTheme = darkTheme) {
                // The icons of the status and navigation bars have to contrast with the
                // theme of the application, which the user can override independently
                // of the system one.
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as? Activity)?.window ?: return@SideEffect
                        val controller = WindowCompat.getInsetsController(window, view)
                        controller.isAppearanceLightStatusBars = !darkTheme
                        controller.isAppearanceLightNavigationBars = !darkTheme
                    }
                }
                // The loading screen covers the interface for a short moment: the
                // screens are composed behind it, so the map starts warming up at once.
                val booted by bootedFlow.collectAsState(initial = false)
                Box(Modifier.fillMaxSize()) {
                    MycoTrackNavHost(container = container)
                    // The self update dialogs, visible above every screen.
                    AppUpdateHost(container.updateManager)
                    if (!booted) {
                        SplashScreen(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    private companion object {
        /**
         * How long the loading screen is shown at the start. It counts from the first
         * frame, which is also when the system splash (the app icon) starts to fade,
         * so it has to cover that animation as well.
         */
        const val SPLASH_MILLIS = 2_600L
    }
}
