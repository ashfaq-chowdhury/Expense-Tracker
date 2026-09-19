package com.example.expensetracker.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.Category
import com.example.expensetracker.data.MainViewModel
import com.example.expensetracker.data.Transaction
import com.example.expensetracker.data.TransactionType
import com.example.expensetracker.data.TimePeriod
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

// ── Custom Dark Theme Colors (matching the HTML/CSS design palette) ──
// These are used exclusively inside AddTransactionSheet to give the modal
// its own premium dark aesthetic independent of the app-wide Material theme.
private val DarkSurface = Color(0xFF12151F)   // Modal background
private val DarkCard    = Color(0xFF181C28)   // Input / pill card fill
private val DarkBorder  = Color(0xFF23293D)   // Default border colour
private val SubtleText  = Color(0xFF7E8B9F)   // Secondary labels
private val BrandPrimary = Color(0xFF6366F1)  // Indigo accent
private val AccentIncome = Color(0xFF10B981)  // Emerald (income)
private val ExpenseRed   = Color(0xFFF43F5E)  // Rose (expense)

/**
 * Maps a [Category.iconName] value to an emoji glyph.
 * Each default category ships with a Material-icon name (e.g. "restaurant");
 * this helper translates that to a visual emoji for the category-pill grid.
 */
private fun categoryEmoji(iconName: String): String = when (iconName) {
    "restaurant"   -> "\uD83C\uDF54"   // 🍔
    "commute"      -> "\uD83D\uDE87"   // 🚇
    "shopping_bag" -> "\uD83D\uDECD\uFE0F" // 🛍️
    "bolt"         -> "\u26A1"          // ⚡
    "movie"        -> "\uD83C\uDF7F"   // 🍿
    "home_work"    -> "\uD83C\uDFE0"   // 🏠
    "payments"     -> "\uD83D\uDCB0"   // 💰
    "label"        -> "\uD83C\uDFF7\uFE0F" // 🏷️
    else           -> "\uD83D\uDCC1"   // 📁  (fallback for custom categories)
}

