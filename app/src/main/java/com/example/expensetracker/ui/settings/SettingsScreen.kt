package com.example.expensetracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.expensetracker.data.MainViewModel
import com.example.expensetracker.data.ThemeMode

@Composable
fun SettingsScreen(viewModel: MainViewModel, contentBottomPadding: Dp = 120.dp) {
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    val currencies = listOf(
        "$" to "USD — US Dollar",
        "€" to "EUR — Euro",
        "£" to "GBP — British Pound",
        "¥" to "JPY — Japanese Yen",
        "₹" to "INR — Indian Rupee",
        "₩" to "KRW — South Korean Won",
        "৳" to "BDT — Bangladeshi Taka",
        "CHF" to "CHF — Swiss Franc",
        "A$" to "AUD — Australian Dollar",
        "C$" to "CAD — Canadian Dollar"
    )

    // Currency picker dialog
    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("Choose Currency") },
            text = {
                Column {
                    currencies.forEach { (symbol, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setCurrencySymbol(symbol)
                                    showCurrencyDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                            if (currencySymbol == symbol) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        symbol,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            } else {
                                Text(symbol, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencyDialog = false }) { Text("Close") }
            }
        )
    }

    // Theme picker dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Appearance") },
            text = {
                Column {
                    listOf(ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark", ThemeMode.SYSTEM to "System Default")
                        .forEach { (mode, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setThemeMode(mode)
                                        showThemeDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = themeMode == mode, onClick = {
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                })
                                Spacer(Modifier.width(8.dp))
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Close") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SettingsItem(
            title = "Currency",
            subtitle = "$currencySymbol  — tap to change",
            onClick = { showCurrencyDialog = true }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        SettingsItem(
            title = "Appearance",
            subtitle = "System Default",
            onClick = {} // Disabled since it's forced to system default
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        SettingsItem(title = "Storage", subtitle = "Local only — JSON file", onClick = {})
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        SettingsItem(title = "About", subtitle = "v1.0.0", onClick = {})

        // Bottom padding so content isn't hidden under the floating nav bar
        Spacer(Modifier.height(contentBottomPadding))


    }
}

@Composable
fun SettingsItem(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
