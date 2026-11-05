package com.momento.server

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Unit tests for the business logic: streaks, toggling, validation and the summary. */
class MomentoStoreTest {

    private val today = LocalDate.of(2026, 11, 4)

    @Test
    fun `streak counts consecutive days ending today`() {
        val dates = setOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(5))
        assertEquals(3, MomentoStore.currentStreak(dates, today))
    }

    @Test
    fun `streak is zero when today is not done`() {
        assertEquals(0, MomentoStore.currentStreak(setOf(today.minusDays(1)), today))
    }

    @Test
    fun `check-in toggles today and updates the streak`() {
        val store = MomentoStore { today }
        val habit = store.addHabit(NewHabit("Evening Walk"))
        assertEquals(1, store.checkIn(habit.id)!!.currentStreak)
        assertEquals(0, store.checkIn(habit.id)!!.currentStreak) // second check-in undoes it
    }

    @Test
    fun `toggling a task flips completed and sorts it last`() {
        val store = MomentoStore { today }
        val first = store.addTask(NewTask("A"))
        store.addTask(NewTask("B"))
        assertTrue(store.toggleTask(first.id)!!.completed)
        assertEquals("A", store.listTasks().last().title)
    }

    @Test
    fun `negative expense is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            MomentoStore().addExpense(NewExpense("Refund", -5.0))
        }
    }

    @Test
    fun `summary adds up spend and completed tasks`() {
        val store = MomentoStore { today }
        store.addExpense(NewExpense("Lunch", 10.0))
        store.addExpense(NewExpense("Cab", 2.5))
        store.toggleTask(store.addTask(NewTask("Done")).id)
        val summary = store.summary()
        assertEquals(12.5, summary.totalSpend)
        assertEquals(1, summary.tasksCompleted)
    }
}
