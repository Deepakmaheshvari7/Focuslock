package com.focuslock

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

private enum class Screen { Home, Apps, Stats, Schedule }

class MainActivity : ComponentActivity() {
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(SystemBarStyle.dark(transparent), SystemBarStyle.dark(transparent))
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        Scheduler.sync(this)
        setContent {
            FocusTheme {
                var screen by remember { mutableStateOf(Screen.Home) }
                val home = { screen = Screen.Home }
                Box(Modifier.fillMaxSize().background(Bg)) {
                    when (screen) {
                        Screen.Home -> HomeScreen(
                            onOpenApps = { screen = Screen.Apps },
                            onOpenStats = { screen = Screen.Stats },
                            onOpenSchedule = { screen = Screen.Schedule }
                        )
                        Screen.Apps -> AppsScreen(onBack = home)
                        Screen.Stats -> StatsScreen(onBack = home)
                        Screen.Schedule -> ScheduleScreen(onBack = home)
                    }
                }
            }
        }
    }
}
