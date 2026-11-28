package com.example.devboxai

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.devboxai.firebase.FirebaseConfigStore
import com.example.devboxai.ui.screens.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Root() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Root() {
    val nav = rememberNavController()
    val context = LocalContext.current

    val items = remember {
        listOf(
            Triple("hud",          "HUD",     Icons.Default.Radar),
            Triple("chat",         "AI",      Icons.Default.Chat),
            Triple("security",     "Shield",  Icons.Default.Shield),
            Triple("productivity", "Tasks",   Icons.Default.CheckCircle),
            Triple("files",        "Files",   Icons.Default.Folder),
            Triple("termux",       "CLI",     Icons.Default.Terminal),
            Triple("apk",          "APK",     Icons.Default.Android),
            Triple("selfheal",     "Heal",    Icons.Default.BuildCircle),
            Triple("settings",     "Set",     Icons.Default.Settings)
        )
    }

    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val selected = items.indexOfFirst { it.first == current }.coerceAtLeast(0)

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    LaunchedEffect(Unit) {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        permLauncher.launch(perms.toTypedArray())
        // Keep the background voice service alive: ask for battery-optimization exemption once
        try {
            val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(android.net.Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        } catch (_: Exception) {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.avatar),
                            contentDescription = "AI",
                            modifier = Modifier.size(36.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("DevBox AI", fontSize = 18.sp,
                            style = MaterialTheme.typography.titleMedium)
                    }
                },
                actions = {
                    IconButton(onClick = { nav.navigate("terminal") { launchSingleTop = true } }) {
                        Icon(Icons.Default.Terminal, "Kali Terminal")
                    }
                    IconButton(onClick = { nav.navigate("firebase_setup") { launchSingleTop = true } }) {
                        Icon(Icons.Default.Cloud, "Firebase")
                    }
                    IconButton(onClick = { nav.navigate("remote") { launchSingleTop = true } }) {
                        Icon(Icons.Default.Public, "Live")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { i, (route, label, icon) ->
                    NavigationBarItem(
                        selected = selected == i,
                        onClick = { nav.navigate(route) { launchSingleTop = true } },
                        icon = { Icon(icon, label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "hud", modifier = Modifier.padding(pad)) {
            composable("hud")          { HudScreen(
                onOpenTerminal = { nav.navigate("terminal") { launchSingleTop = true } },
                onNavigate = { route -> nav.navigate(route) { launchSingleTop = true } }
            ) }
            composable("terminal")     { TerminalScreen() }
            composable("chat")         { ChatScreen() }
            composable("security")     { SecurityScreen() }
            composable("productivity") { ProductivityScreen() }
            composable("files")        { FilesScreen() }
            composable("termux")       { TermuxToolsScreen() }
            composable("apk")          { ApkModderScreen() }
            composable("selfheal")     { SelfHealScreen() }
            composable("settings")     { SettingsScreen() }
            composable("remote") {
                RemoteScreen(onGoToSetup = {
                    nav.navigate("firebase_setup") { launchSingleTop = true }
                })
            }
            composable("firebase_setup") {
                FirebaseSetupScreen(onDone = { nav.popBackStack() })
            }
        }
    }
}
