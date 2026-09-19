package com.example.expensetracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.expensetracker.data.Category
import com.example.expensetracker.data.MainViewModel

@Composable
fun CategoryManagementScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val categories by viewModel.categories.collectAsState()

    // State for "Add new category" input
    var showAddField by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    // State for edit dialog
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var editName by remember { mutableStateOf("") }

    // State for delete confirmation dialog
    var deletingCategory by remember { mutableStateOf<Category?>(null) }

    // ── Edit Dialog ──
    editingCategory?.let { cat ->
        Dialog(onDismissRequest = { editingCategory = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Edit Category",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Category Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { editingCategory = null }) {
                            Text("Cancel")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (editName.isNotBlank()) {
                                    viewModel.updateCategory(cat.id, editName, cat.iconName)
                                    editingCategory = null
                                }
                            }
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    // ── Delete Confirmation Dialog ──
    deletingCategory?.let { cat ->
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text("Delete Category") },
            text = { Text("Delete \"${cat.label}\"? Transactions using this category will keep their label.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCategory(cat.id)
                    deletingCategory = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingCategory = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Main Content ──
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Manage Categories",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "ADD" text button — replaces the old FAB at the bottom
                TextButton(
                    onClick = { showAddField = true },
                    enabled = !showAddField
                ) {
                    Text(
                        "ADD",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (!showAddField)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ── Category List ──
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(categories, key = { it.id }) { cat ->
                val isIncome = cat.id == Category.INCOME.id

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category icon circle with first letter
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            cat.label.take(1),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    // Category name
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            cat.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isIncome) {
                            Text(
                                "Protected — cannot delete",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Edit button
                    IconButton(onClick = {
                        editName = cat.label
                        editingCategory = cat
                    }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit ${cat.label}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Delete button (disabled for Income)
                    IconButton(
                        onClick = { deletingCategory = cat },
                        enabled = !isIncome
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete ${cat.label}",
                            tint = if (isIncome)
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }

            // ── Add Category Section ──
            item {
                if (showAddField) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it },
                            placeholder = { Text("New category name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (newCategoryName.isNotBlank()) {
                                        viewModel.addCategory(newCategoryName)
                                        newCategoryName = ""
                                        showAddField = false
                                    }
                                }
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newCategoryName.isNotBlank()) {
                                    viewModel.addCategory(newCategoryName)
                                    newCategoryName = ""
                                    showAddField = false
                                }
                            }
                        ) {
                            Text("Add")
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = {
                            showAddField = false
                            newCategoryName = ""
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    }
                }
            }
        }


    }
}
