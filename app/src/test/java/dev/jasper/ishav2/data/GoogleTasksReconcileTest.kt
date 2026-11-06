package dev.jasper.ishav2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class GoogleTasksReconcileTest {
    private val zone = ZoneId.of("Asia/Manila")
    private val nowMs = LocalDate.of(2026, 11, 3).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
    private fun day(d: Int) = LocalDate.of(2026, 11, d)
    private fun eod(d: Int) = DeadlineStore.endOfDay(day(d), zone)
    private fun remote(id: String, title: String, d: Int?, done: Boolean = false) =
        RemoteTask(id, title, "", d?.let(::day), done, null)

    @Test
    fun importsNewTasksAndCreatesUnlinkedDeadlines() {
        val data = DeadlineData(items = listOf(Deadline(id = "m:1", title = "Essay", dueAt = eod(6))))
        val (next, push) = DeadlineStore.reconcileTasks(
            data, listOf(remote("t1", "Buy milk", 5), remote("t2", "No date", null)), nowMs, zone
        )
        val imported = next.items.single { it.fromTasks }
        assertEquals("gt:t1", imported.id)
        assertEquals("t1", imported.taskId)
        assertEquals(listOf("m:1"), push.create.map { it.id })
    }

    @Test
    fun linksMatchingTaskInsteadOfDuplicating() {
        val data = DeadlineData(items = listOf(Deadline(id = "m:1", title = "Essay", dueAt = eod(6))))
        val (next, push) = DeadlineStore.reconcileTasks(data, listOf(remote("t1", "essay", 6)), nowMs, zone)
        assertEquals("t1", next.items.single().taskId)
        assertTrue(push.create.isEmpty())
    }

    @Test
    fun localChangeWinsAndRemoteChangeIsPulled() {
        val data = DeadlineData(
            items = listOf(
                Deadline(id = "m:1", title = "A", dueAt = eod(6), done = true, taskId = "t1", taskDone = false),
                Deadline(id = "m:2", title = "B", dueAt = eod(6), taskId = "t2", taskDone = false)
            )
        )
        val (next, push) = DeadlineStore.reconcileTasks(
            data, listOf(remote("t1", "A", 6), remote("t2", "B", 6, done = true)), nowMs, zone
        )
        assertEquals(listOf(Triple("m:1", "t1", true)), push.status)
        assertTrue(next.items.single { it.id == "m:2" }.done)
    }

    @Test
    fun remoteDeletionRemovesTaskItemsAndUnlinksOthers() {
        val data = DeadlineData(
            items = listOf(
                Deadline(id = "gt:t1", title = "A", dueAt = eod(6), source = Deadline.SOURCE_TASKS, taskId = "t1", taskDone = false),
                Deadline(id = "m:2", title = "B", dueAt = eod(6), taskId = "t2", taskDone = false)
            )
        )
        val (next, push) = DeadlineStore.reconcileTasks(data, emptyList(), nowMs, zone)
        val left = next.items.single()
        assertEquals("m:2", left.id)
        assertNull(left.taskId)
        assertTrue("m:2" in next.tasksSkip)
        assertTrue(push.create.isEmpty())
    }
}
