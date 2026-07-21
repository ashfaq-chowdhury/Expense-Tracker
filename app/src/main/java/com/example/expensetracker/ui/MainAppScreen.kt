package com.example.expensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.expensetracker.data.MainViewModel
import com.example.expensetracker.ui.analytics.AnalyticsScreen
import com.example.expensetracker.ui.dashboard.DashboardScreen
import com.example.expensetracker.ui.history.HistoryScreen
import com.example.expensetracker.ui.settings.SettingsScreen
import com.example.expensetracker.ui.voice.VoiceScreen
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Filled.Home)
    object History : Screen("history", "History", Icons.Filled.History)
    object Analytics : Screen("analytics", "Analytics", Icons.Filled.Analytics)
    object Voice : Screen("voice", "Voice", Icons.Filled.Mic)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
}

@Composable
fun MainAppScreen(viewModel: MainViewModel = viewModel()) {
    val items = listOf(Screen.Dashboard, Screen.History, Screen.Analytics, Screen.Voice, Screen.Settings)
    val pagerState = rememberPagerState(initialPage = 0) { items.size }
    val coroutineScope = rememberCoroutineScope()

    // Measure system navigation bar height so we can sit just above it
    val navBarInsets = WindowInsets.navigationBars
    val navBarHeight = navBarInsets.asPaddingValues().calculateBottomPadding()

    // Height of the pill nav bar itself (icon + vertical padding inside)
    val pillNavHeight = 64.dp

    // Total bottom padding that content should respect so nothing hides under the nav bar
    val contentBottomPadding = navBarHeight + pillNavHeight + 12.dp // 12dp gap above pill

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true,
            beyondViewportPageCount = 1
        ) { page ->
            when (items[page]) {
                Screen.Dashboard -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToHistory = {
                        coroutineScope.launch {
                            pagerState.scrollToPage(1)
                        }
                    },
                    contentBottomPadding = contentBottomPadding
                )
                Screen.History -> HistoryScreen(
                    viewModel = viewModel,
                    contentBottomPadding = contentBottomPadding
                )
                Screen.Analytics -> AnalyticsScreen(
                    viewModel = viewModel,
                    contentBottomPadding = contentBottomPadding
                )
                Screen.Voice -> VoiceScreen(
                    viewModel = viewModel,
                    contentBottomPadding = contentBottomPadding
                )
                Screen.Settings -> SettingsScreen(
                    viewModel = viewModel,
                    contentBottomPadding = contentBottomPadding
                )
            }
        }

        // Floating pill nav bar — sits just above the system navigation bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp)
                .padding(bottom = navBarHeight + 12.dp)   // always above the system nav bar
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(50.dp),
                    ambientColor = Color.Black.copy(alpha = 0.4f),
                    spotColor = Color.Black.copy(alpha = 0.4f)
                )
                .clip(RoundedCornerShape(50.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1A1A2E),
                            Color(0xFF16213E),
                            Color(0xFF0F3460)
                        )
                    )
                )
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, screen ->
                    val selected = pagerState.currentPage == index
                    PillNavItem(
                        label = screen.title,
                        icon = screen.icon,
                        selected = selected,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.scrollToPage(index)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PillNavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val selectedBg = Brush.linearGradient(
        colors = listOf(Color(0xFF338FE4), Color(0xFF6FA8DC))
    )
    val contentColor = if (selected) Color.White else Color(0xFF8A8A9A)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .then(
                if (selected) Modifier.background(brush = selectedBg)
                else Modifier.background(Color.Transparent)
            )
            .clickable { onClick() }
            .padding(horizontal = if (selected) 16.dp else 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(18.dp))
                Text(label, color = contentColor, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        } else {
            Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(22.dp))
        }
    }
}