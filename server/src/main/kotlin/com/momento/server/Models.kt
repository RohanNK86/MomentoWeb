package com.momento.server

import kotlinx.serialization.Serializable

/** Same priority levels as the Android app. */
@Serializable
enum class Priority { LOW, MEDIUM, HIGH }

@Serializable
data class Task(
    val id: Int,
    val title: String,
    val category: String = "General",
    val priority: Priority = Priority.MEDIUM,
    val dueDate: String? = null,
    val completed: Boolean = false,
)

@Serializable
data class NewTask(
    val title: String,
    val category: String = "General",
    val priority: Priority = Priority.MEDIUM,
    val dueDate: String? = null,
)

@Serializable
data class Habit(
    val id: Int,
    val name: String,
    val currentStreak: Int,
    val longestStreak: Int,
    val doneToday: Boolean,
    /** ISO dates (yyyy-MM-dd) within the last 7 days on which the habit was done. */
    val last7Days: List<String>,
)

@Serializable
data class NewHabit(val name: String)

@Serializable
data class Expense(
    val id: Int,
    val title: String,
    val amount: Double,
    val category: String = "Other",
    val date: String,
)

@Serializable
data class NewExpense(
    val title: String,
    val amount: Double,
    val category: String = "Other",
)

@Serializable
data class Summary(
    val totalTasks: Int,
    val tasksCompleted: Int,
    val totalHabits: Int,
    val habitsDoneToday: Int,
    val totalSpend: Double,
)

@Serializable
data class HealthStatus(val status: String, val version: String, val environment: String)

@Serializable
data class ReadyStatus(val status: String)

@Serializable
data class ErrorResponse(val error: String)
