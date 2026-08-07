package com.rzi.quotes.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rzi.quotes.ui.library.LibraryScreen
import com.rzi.quotes.ui.reel.ReelScreen

@Composable
fun RziNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute?.contains("Reel") == true,
                    onClick = {
                        navController.popBackStack()
                        navController.navigate(Destination.Reel) { launchSingleTop = true }
                    },
                    icon = { Icon(Icons.Filled.AutoStories, contentDescription = null) },
                    label = { Text("Reel") },
                )
                NavigationBarItem(
                    selected = currentRoute?.contains("Library") == true,
                    onClick = {
                        navController.popBackStack()
                        navController.navigate(Destination.Library) { launchSingleTop = true }
                    },
                    icon = { Icon(Icons.Filled.CollectionsBookmark, contentDescription = null) },
                    label = { Text("Library") },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Reel,
            modifier = Modifier.padding(padding),
        ) {
            composable<Destination.Reel> {
                ReelScreen(onAddQuote = { navController.navigate(Destination.Library) })
            }
            composable<Destination.Library> { LibraryScreen() }
        }
    }
}
