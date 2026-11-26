package com.voidkernel.apkbuilder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VoidTheme { AppRoot() } }
    }
}

@Composable
fun AppRoot() {
    val vm: AppVM = viewModel()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.toasts.collect { snack.showSnackbar(it) } }
    val ed = vm.editing
    val labels = listOf("Repos", "Workspace", "Output", "Consoles", "Settings")
    val icons = listOf(Icons.Default.Home, Icons.Default.Folder, Icons.Default.Android, Icons.Default.Terminal, Icons.Default.Settings)

    Scaffold(
        containerColor = VC.bg,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (ed == null) NavigationBar(containerColor = VC.surf) {
                labels.forEachIndexed { i, l ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(icons[i], l) }, label = { Text(l, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = VC.purple, selectedTextColor = VC.purple,
                            indicatorColor = VC.card, unselectedIconColor = VC.muted, unselectedTextColor = VC.muted
                        )
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (ed != null) EditorScreen(vm, ed) { vm.editing = null }
            else when (tab) {
                0 -> ReposScreen(vm) { tab = 1 }
                1 -> WorkspaceScreen(vm)
                2 -> OutputScreen(vm)
                3 -> ConsoleScreen(vm)
                else -> SettingsScreen(vm)
            }
            val b = vm.busy
            if (b != null) Column(Modifier.fillMaxWidth()) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = VC.purple)
                Text(b, color = VC.blue, fontSize = 11.sp, modifier = Modifier.padding(start = 12.dp, top = 2.dp))
            }
        }
    }
}
