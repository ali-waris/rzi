package com.hc.rzi.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.hc.rzi.ui.library.LibraryScreen
import com.hc.rzi.ui.library.detail.QuoteDetailScreen
import com.hc.rzi.ui.reel.ReelScreen
import com.hc.rzi.ui.theme.AppEasing
import com.hc.rzi.ui.theme.Duration

@Composable
fun RziNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            val onTopLevel = currentDestination?.hierarchy?.any {
                it.hasRoute<Destination.Reel>() || it.hasRoute<Destination.Library>()
            } == true
            if (onTopLevel) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentDestination.hierarchy.any {
                            it.hasRoute<Destination.Reel>()
                        },
                        onClick = {
                            navController.popBackStack()
                            navController.navigate(Destination.Reel) { launchSingleTop = true }
                        },
                        icon = { Icon(Icons.Filled.AutoStories, contentDescription = null) },
                        label = { Text("Reel") },
                    )
                    NavigationBarItem(
                        selected = currentDestination.hierarchy.any {
                            it.hasRoute<Destination.Library>()
                        },
                        onClick = {
                            navController.popBackStack()
                            navController.navigate(Destination.Library) { launchSingleTop = true }
                        },
                        icon = { Icon(Icons.Filled.CollectionsBookmark, contentDescription = null) },
                        label = { Text("Library") },
                    )
                }
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
            composable<Destination.Library> {
                LibraryScreen(onQuoteClick = { quoteId ->
                    navController.navigate(Destination.QuoteDetail(quoteId))
                })
            }
            composable<Destination.QuoteDetail>(
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(Duration.medium4, easing = AppEasing.EmphasizedDecel),
                    ) + fadeIn(tween(Duration.medium4))
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { -it / 4 },
                        animationSpec = tween(Duration.short4, easing = AppEasing.EmphasizedAccel),
                    ) + fadeOut(tween(Duration.short4))
                },
                popEnterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { -it / 4 },
                        animationSpec = tween(Duration.medium4, easing = AppEasing.EmphasizedDecel),
                    ) + fadeIn(tween(Duration.medium4))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(Duration.short4, easing = AppEasing.EmphasizedAccel),
                    ) + fadeOut(tween(Duration.short4))
                },
            ) { backStackEntry ->
                val route = backStackEntry.toRoute<Destination.QuoteDetail>()
                QuoteDetailScreen(
                    quoteId = route.quoteId,
                    onBack = { navController.navigateUp() },
                )
            }
        }
    }
}
