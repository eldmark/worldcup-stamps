package com.estampitas.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.estampitas.app.theme.EstampitasTheme
import com.estampitas.app.ui.AuthScreen
import com.estampitas.app.ui.InventoryScreen
import com.estampitas.app.ui.JoinFamilyScreen
import com.estampitas.di.AppGraph
import com.estampitas.presentation.EstampitasViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val vm =
        remember {
            EstampitasViewModel(AppGraph.client, AppGraph.repository)
        }
    val ui by vm.ui.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val systemInDarkTheme = isSystemInDarkTheme()
    var darkTheme by remember { mutableStateOf(systemInDarkTheme) }

    LaunchedEffect(ui.errorMessage) {
        val msg = ui.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            vm.clearError()
        }
    }

    EstampitasTheme(useDarkTheme = darkTheme) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("Estampitas") },
                    actions = {
                        IconButton(onClick = { darkTheme = !darkTheme }) {
                            Icon(
                                if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Tema",
                            )
                        }
                        if (ui.isAuthenticated) {
                            if (ui.familyId != null) {
                                IconButton(
                                    onClick = { vm.refresh() },
                                    enabled = !ui.isRefreshing && !ui.isLoading,
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                                }
                            }
                            IconButton(onClick = { vm.signOut() }) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Salir")
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    ui.authInitializing -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    !ui.isAuthenticated -> {
                        AuthScreen(
                            ui = ui,
                            onEmailChange = vm::setEmail,
                            onPasswordChange = vm::setPassword,
                            onSignIn = { vm.signIn() },
                            onSignUp = { vm.signUp() },
                        )
                    }
                    ui.familyId == null -> {
                        JoinFamilyScreen(
                            ui = ui,
                            onInviteChange = vm::setInviteKey,
                            onJoin = { vm.joinFamily() },
                        )
                    }
                    else -> {
                        InventoryScreen(
                            ui = ui,
                            onPlusOne = { id -> vm.applyDelta(id, 1) },
                            onMinusOne = { id -> vm.applyDelta(id, -1) },
                        )
                    }
                }
            }
        }
    }
}
