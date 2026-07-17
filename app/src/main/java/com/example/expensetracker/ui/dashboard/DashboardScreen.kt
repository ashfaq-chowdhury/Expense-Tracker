package com.example.expensetracker.ui.dashboard

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.expensetracker.data.Category
import com.example.expensetracker.data.MainViewModel
import com.example.expensetracker.data.Transaction
import com.example.expensetracker.data.TransactionType
import com.example.expensetracker.data.TimePeriod
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onSave: (desc: String, category: Category, amount: Double, isIncome: Boolean) -> Unit
) {
    var desc by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.FOOD) }
    var isIncome by remember { mutableStateOf(false) }
    var descError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Description field — full keyboard
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it; descError = false },
                    label = { Text("Description") },
                    isError = descError,
                    supportingText = { if (descError) Text("Required") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true
                )
                // Amount field — decimal keyboard
                OutlinedTextField(
                    value = amount,
                    onValueChange = { value ->
                        // Allow digits and a single decimal point
                        val filtered = value.filter { it.isDigit() || it == '.' }
                        val dotCount = filtered.count { it == '.' }
                        if (dotCount <= 1) { amount = filtered; amountError = false }
                    },
                    label = { Text("Amount") },
                    isError = amountError,
                    supportingText = { if (amountError) Text("Enter a valid amount") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    prefix = { Text(if (isIncome) "+" else "-") }
                )
                // Income / Expense toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Switch(
                        checked = isIncome,
                        onCheckedChange = {
                            isIncome = it
                            if (it) category = Category.INCOME
                            else if (category == Category.INCOME) category = Category.FOOD
                        }
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (isIncome) "Income" else "Expense",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isIncome) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                    )
                }
                // Category chips
                Text(
                    "Category",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val categoryRows = if (isIncome) {
                    listOf(listOf(Category.INCOME))
                } else {
                    listOf(
                        listOf(Category.FOOD, Category.TRANSPORT, Category.SHOPPING),
                        listOf(Category.ENTERTAINMENT, Category.RENT, Category.UTILITIES)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    categoryRows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { cat ->
                                FilterChip(
                                    selected = category == cat,
                                    onClick = { category = cat },
                                    label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull()
                descError = desc.isBlank()
                amountError = amt == null || amt <= 0
                if (!descError && !amountError) {
                    onSave(desc.trim(), category, amt!!, isIncome)
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToHistory: () -> Unit,
    contentBottomPadding: Dp = 120.dp
) {
    val transactions by viewModel.transactions.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val recentTransactions = transactions.take(5)

    val filteredTransactions = viewModel.getFilteredTransactions(transactions, selectedPeriod)
    val totalBalance = filteredTransactions.sumOf { it.amount }
    val filteredIncome = filteredTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val filteredExpense = filteredTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { abs(it.amount) }

    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddTransactionDialog(
            onDismiss = { showAddDialog = false },
            onSave = { desc, category, amt, isIncome ->
                val finalAmt = if (isIncome) amt else -amt
                val today = LocalDate.now().toString()
                val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                viewModel.addTransaction(desc, category, finalAmt, today, now)
                showAddDialog = false
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ExpenseTracker", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "ET",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TimePeriod.entries.forEach { period ->
                        FilterChip(
                            selected = selectedPeriod == period,
                            onClick = { viewModel.setTimePeriod(period) },
                            label = {
                                Text(
                                    when (period) {
                                        TimePeriod.TODAY -> "Today"
                                        TimePeriod.THIS_WEEK -> "Week"
                                        TimePeriod.THIS_MONTH -> "Month"
                                        TimePeriod.ALL_TIME -> "All"
                                    },
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        )
                    }
                }
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            when (selectedPeriod) {
                                TimePeriod.TODAY -> "Today's Balance"
                                TimePeriod.THIS_WEEK -> "This Week's Balance"
                                TimePeriod.THIS_MONTH -> "This Month's Balance"
                                TimePeriod.ALL_TIME -> "Total Balance"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                currencySymbol,
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(bottom = 6.dp, end = 4.dp)
                            )
                            Text("%,.2f".format(totalBalance), style = MaterialTheme.typography.displayLarge)
                        }
                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    "▲ $currencySymbol${"%.2f".format(filteredIncome)}",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    "▼ $currencySymbol${"%.2f".format(filteredExpense)}",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent Activity", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "See All",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onNavigateToHistory() }
                    )
                }
            }
            if (transactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No transactions yet.\nTap + to add one!",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(recentTransactions) { tx ->
                    TransactionRow(tx = tx, currencySymbol = currencySymbol)
                }
            }
            // Bottom spacer so the last item is never hidden under the FAB + nav bar
            item { Spacer(Modifier.height(contentBottomPadding + 72.dp + 16.dp)) }
        }

        // FAB floats just above the pill nav bar
        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 24.dp,
                    bottom = contentBottomPadding + 5.dp   // sits just above nav bar
                )
                .size(64.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add transaction",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun TransactionRow(tx: Transaction, currencySymbol: String) {
    val amountColor = if (tx.amount < 0) MaterialTheme.colorScheme.onSurface
                      else MaterialTheme.colorScheme.secondary
    val amountPrefix = if (tx.amount < 0) "-" else "+"
    val formattedAmount = "$amountPrefix$currencySymbol${"%.2f".format(abs(tx.amount))}"

    val catBgColor = when (tx.cat) {
        Category.FOOD -> MaterialTheme.colorScheme.secondaryContainer
        Category.RENT -> MaterialTheme.colorScheme.tertiaryContainer
        Category.INCOME -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val catIconColor = when (tx.cat) {
        Category.FOOD -> MaterialTheme.colorScheme.onSecondaryContainer
        Category.RENT -> MaterialTheme.colorScheme.onTertiaryContainer
        Category.INCOME -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(catBgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tx.cat.label.take(1),
                    color = catIconColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    tx.desc,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${tx.cat.label} • ${tx.date}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            formattedAmount,
            style = MaterialTheme.typography.bodyLarge,
            color = amountColor,
            fontWeight = FontWeight.Bold
        )
    }
}
