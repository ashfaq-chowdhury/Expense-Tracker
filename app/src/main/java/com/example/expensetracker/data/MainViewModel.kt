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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dataFile = File(application.filesDir, "transactions.json")
    private val prefs = application.getSharedPreferences("expense_prefs", Context.MODE_PRIVATE)

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
        viewModelScope.launch { loadTransactions() }
    }

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

    private suspend fun saveTransactions() = withContext(Dispatchers.IO) {
        val array = JSONArray()
        _transactions.value.forEach { tx ->
            array.put(JSONObject().apply {
                put("id", tx.id)
                put("desc", tx.desc)
                put("cat", tx.cat.name)
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
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(Transaction(
                    id = obj.getInt("id"),
                    desc = obj.getString("desc"),
                    cat = Category.valueOf(obj.getString("cat")),
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
