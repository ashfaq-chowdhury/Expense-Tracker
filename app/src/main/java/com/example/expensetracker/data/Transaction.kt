package com.example.expensetracker.data

data class Transaction(
    val id: Int,
    val desc: String,
    val cat: Category,
    val amount: Double,
    val date: String, // e.g. "2026-06-23"
    val time: String, // e.g. "14:30"
    val type: TransactionType
)

enum class TransactionType {
    INCOME, EXPENSE
}

data class Category(
    val id: String,
    val label: String,
    val iconName: String
) {
    companion object {
        val DEFAULTS = listOf(
            Category("food", "Food", "restaurant"),
            Category("transport", "Transport", "commute"),
            Category("shopping", "Shopping", "shopping_bag"),
            Category("utilities", "Utilities", "bolt"),
            Category("entertainment", "Entertainment", "movie"),
            Category("rent", "Rent", "home_work"),
            Category("income", "Income", "payments")
        )
        val INCOME = DEFAULTS.last()
    }
}
