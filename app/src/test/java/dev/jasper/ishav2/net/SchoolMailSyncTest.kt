package dev.jasper.ishav2.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class SchoolMailSyncTest {
    private val manila = ZoneId.of("Asia/Manila")
    private val now = LocalDate.of(2026, 11, 3).atTime(9, 0).atZone(manila).toInstant().toEpochMilli()

    @Test
    fun findsArraysInsideMultiExecuteWrappersAndJsonStrings() {
        // Connect mode: results -> response -> data, with the payload as a JSON string.
        val raw = """{"successful":true,"data":{"results":[{"tool_slug":"X","response":{"successful":true,
            "data":"{\"courses\":[{\"id\":\"c1\",\"name\":\"Calculus\",\"courseState\":\"ACTIVE\"},{\"id\":\"c2\",\"name\":\"Old\",\"courseState\":\"ARCHIVED\"}]}"}}]}}"""
        val courses = ClassroomSync.parseCourses(ComposioJson.parse(raw))
        assertEquals(listOf(ClassroomSync.Course("c1", "Calculus")), courses)
    }

    @Test
    fun nestedFailureIsReported() {
        val raw = """{"successful":true,"data":{"results":[{"response":{"successful":false,"error":"No connected account"}}]}}"""
        assertEquals("No connected account", ComposioJson.failure(ComposioJson.parse(raw)))
        assertNull(ComposioJson.failure(ComposioJson.parse("""{"successful":true,"data":{}}""")))
    }

    @Test
    fun courseWorkUsesUtcTimeAndSkipsUndatedAndOld() {
        val raw = """{"data":{"courseWork":[
            {"id":"w1","title":"Problem set 4","state":"PUBLISHED","dueDate":{"year":2026,"month":11,"day":5},"dueTime":{"hours":15,"minutes":59},"alternateLink":"https://classroom.google.com/x"},
            {"id":"w2","title":"Reading","state":"PUBLISHED","dueDate":{"year":2026,"month":11,"day":6}},
            {"id":"w3","title":"No due date","state":"PUBLISHED"},
            {"id":"w4","title":"Ancient","state":"PUBLISHED","dueDate":{"year":2026,"month":9,"day":1}},
            {"id":"w5","title":"Draft","state":"DRAFT","dueDate":{"year":2026,"month":11,"day":7}}
        ]}}"""
        val items = ClassroomSync.parseCourseWork(
            ComposioJson.parse(raw), ClassroomSync.Course("c1", "Calculus"), setOf("w2"), now, manila
        )
        assertEquals(listOf("cw:c1:w1", "cw:c1:w2"), items.map { it.id })
        val timed = items[0]
        // 15:59 UTC is 23:59 in Manila.
        assertEquals(LocalDate.of(2026, 11, 5).atTime(15, 59).toInstant(ZoneOffset.UTC).toEpochMilli(), timed.dueAt)
        assertTrue(timed.hasTime)
        assertFalse(timed.turnedIn)
        val dateOnly = items[1]
        assertFalse(dateOnly.hasTime)
        assertTrue(dateOnly.turnedIn)
        assertEquals(LocalDate.of(2026, 11, 6).atTime(23, 59).atZone(manila).toInstant().toEpochMilli(), dateOnly.dueAt)
    }

    @Test
    fun turnedInReadsSubmissionStates() {
        val raw = """{"studentSubmissions":[{"courseWorkId":"a","state":"TURNED_IN"},{"courseWorkId":"b","state":"CREATED"},{"courseWorkId":"c","state":"RETURNED"}]}"""
        assertEquals(setOf("a", "c"), ClassroomSync.parseTurnedIn(ComposioJson.parse(raw)))
    }

    @Test
    fun gmailMessagesAreParsedTolerantly() {
        val raw = """{"data":{"messages":[
            {"messageId":"m1","threadId":"t1","sender":"Prof. Santos <santos@school.edu>","subject":"Quiz moved","messageTimestamp":"2026-11-02T08:30:00Z","labelIds":["UNREAD","IMPORTANT","INBOX"],"preview":{"body":"The quiz  is now\non Friday."}},
            {"id":"m2","from":"news@shop.com","snippet":"Sale","internalDate":"1793520000000","labelIds":["INBOX"]}
        ]}}"""
        val mail = GmailSync.parseMessages(ComposioJson.parse(raw))
        assertEquals(2, mail.size)
        assertEquals("Prof. Santos", mail[0].from)
        assertEquals("Quiz moved", mail[0].subject)
        assertEquals("The quiz is now on Friday.", mail[0].snippet)
        assertTrue(mail[0].unread && mail[0].important)
        assertEquals(1793608200000L, mail[0].receivedAt)
        assertEquals("(no subject)", mail[1].subject)
        assertEquals(1793520000000L, mail[1].receivedAt)
        assertFalse(mail[1].unread)
    }

    @Test
    fun timeParsingHandlesSecondsAndRfc() {
        assertEquals(1_700_000_000_000L, GmailSync.parseTime("1700000000"))
        assertNotNull(GmailSync.parseTime("Mon, 2 Nov 2026 08:30:00 +0800").takeIf { it > 0 })
        assertEquals(0L, GmailSync.parseTime("garbage"))
    }
}
