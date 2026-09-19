package com.example.expensetracker.data

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dataFile = File(application.filesDir, "transactions.json")
    private val categoriesFile = File(application.filesDir, "categories.json")
    private val prefs = application.getSharedPreferences("expense_prefs", Context.MODE_PRIVATE)

    private val _categories = MutableStateFlow(Category.DEFAULTS)
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _currencySymbol = MutableStateFlow(prefs.getString("currency", "$") ?: "$")
    val currencySymbol: StateFlow<String> = _currencySymbol.asStateFlow()

    private val _themeMode = MutableStateFlow(
        ThemeMode.valueOf(prefs.getString("theme", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _selectedPeriod = MutableStateFlow(TimePeriod.ALL_TIME)
    val selectedPeriod: StateFlow<TimePeriod> = _selectedPeriod.asStateFlow()

    init {
        viewModelScope.launch {
            loadCategories()
            loadTransactions()
        }
    }

    // ── Category CRUD ──────────────────────────────────────────

    fun addCategory(label: String, iconName: String = "label") {
        val newCat = Category(
            id = UUID.randomUUID().toString(),
            label = label.trim(),
            iconName = iconName
        )
        _categories.value = _categories.value + newCat
        viewModelScope.launch { saveCategories() }
    }

    fun updateCategory(id: String, newLabel: String, newIconName: String) {
        _categories.value = _categories.value.map { cat ->
            if (cat.id == id) cat.copy(label = newLabel.trim(), iconName = newIconName) else cat
        }
        // Also update all transactions that reference this category
        _transactions.value = _transactions.value.map { tx ->
            if (tx.cat.id == id) tx.copy(cat = tx.cat.copy(label = newLabel.trim(), iconName = newIconName))
            else tx
        }
        viewModelScope.launch {
            saveCategories()
            saveTransactions()
        }
    }

    fun deleteCategory(id: String) {
        // Don't allow deleting the Income category
        if (id == Category.INCOME.id) return
        _categories.value = _categories.value.filter { it.id != id }
        viewModelScope.launch { saveCategories() }
    }

    private suspend fun saveCategories() = withContext(Dispatchers.IO) {
        val array = JSONArray()
        _categories.value.forEach { cat ->
            array.put(JSONObject().apply {
                put("id", cat.id)
                put("label", cat.label)
                put("iconName", cat.iconName)
            })
        }
        categoriesFile.writeText(array.toString())
    }

    private suspend fun loadCategories() = withContext(Dispatchers.IO) {
        if (!categoriesFile.exists()) {
            _categories.value = Category.DEFAULTS
            return@withContext
        }
        try {
            val array = JSONArray(categoriesFile.readText())
            val list = mutableListOf<Category>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(Category(
                    id = obj.getString("id"),
                    label = obj.getString("label"),
                    iconName = obj.optString("iconName", "label")
                ))
            }
            _categories.value = list
        } catch (e: Exception) {
            _categories.value = Category.DEFAULTS
        }
    }

    // ── Transaction CRUD ──────────────────────────────────────

    fun addTransaction(desc: String, category: Category, amount: Double, date: String, time: String) {
        val type = if (amount >= 0) TransactionType.INCOME else TransactionType.EXPENSE
        val newTx = Transaction(
            id = (_transactions.value.maxOfOrNull { it.id } ?: 0) + 1,
            desc = desc,
            cat = category,
            amount = amount,
            date = date,
            time = time,
            type = type
        )
        _transactions.value = listOf(newTx) + _transactions.value
        viewModelScope.launch { saveTransactions() }
    }

    fun deleteTransaction(id: Int) {
        _transactions.value = _transactions.value.filter { it.id != id }
        viewModelScope.launch { saveTransactions() }
    }

    fun clearAllTransactions() {
        _transactions.value = emptyList()
        viewModelScope.launch { saveTransactions() }
    }

    fun setCurrencySymbol(symbol: String) {
        _currencySymbol.value = symbol
        prefs.edit().putString("currency", symbol).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme", mode.name).apply()
    }

    fun setTimePeriod(period: TimePeriod) {
        _selectedPeriod.value = period
    }

    fun getFilteredTransactions(
        transactions: List<Transaction>,
        period: TimePeriod
    ): List<Transaction> {
        val today = LocalDate.now()
        return when (period) {
            TimePeriod.TODAY -> transactions.filter {
                it.date == today.toString()
            }
            TimePeriod.THIS_WEEK -> {
                val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                transactions.filter {
                    val txDate = LocalDate.parse(it.date)
                    !txDate.isBefore(weekStart) && !txDate.isAfter(today)
                }
            }
            TimePeriod.THIS_MONTH -> transactions.filter {
                it.date.startsWith(today.toString().substring(0, 7))
            }
            TimePeriod.ALL_TIME -> transactions
        }
    }

    fun getExpensesByCategory(
        transactions: List<Transaction>,
        period: TimePeriod
    ): Map<Category, Double> {
        return getFilteredTransactions(transactions, period)
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.cat }
            .mapValues { (_, txList) -> txList.sumOf { kotlin.math.abs(it.amount) } }
    }


    fun getDailyExpenses(transactions: List<Transaction>): List<Pair<String, Double>> {
        val today = LocalDate.now().toString()
        return transactions
            .filter { it.type == TransactionType.EXPENSE && it.date == today }
            .groupBy { it.cat }
            .map { (cat, txs) -> cat.label to txs.sumOf { kotlin.math.abs(it.amount) } }
    }

    fun getWeeklyExpenses(transactions: List<Transaction>): List<Pair<String, Double>> {
        val today = LocalDate.now()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val days = (0..6).map { weekStart.plusDays(it.toLong()) }

        val expenseByDay = transactions
            .filter { it.type == TransactionType.EXPENSE }
            .filter {
                val d = LocalDate.parse(it.date)
                !d.isBefore(weekStart) && !d.isAfter(weekStart.plusDays(6))
            }
            .groupBy { it.date }
            .mapValues { (_, txs) -> txs.sumOf { kotlin.math.abs(it.amount) } }

        return days.map { day ->
            val label = day.dayOfWeek.name.take(3)
            val total = expenseByDay[day.toString()] ?: 0.0
            label to total
        }
    }

    fun getMonthlyExpenses(transactions: List<Transaction>): List<Pair<String, Double>> {
        val today = LocalDate.now()
        val monthStart = today.withDayOfMonth(1)
        val weeksInMonth = mutableListOf<Pair<LocalDate, LocalDate>>()

        var current = monthStart
        while (!current.isAfter(today)) {
            val weekEnd = minOf(current.plusDays(6), today)
            weeksInMonth.add(current to weekEnd)
            current = weekEnd.plusDays(1)
        }

        return weeksInMonth.mapIndexed { index, (start, end) ->
            val total = transactions
                .filter { it.type == TransactionType.EXPENSE }
                .filter {
                    val d = LocalDate.parse(it.date)
                    !d.isBefore(start) && !d.isAfter(end)
                }
                .sumOf { kotlin.math.abs(it.amount) }
            "W${index + 1}" to total
        }
    }

    private suspend fun saveTransactions() = withContext(Dispatchers.IO) {
        val array = JSONArray()
        _transactions.value.forEach { tx ->
            array.put(JSONObject().apply {
                put("id", tx.id)
                put("desc", tx.desc)
                put("catId", tx.cat.id)
                put("catLabel", tx.cat.label)
                put("catIcon", tx.cat.iconName)
                put("amount", tx.amount)
                put("date", tx.date)
                put("time", tx.time)
                put("type", tx.type.name)
            })
        }
        dataFile.writeText(array.toString())
    }

    private suspend fun loadTransactions() = withContext(Dispatchers.IO) {
        if (!dataFile.exists()) {
            _transactions.value = emptyList()
            return@withContext
        }
        try {
            val array = JSONArray(dataFile.readText())
            val list = mutableListOf<Transaction>()
            val cats = _categories.value
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)

                // New format: catId/catLabel/catIcon
                // Old format: cat (enum name like "FOOD")
                val category = if (obj.has("catId")) {
                    val catId = obj.getString("catId")
                    cats.find { it.id == catId }
                        ?: Category(catId, obj.optString("catLabel", catId), obj.optString("catIcon", "label"))
                } else {
                    // Backward compat: old enum name → lowercase id lookup
                    val enumName = obj.getString("cat")
                    cats.find { it.id == enumName.lowercase() }
                        ?: Category(enumName.lowercase(), enumName.lowercase().replaceFirstChar { it.uppercase() }, "label")
                }

                list.add(Transaction(
                    id = obj.getInt("id"),
                    desc = obj.getString("desc"),
                    cat = category,
                    amount = obj.getDouble("amount"),
                    date = obj.getString("date"),
                    time = obj.getString("time"),
                    type = TransactionType.valueOf(obj.getString("type"))
                ))
            }
            _transactions.value = list
        } catch (e: Exception) {
            _transactions.value = emptyList()
        }
    }
}

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class TimePeriod { TODAY, THIS_WEEK, THIS_MONTH, ALL_TIME }
