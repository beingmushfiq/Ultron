package com.ultron.companion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import kotlinx.coroutines.launch
import com.ultron.companion.core.designsystem.*
import com.ultron.companion.core.model.ConnectionState
import com.ultron.companion.feature.actions.ActionsScreen
import com.ultron.companion.feature.actions.ActionsViewModel
import com.ultron.companion.feature.chat.ChatScreen
import com.ultron.companion.feature.chat.ChatViewModel
import com.ultron.companion.feature.home.HomeScreen
import com.ultron.companion.feature.home.HomeViewModel
import com.ultron.companion.feature.memory.MemoryScreen
import com.ultron.companion.feature.memory.MemoryViewModel
import com.ultron.companion.feature.onboarding.PairingScreen
import com.ultron.companion.feature.onboarding.PairingViewModel
import com.ultron.companion.feature.settings.SettingsScreen
import com.ultron.companion.feature.settings.SettingsViewModel
import com.ultron.companion.feature.voice.VoiceInteractionSheet
import com.ultron.companion.feature.voice.VoiceManager

class MainActivity : ComponentActivity() {

    private lateinit var voiceManager: VoiceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = UltronCompanionApp.instance
        val repository = app.repository

        voiceManager = VoiceManager(this) { spokenText ->
            repository.addMessage(
                com.ultron.companion.core.model.ChatMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    sender = com.ultron.companion.core.model.MessageSender.USER,
                    text = spokenText,
                    timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                )
            )
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                repository.sendChatMessage(spokenText)
            }
        }

        setContent {
            UltronTheme {
                var showVoiceSheet by remember { mutableStateOf(false) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        showVoiceSheet = true
                        voiceManager.startListening()
                    }
                }

                fun onTriggerVoice() {
                    val hasMicPermission = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasMicPermission) {
                        showVoiceSheet = true
                        voiceManager.startListening()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                val navController = rememberNavController()
                val connState by repository.connectionState.collectAsState()

                val startDestination = if (app.secureStorage.isConfigured) "home" else "pairing"

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(
                    bottomBar = {
                        if (currentRoute != "pairing" && connState !is ConnectionState.NotConfigured) {
                            NavigationBar(
                                containerColor = ObsidianSurface,
                                tonalElevation = 0.dp
                            ) {
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                    label = { Text("Status") },
                                    selected = currentRoute == "home",
                                    onClick = {
                                        navController.navigate("home") {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CrimsonPrimary,
                                        selectedTextColor = CrimsonPrimary,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary,
                                        indicatorColor = GraphiteElevated
                                    )
                                )

                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") },
                                    label = { Text("Chat") },
                                    selected = currentRoute == "chat",
                                    onClick = {
                                        navController.navigate("chat") {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CrimsonPrimary,
                                        selectedTextColor = CrimsonPrimary,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary,
                                        indicatorColor = GraphiteElevated
                                    )
                                )

                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.Bolt, contentDescription = "Actions") },
                                    label = { Text("Actions") },
                                    selected = currentRoute == "actions",
                                    onClick = {
                                        navController.navigate("actions") {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CrimsonPrimary,
                                        selectedTextColor = CrimsonPrimary,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary,
                                        indicatorColor = GraphiteElevated
                                    )
                                )

                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.Psychology, contentDescription = "Memory") },
                                    label = { Text("Memory") },
                                    selected = currentRoute == "memory",
                                    onClick = {
                                        navController.navigate("memory") {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CrimsonPrimary,
                                        selectedTextColor = CrimsonPrimary,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary,
                                        indicatorColor = GraphiteElevated
                                    )
                                )

                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                    label = { Text("Settings") },
                                    selected = currentRoute == "settings",
                                    onClick = {
                                        navController.navigate("settings") {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CrimsonPrimary,
                                        selectedTextColor = CrimsonPrimary,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary,
                                        indicatorColor = GraphiteElevated
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("pairing") {
                            val pairingVm = remember { PairingViewModel(repository) }
                            PairingScreen(
                                viewModel = pairingVm,
                                onPairedSuccess = {
                                    navController.navigate("home") {
                                        popUpTo("pairing") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("home") {
                            val homeVm = remember { HomeViewModel(repository) }
                            HomeScreen(
                                viewModel = homeVm,
                                onNavigateToChat = { navController.navigate("chat") },
                                onNavigateToActions = { navController.navigate("actions") },
                                onNavigateToMemory = { navController.navigate("memory") },
                                onNavigateToSettings = { navController.navigate("settings") },
                                onTriggerVoice = { onTriggerVoice() }
                            )
                        }

                        composable("chat") {
                            val chatVm = remember { ChatViewModel(repository) }
                            ChatScreen(
                                viewModel = chatVm,
                                onNavigateBack = { navController.popBackStack() },
                                onTriggerVoice = { onTriggerVoice() }
                            )
                        }

                        composable("actions") {
                            val actionsVm = remember { ActionsViewModel(repository) }
                            ActionsScreen(
                                viewModel = actionsVm,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("memory") {
                            val memoryVm = remember { MemoryViewModel(repository) }
                            MemoryScreen(
                                viewModel = memoryVm,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("settings") {
                            val settingsVm = remember { SettingsViewModel(repository) }
                            SettingsScreen(
                                viewModel = settingsVm,
                                onNavigateBack = { navController.popBackStack() },
                                onDisconnected = {
                                    navController.navigate("pairing") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                    }

                    if (showVoiceSheet) {
                        VoiceInteractionSheet(
                            voiceManager = voiceManager,
                            onDismiss = { showVoiceSheet = false }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager.destroy()
    }
}