/**
 * A redesigned "Add Transaction" bottom-sheet that replaces the old
 * Dialog-based popup.  It slides up from the bottom with a dark, premium
 * look matching the provided HTML/CSS reference design.
 *
 * @param categories       All available categories from the ViewModel.
 * @param currencySymbol   The user-chosen currency symbol ("$", "€", etc.).
 * @param onDismiss        Called when the sheet is dismissed (scrim tap, close, cancel).
 * @param onSave           Called with validated inputs.  [date] comes from the in-sheet
 *                         date picker instead of being hardcoded to today.
 * @param onAddCategory    Called when the user creates a new category via the
 *                         "+ Add Category" button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    categories: List<Category>,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (desc: String, category: Category, amount: Double, isIncome: Boolean, date: String) -> Unit,
    onAddCategory: (String) -> Unit
) {
    // sheetState  – controls expand / collapse; skipPartiallyExpanded = true
    //               means the sheet always opens fully (no half-height peek).
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // ── Form state ──
    var desc             by remember { mutableStateOf("") }
    var amount           by remember { mutableStateOf("") }
    var isIncome         by remember { mutableStateOf(false) }
    var selectedCategory by remember {
        mutableStateOf(
            categories.find { it.id == "food" }
                ?: categories.firstOrNull()
                ?: Category.DEFAULTS.first()
        )
    }
    var selectedDate     by remember { mutableStateOf(LocalDate.now()) }
    var descError        by remember { mutableStateOf(false) }
    var amountError      by remember { mutableStateOf(false) }
    var showDatePicker   by remember { mutableStateOf(false) }
    var showAddCatDialog by remember { mutableStateOf(false) }
    var newCategoryName  by remember { mutableStateOf("") }

    // typeColor – smoothly animates between rose (expense) ↔ emerald (income)
    //             whenever the user flips the segmented toggle.
    val typeColor by animateColorAsState(
        targetValue = if (isIncome) AccentIncome else ExpenseRed,
        animationSpec = tween(300), label = "typeColor"
    )

    // ────────────────────────────────────────────────────────────────
    // Date Picker Dialog  (Material3 calendar popup)
    // Converts LocalDate → epoch-millis for the picker, then back.
    // ────────────────────────────────────────────────────────────────
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    // ────────────────────────────────────────────────────────────────
    // Add Category Dialog  (simple text-input alert, like the HTML prompt)
    // ────────────────────────────────────────────────────────────────
    if (showAddCatDialog) {
        AlertDialog(
            onDismissRequest = { showAddCatDialog = false; newCategoryName = "" },
            title = { Text("Add Category") },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("Category name") },
                    placeholder = { Text("e.g. Subscriptions, Gym") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newCategoryName.isNotBlank()) {
                        onAddCategory(newCategoryName.trim())
                        newCategoryName = ""
                        showAddCatDialog = false
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddCatDialog = false; newCategoryName = ""
                }) { Text("Cancel") }
            }
        )
    }

    // ────────────────────────────────────────────────────────────────
    // Main Bottom Sheet
    // ────────────────────────────────────────────────────────────────
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        dragHandle = null,                       // we render our own pull-handle
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 24.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // ── Pull Handle Bar ──
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF3D4458))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(12.dp))

            // ── Header Row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Add Transaction",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                    Text(
                        "Track money in & out of your wallets",
                        style = MaterialTheme.typography.labelMedium,
                        color = SubtleText
                    )
                }
                // Close (✕) button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(DarkCard)
                        .border(1.dp, DarkBorder, CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Divider ──
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DarkBorder.copy(alpha = 0.4f))
            )

            Spacer(Modifier.height(16.dp))

            // ══════════════════════════════════════════════════════
            // 1.  EXPENSE / INCOME  SEGMENTED TOGGLE
            // Two side-by-side buttons inside a dark card.
            // The active button gets a tinted bg + coloured border
            // + a small glowing dot indicator.
            // ══════════════════════════════════════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
                    .border(1.dp, DarkBorder.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // — Expense button —
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (!isIncome) ExpenseRed.copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .then(
                            if (!isIncome) Modifier.border(
                                1.dp, ExpenseRed.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            ) else Modifier
                        )
                        .clickable {
                            isIncome = false
                            if (selectedCategory.id == Category.INCOME.id) {
                                selectedCategory = categories.firstOrNull {
                                    it.id != Category.INCOME.id
                                } ?: selectedCategory
                            }
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (!isIncome) ExpenseRed else Color(0xFF475569)
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Expense",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (!isIncome) FontWeight.Bold
                                             else FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = if (!isIncome) ExpenseRed else Color(0xFF94A3B8)
                        )
                    }
                }

                // — Income button —
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isIncome) AccentIncome.copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .then(
                            if (isIncome) Modifier.border(
                                1.dp, AccentIncome.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            ) else Modifier
                        )
                        .clickable {
                            isIncome = true
                            selectedCategory = categories.find {
                                it.id == Category.INCOME.id
                            } ?: Category.INCOME
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isIncome) AccentIncome else Color(0xFF475569)
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Income",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isIncome) FontWeight.Bold
                                             else FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = if (isIncome) AccentIncome else Color(0xFF94A3B8)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ══════════════════════════════════════════════════════
            // 2.  AMOUNT INPUT
            // Large, centred mono-font field inside a dark card.
            // The currency symbol colour animates with typeColor.
            // ══════════════════════════════════════════════════════
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard.copy(alpha = 0.8f))
                    .border(
                        width = 1.dp,
                        color = if (amountError) Color(0xFFEF4444) else DarkBorder,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "AMOUNT",
                    style = MaterialTheme.typography.labelMedium.copy(
                        letterSpacing = 1.sp, fontWeight = FontWeight.Medium
                    ),
                    color = SubtleText
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Currency symbol — colour changes with transaction type
                    Text(
                        currencySymbol,
                        style = TextStyle(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = typeColor
                    )
                    Spacer(Modifier.width(4.dp))
                    // BasicTextField for full styling control (no Material
                    // decoration that clashes with the dark card look).
                    BasicTextField(
                        value = amount,
                        onValueChange = { value ->
                            val filtered = value.filter { it.isDigit() || it == '.' }
                            if (filtered.count { it == '.' } <= 1) {
                                amount = filtered; amountError = false
                            }
                        },
                        textStyle = TextStyle(
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(BrandPrimary),
                        modifier = Modifier.widthIn(max = 200.dp),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.Center) {
                                if (amount.isEmpty()) {
                                    Text(
                                        "0.00",
                                        style = TextStyle(
                                            fontSize = 30.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF475569),
                                            textAlign = TextAlign.Center
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
                if (amountError) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Please enter a valid amount",
                        color = Color(0xFFEF4444),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ══════════════════════════════════════════════════════
            // 3.  DESCRIPTION INPUT
            // BasicTextField inside a dark rounded card.
            // ══════════════════════════════════════════════════════
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Description",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold, fontSize = 12.sp
                    ),
                    color = Color(0xFFCBD5E1)
                )
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = desc,
                    onValueChange = { desc = it; descError = false },
                    textStyle = TextStyle(fontSize = 14.sp, color = Color.White),
                    singleLine = true,
                    cursorBrush = SolidColor(BrandPrimary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkCard)
                        .border(
                            1.dp,
                            if (descError) Color(0xFFEF4444) else DarkBorder,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    decorationBox = { innerTextField ->
                        Box {
                            if (desc.isEmpty()) {
                                Text(
                                    "e.g. Grocery & Snacks, Metro Pass",
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        color = Color(0xFF64748B)
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (descError) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Description is required",
                        color = Color(0xFFEF4444),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ══════════════════════════════════════════════════════
            // 4.  CATEGORY GRID
            // 3-column grid of emoji-icon + label pills.
            // Selected pill gets an indigo border glow.
            // Includes a dashed-border "+ Add Category" button.
            // ══════════════════════════════════════════════════════
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Category",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold, fontSize = 12.sp
                    ),
                    color = Color(0xFFCBD5E1)
                )

                Spacer(Modifier.height(8.dp))

                // displayCategories – show only expense cats in Expense mode,
                // or only the Income cat in Income mode.
                val displayCategories = if (isIncome) {
                    listOf(
                        categories.find { it.id == Category.INCOME.id }
                            ?: Category.INCOME
                    )
                } else {
                    categories.filter { it.id != Category.INCOME.id }
                }

                // Render rows of 3 category pills each
                val rows = displayCategories.chunked(3)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { rowItems ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowItems.forEach { cat ->
                                val isSelected = selectedCategory == cat
                                val pillBorder =
                                    if (isSelected) BrandPrimary.copy(alpha = 0.6f)
                                    else DarkBorder
                                val pillBg =
                                    if (isSelected) BrandPrimary.copy(alpha = 0.1f)
                                    else DarkCard
                                val pillText =
                                    if (isSelected) Color(0xFFA5B4FC)
                                    else Color(0xFFCBD5E1)

                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(pillBg)
                                        .border(
                                            1.dp, pillBorder,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { selectedCategory = cat }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected)
                                                    BrandPrimary.copy(alpha = 0.2f)
                                                else Color(0xFF1E293B)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            categoryEmoji(cat.iconName),
                                            fontSize = 14.sp
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        cat.label,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight =
                                                if (isSelected) FontWeight.SemiBold
                                                else FontWeight.Medium,
                                            fontSize = 12.sp
                                        ),
                                        color = pillText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            // Fill any empty cells if row has < 3 items
                            repeat(3 - rowItems.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }

                    // ── "+ Add Category" dashed-border button ──
                    // Only shown in Expense mode (Income has one fixed category).
                    if (!isIncome) {
                        // dashColor / dashStroke – used by drawBehind to draw
                        // a dashed rounded-rect border (Compose has no built-in
                        // dashed Border modifier).
                        val dashColor  = BrandPrimary.copy(alpha = 0.5f)
                        val dashStroke = Stroke(
                            width = 1.dp.value * 2.5f,
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(10f, 6f), 0f
                            )
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(2f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BrandPrimary.copy(alpha = 0.05f))
                                    .drawBehind {
                                        drawRoundRect(
                                            color = dashColor,
                                            style = dashStroke,
                                            cornerRadius = CornerRadius(
                                                12.dp.toPx()
                                            )
                                        )
                                    }
                                    .clickable { showAddCatDialog = true }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            BrandPrimary.copy(alpha = 0.2f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Add",
                                        tint = Color(0xFF818CF8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "+ Add Category",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    ),
                                    color = Color(0xFF818CF8)
                                )
                            }
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ══════════════════════════════════════════════════════
            // 5.  DATE SECTION
            // "Today" / "Yesterday" quick-pick chips + tappable
            // date display that opens a Material3 DatePickerDialog.
            // ══════════════════════════════════════════════════════
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Date",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold, fontSize = 12.sp
                        ),
                        color = Color(0xFFCBD5E1)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // isToday / isYesterday – highlight the active chip
                        val isToday = selectedDate == LocalDate.now()
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isToday) BrandPrimary.copy(alpha = 0.2f)
                                    else DarkCard
                                )
                                .border(
                                    1.dp,
                                    if (isToday) BrandPrimary.copy(alpha = 0.3f)
                                    else DarkBorder,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { selectedDate = LocalDate.now() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "Today",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp
                                ),
                                color = if (isToday) Color(0xFFA5B4FC)
                                        else Color(0xFF94A3B8)
                            )
                        }
                        val isYesterday =
                            selectedDate == LocalDate.now().minusDays(1)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isYesterday) BrandPrimary.copy(alpha = 0.2f)
                                    else DarkCard
                                )
                                .border(
                                    1.dp,
                                    if (isYesterday) BrandPrimary.copy(alpha = 0.3f)
                                    else DarkBorder,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    selectedDate = LocalDate.now().minusDays(1)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "Yesterday",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp
                                ),
                                color = if (isYesterday) Color(0xFFA5B4FC)
                                        else Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Tappable date display – opens the calendar DatePickerDialog
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkCard)
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        selectedDate.format(
                            DateTimeFormatter.ofPattern("yyyy-MM-dd")
                        ),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 12.sp
                        ),
                        color = Color(0xFFE2E8F0)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ══════════════════════════════════════════════════════
            // 6.  ACTION BUTTONS
            // Cancel (1/3 width, dark outlined) +
            // Save Transaction (2/3 width, gradient indigo CTA
            // with ✓ icon).
            // ══════════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Cancel
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkCard)
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                        .clickable { onDismiss() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Cancel",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold, fontSize = 12.sp
                        ),
                        color = Color(0xFF94A3B8)
                    )
                }

                // Save — gradient indigo background
                Box(
                    modifier = Modifier
                        .weight(2f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF6366F1), Color(0xFF4F46E5)
                                )
                            )
                        )
                        .clickable {
                            val amt = amount.toDoubleOrNull()
                            amountError = amt == null || amt <= 0
                            if (!amountError) {
                                // If desc is blank, auto-fill with category label
                                val finalDesc = desc.trim().ifEmpty {
                                    selectedCategory.label
                                }
                                onSave(
                                    finalDesc,
                                    selectedCategory,
                                    amt!!,
                                    isIncome,
                                    selectedDate.toString()
                                )
                            }
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Save",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Save Transaction",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold, fontSize = 12.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
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
        AddTransactionSheet(
            categories = viewModel.categories.collectAsState().value,
            currencySymbol = currencySymbol,
            onDismiss = { showAddDialog = false },
            onSave = { desc, category, amt, isIncome, date ->
                val finalAmt = if (isIncome) amt else -amt
                val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                viewModel.addTransaction(desc, category, finalAmt, date, now)
                showAddDialog = false
            },
            onAddCategory = { name -> viewModel.addCategory(name) }
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

    val catBgColor = when (tx.cat.id) {
        "food" -> MaterialTheme.colorScheme.secondaryContainer
        "rent" -> MaterialTheme.colorScheme.tertiaryContainer
        "income" -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val catIconColor = when (tx.cat.id) {
        "food" -> MaterialTheme.colorScheme.onSecondaryContainer
        "rent" -> MaterialTheme.colorScheme.onTertiaryContainer
        "income" -> MaterialTheme.colorScheme.onPrimaryContainer
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
