package com.momento.server

import java.time.LocalDate

/**
 * Thread-safe in-memory store for tasks, habits and expenses.
 * Every public method locks the store, so concurrent requests from Netty threads are safe.
 *
 * @param today supplies the current date; tests pass a fixed date so streaks are deterministic.
 */
class MomentoStore(private val today: () -> LocalDate = { LocalDate.now() }) {

    private class HabitRecord(val id: Int, val name: String, val doneDates: MutableSet<LocalDate>)

    private val lock = Any()
    private val tasks = mutableListOf<Task>()
    private val habits = mutableListOf<HabitRecord>()
    private val expenses = mutableListOf<Expense>()
    private var nextTaskId = 1
    private var nextHabitId = 1
    private var nextExpenseId = 1

    @Volatile
    var ready: Boolean = false
        private set

    fun markReady() {
        ready = true
    }

    // ---- Tasks ----

    /** Incomplete tasks first, then by priority (HIGH first), then by id. */
    fun listTasks(): List<Task> = synchronized(lock) {
        tasks.sortedWith(
            compareBy<Task> { it.completed }
                .thenByDescending { it.priority.ordinal }
                .thenBy { it.id }
        )
    }

    fun addTask(input: NewTask): Task = synchronized(lock) {
        require(input.title.isNotBlank()) { "title must not be blank" }
        val task = Task(
            id = nextTaskId++,
            title = input.title.trim(),
            category = input.category,
            priority = input.priority,
            dueDate = input.dueDate,
        )
        tasks += task
        task
    }

    fun toggleTask(id: Int): Task? = synchronized(lock) {
        val index = tasks.indexOfFirst { it.id == id }
        if (index < 0) return null
        val updated = tasks[index].copy(completed = !tasks[index].completed)
        tasks[index] = updated
        updated
    }

    fun deleteTask(id: Int): Boolean = synchronized(lock) { tasks.removeIf { it.id == id } }

    // ---- Habits ----

    fun listHabits(): List<Habit> = synchronized(lock) { habits.map { it.toHabit() } }

    fun addHabit(input: NewHabit): Habit = synchronized(lock) {
        require(input.name.isNotBlank()) { "name must not be blank" }
        val record = HabitRecord(nextHabitId++, input.name.trim(), mutableSetOf())
        habits += record
        record.toHabit()
    }

    /** Checks in for today; a second check-in on the same day undoes it (same as the Android app). */
    fun checkIn(id: Int): Habit? = synchronized(lock) {
        val record = habits.firstOrNull { it.id == id } ?: return null
        val date = today()
        if (!record.doneDates.remove(date)) record.doneDates += date
        record.toHabit()
    }

    private fun HabitRecord.toHabit(): Habit {
        val date = today()
        return Habit(
            id = id,
            name = name,
            currentStreak = currentStreak(doneDates, date),
            longestStreak = longestStreak(doneDates),
            doneToday = date in doneDates,
            last7Days = (6 downTo 0).map { date.minusDays(it.toLong()) }
                .filter { it in doneDates }
                .map { it.toString() },
        )
    }

    // ---- Expenses ----

    fun listExpenses(): List<Expense> = synchronized(lock) { expenses.sortedByDescending { it.id } }

    fun addExpense(input: NewExpense): Expense = synchronized(lock) {
        require(input.title.isNotBlank()) { "title must not be blank" }
        require(input.amount > 0) { "amount must be positive" }
        val expense = Expense(nextExpenseId++, input.title.trim(), input.amount, input.category, today().toString())
        expenses += expense
        expense
    }

    // ---- Dashboard ----

    fun summary(): Summary = synchronized(lock) {
        val date = today()
        Summary(
            totalTasks = tasks.size,
            tasksCompleted = tasks.count { it.completed },
            totalHabits = habits.size,
            habitsDoneToday = habits.count { date in it.doneDates },
            totalSpend = expenses.sumOf { it.amount },
        )
    }

    /** Adds past check-ins directly; used only for the seed data. */
    private fun seedCheckIns(habitId: Int, dates: Collection<LocalDate>) = synchronized(lock) {
        habits.first { it.id == habitId }.doneDates += dates
    }

    companion object {
        /**
         * Current streak, the same rule as the Android app's calculateCurrentStreak:
         * the number of consecutive days ending today. If today is not done, the streak is 0.
         */
        fun currentStreak(dates: Set<LocalDate>, today: LocalDate): Int {
            var streak = 0
            var day = today
            while (day in dates) {
                streak++
                day = day.minusDays(1)
            }
            return streak
        }

        /** Longest run of consecutive days anywhere in the history. */
        fun longestStreak(dates: Set<LocalDate>): Int {
            var best = 0
            for (start in dates) {
                if (start.minusDays(1) in dates) continue // not the first day of a run
                var length = 0
                var day = start
                while (day in dates) {
                    length++
                    day = day.plusDays(1)
                }
                best = maxOf(best, length)
            }
            return best
        }

        /** A store filled with the same kind of starter data the Android app seeds on first launch. */
        fun seeded(today: () -> LocalDate = { LocalDate.now() }): MomentoStore {
            val store = MomentoStore(today)
            val date = today()
            store.addTask(NewTask("Finalize Q4 Marketing Deck", "Work", Priority.HIGH, date.toString()))
            store.addTask(NewTask("Review pull requests", "Work", Priority.MEDIUM, date.toString()))
            store.addTask(NewTask("Book dentist appointment", "Health", Priority.LOW, date.plusDays(3).toString()))
            store.addTask(NewTask("Plan weekend trip", "Personal", Priority.MEDIUM, date.plusDays(5).toString()))
            store.toggleTask(store.addTask(NewTask("Pay electricity bill", "Finance", Priority.HIGH)).id)

            val meditate = store.addHabit(NewHabit("Morning Meditation"))
            val read = store.addHabit(NewHabit("Read 20 Pages"))
            store.addHabit(NewHabit("Drink 8 Glasses of Water"))
            store.seedCheckIns(meditate.id, (1L..4L).map { date.minusDays(it) })
            store.seedCheckIns(read.id, listOf(date.minusDays(1), date.minusDays(2)))

            store.addExpense(NewExpense("Groceries", 850.0, "Food"))
            store.addExpense(NewExpense("Metro card recharge", 300.0, "Transport"))
            store.addExpense(NewExpense("Coffee with team", 100.0, "Food"))
            return store
        }
    }
}
