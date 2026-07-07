package com.example.expensetracker.ui.history

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.expensetracker.data.Category
import com.example.expensetracker.data.MainViewModel
import com.example.expensetracker.data.Transaction
import com.example.expensetracker.ui.dashboard.TransactionRow
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel, contentBottomPadding: Dp = 120.dp) {
    val transactions by viewModel.transactions.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var txToDelete by remember { mutableStateOf<Transaction?>(null) }

    val categories = Category.values().toList()

    val filteredTransactions = transactions.filter { tx ->
        val matchesSearch = searchQuery.isEmpty() ||
                tx.desc.contains(searchQuery, ignoreCase = true) ||
                tx.cat.label.contains(searchQuery, ignoreCase = true)
        val matchesCategory = selectedCategory == null || tx.cat == selectedCategory
        matchesSearch && matchesCategory
    }
    val grouped = filteredTransactions.groupBy { it.date }

    // Delete single dialog
    txToDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { txToDelete = null },
            title = { Text("Delete Transaction") },
            text = { Text("Delete \"${tx.desc}\"?") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteTransaction(tx.id); txToDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { txToDelete = null }) { Text("Cancel") } }
        )
    }

    // Clear all dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Transactions") },
            text = { Text("Permanently delete all transactions? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAllTransactions(); showClearDialog = false }) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Transactions", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            Row {
                // Export PDF button
                TextButton(onClick = { exportPdf(context, transactions, currencySymbol) }) {
                    Text("PDF")
                }
                if (transactions.isNotEmpty()) {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear all", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Search bar
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search transactions...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )

        Spacer(Modifier.height(10.dp))

        // Category filter chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All") }
                )
            }
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                    label = { Text(cat.label) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (filteredTransactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No transactions found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                grouped.forEach { (dateStr, txs) ->
                    item {
                        Text(
                            formatDateLabel(dateStr),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(txs, key = { it.id }) { tx ->
                        SwipeToDeleteRow(tx = tx, currencySymbol = currencySymbol, onDelete = { txToDelete = tx })
                    }
                }
                item { Spacer(Modifier.height(contentBottomPadding + 16.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteRow(tx: Transaction, currencySymbol: String, onDelete: () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) { onDelete(); false } else false
        }
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    ) {
        TransactionRow(tx = tx, currencySymbol = currencySymbol)
    }
}

fun formatDateLabel(dateStr: String): String {
    return try {
        val date = LocalDate.parse(dateStr)
        val today = LocalDate.now()
        when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
        }
    } catch (e: DateTimeParseException) { dateStr }
}

fun exportPdf(context: Context, transactions: List<Transaction>, currencySymbol: String) {
    val doc = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = doc.startPage(pageInfo)
    val canvas = page.canvas
    val paint = Paint().apply { textSize = 12f }

    var y = 50f
    paint.textSize = 18f
    paint.isFakeBoldText = true
    canvas.drawText("Expense Report", 40f, y, paint)
    y += 30f
    paint.textSize = 11f
    paint.isFakeBoldText = false

    transactions.forEach { tx ->
        if (y > 800f) return@forEach // simple truncation
        val sign = if (tx.amount >= 0) "+" else ""
        canvas.drawText(
            "${tx.date}  ${tx.desc}  ${tx.cat.label}  $sign$currencySymbol${"%.2f".format(tx.amount)}",
            40f, y, paint
        )
        y += 20f
    }

    doc.finishPage(page)
    val file = File(context.cacheDir, "expense_report.pdf")
    doc.writeTo(FileOutputStream(file))
    doc.close()

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Open PDF"))
}