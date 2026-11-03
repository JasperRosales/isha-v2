package dev.jasper.ishav2.data

import dev.jasper.ishav2.agent.offlineContextFor
import dev.jasper.ishav2.notify.DeadlineReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DeadlineStoreTest {
    private val zone = ZoneId.of("Asia/Manila")
    private val now = LocalDate.of(2026, 11, 3).atTime(9, 0).atZone(zone)
    private val nowMs = now.toInstant().toEpochMilli()
    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDate.of(2026, 11, day).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun mergeKeepsLocalStateAndManualItemsAndHonoursDismissed() {
        val data = DeadlineData(
            items = listOf(
                Deadline(id = "cw:1:a", title = "A", dueAt = at(5, 10), source = Deadline.SOURCE_CLASSROOM, done = true),
                Deadline(id = "cw:1:gone", title = "Gone", dueAt = at(5, 10), source = Deadline.SOURCE_CLASSROOM),
                Deadline(id = "m:1", title = "Mine", dueAt = at(6, 10))
            ),
            dismissed = setOf("cw:1:hidden")
        )
        val fetched = listOf(
            Deadline(id = "cw:1:a", title = "A renamed", dueAt = at(5, 12), source = Deadline.SOURCE_CLASSROOM),
            Deadline(id = "cw:1:hidden", title = "Hidden", dueAt = at(7, 10), source = Deadline.SOURCE_CLASSROOM)
        )
        val merged = DeadlineStore.merge(data, fetched, nowMs).associateBy { it.id }
        assertEquals(setOf("m:1", "cw:1:a"), merged.keys)
        assertTrue(merged.getValue("cw:1:a").done)
        assertEquals("A renamed", merged.getValue("cw:1:a").title)
    }

    @Test
    fun groupsByDay() {
        val items = listOf(
            Deadline(id = "late", title = "late", dueAt = at(2, 23)),
            Deadline(id = "today", title = "today", dueAt = at(3, 23)),
            Deadline(id = "tmrw", title = "tmrw", dueAt = at(4, 8)),
            Deadline(id = "week", title = "week", dueAt = at(9, 8)),
            Deadline(id = "later", title = "later", dueAt = at(10, 8)),
            Deadline(id = "done", title = "done", dueAt = at(4, 8), done = true)
        )
        val groups = DeadlineStore.grouped(items, now).associate { (g, list) -> g to list.map { it.id } }
        assertEquals(listOf("late"), groups[DeadlineGroup.OVERDUE])
        assertEquals(listOf("today"), groups[DeadlineGroup.TODAY])
        assertEquals(listOf("tmrw"), groups[DeadlineGroup.TOMORROW])
        assertEquals(listOf("week"), groups[DeadlineGroup.THIS_WEEK])
        assertEquals(listOf("later"), groups[DeadlineGroup.LATER])
        assertEquals(listOf("done"), groups[DeadlineGroup.DONE])
    }

    @Test
    fun remindersPlannedDayBeforeAndTwoHoursBefore() {
        val timed = Deadline(id = "t", title = "t", dueAt = at(5, 17))
        val dateOnly = Deadline(id = "d", title = "d", dueAt = DeadlineStore.endOfDay(LocalDate.of(2026, 11, 6), zone), hasTime = false)
        val soon = Deadline(id = "s", title = "s", dueAt = at(3, 20)) // day-before already passed
        val done = Deadline(id = "x", title = "x", dueAt = at(5, 17), done = true)
        val plan = DeadlineReceiver.plan(listOf(timed, dateOnly, soon, done), nowMs, zone)
        val byItem = plan.groupBy { it.id }.mapValues { (_, a) -> a.map { it.at }.sorted() }
        assertEquals(listOf(at(4, 17), at(5, 15)), byItem["t"])
        assertEquals(listOf(at(5, 19), at(6, 8)), byItem["d"])
        assertEquals(listOf(at(3, 18)), byItem["s"])
        assertFalse("x" in byItem)
    }

    @Test
    fun offlineContextPicksMatchingBlocks() {
        val ctx = offlineContextFor(
            text = "what's due this week and any important emails?",
            budget = { "BUDGET" },
            deadlines = { "DEADLINES" },
            mail = { "MAIL" }
        )!!
        assertTrue("DEADLINES" in ctx && "MAIL" in ctx && "BUDGET" !in ctx)
        assertNull(offlineContextFor("tell me a joke", { "B" }, { "D" }, { "M" }))
    }
}
