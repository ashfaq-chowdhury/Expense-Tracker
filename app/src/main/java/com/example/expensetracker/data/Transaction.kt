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

enum class Category(val label: String, val iconName: String) {
    FOOD("Food", "restaurant"),
    TRANSPORT("Transport", "commute"),
    SHOPPING("Shopping", "shopping_bag"),
    UTILITIES("Utilities", "bolt"),
    ENTERTAINMENT("Entertainment", "movie"),
    RENT("Rent", "home_work"),
    INCOME("Income", "payments")
}
