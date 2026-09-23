package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.MainViewModel
import com.example.ui.NavTab
import com.example.ui.components.MediaPlayerDialog
import com.example.ui.components.QuickFormatSheet
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.MediaVaultTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextMuted
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle incoming share or view intent
        handleIntent(intent)

        setContent {
            MediaVaultTheme {
                MediaVaultApp(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_SEND -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!sharedText.isNullOrBlank()) {
                    viewModel.handleSharedIntentText(sharedText)
                }
            }
            Intent.ACTION_VIEW -> {
                val dataUri = intent.dataString
                if (!dataUri.isNullOrBlank()) {
                    viewModel.handleSharedIntentText(dataUri)
                }
            }
        }
    }
}

@Composable
fun MediaVaultApp(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsState()
    val showFormatSheet by viewModel.showFormatSheet.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val analyzedInfo by viewModel.analyzedInfo.collectAsState()
    val selectedFormat by viewModel.selectedFormat.collectAsState()
    val urlInput by viewModel.urlInput.collectAsState()
    val currentlyPlayingItem by viewModel.currentlyPlayingItem.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Request Notification permission for Android 13+ (API 33+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Observe snackbar messages
    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = modifier
            .background(DarkBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == NavTab.HOME,
                    onClick = { viewModel.setTab(NavTab.HOME) },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonCyan,
                        selectedTextColor = NeonCyan,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                        indicatorColor = NeonCyan.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_home")
                )

                NavigationBarItem(
                    selected = currentTab == NavTab.DOWNLOADS,
                    onClick = { viewModel.setTab(NavTab.DOWNLOADS) },
                    icon = { Icon(Icons.Default.CloudDownload, contentDescription = "Queue") },
                    label = { Text("Queue") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonCyan,
                        selectedTextColor = NeonCyan,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                        indicatorColor = NeonCyan.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_downloads")
                )

                NavigationBarItem(
                    selected = currentTab == NavTab.HISTORY,
                    onClick = { viewModel.setTab(NavTab.HISTORY) },
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Vault") },
                    label = { Text("Vault") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonCyan,
                        selectedTextColor = NeonCyan,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                        indicatorColor = NeonCyan.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_history")
                )

                NavigationBarItem(
                    selected = currentTab == NavTab.SETTINGS,
                    onClick = { viewModel.setTab(NavTab.SETTINGS) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonCyan,
                        selectedTextColor = NeonCyan,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                        indicatorColor = NeonCyan.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                NavTab.HOME -> HomeScreen(viewModel = viewModel)
                NavTab.DOWNLOADS -> DownloadsScreen(viewModel = viewModel)
                NavTab.HISTORY -> HistoryScreen(viewModel = viewModel)
                NavTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }
        }
    }

    // Modal Format Picker Sheet
    if (showFormatSheet) {
        QuickFormatSheet(
            url = urlInput,
            videoInfo = analyzedInfo,
            isAnalyzing = isAnalyzing,
            selectedFormat = selectedFormat,
            onFormatSelected = { viewModel.selectFormat(it) },
            onConfirmDownload = { format ->
                viewModel.startDownload(context, format)
            },
            onDismiss = { viewModel.closeFormatSheet() }
        )
    }

    // Media Player Dialog
    currentlyPlayingItem?.let { item ->
        MediaPlayerDialog(
            item = item,
            onDismiss = { viewModel.dismissMediaPlayer() },
            onShare = { viewModel.shareMediaFile(context, item) }
        )
    }
}
