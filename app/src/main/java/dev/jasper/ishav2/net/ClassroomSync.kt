package dev.jasper.ishav2.net

import dev.jasper.ishav2.data.Deadline
import dev.jasper.ishav2.data.DeadlineStore
import dev.jasper.ishav2.net.ComposioJson.long
import dev.jasper.ishav2.net.ComposioJson.obj
import dev.jasper.ishav2.net.ComposioJson.str
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Pulls Google Classroom coursework with due dates through Composio. */
class ClassroomSync(private val calls: ComposioCalls) {

    data class Course(val id: String, val name: String)
    data class Announcement(val id: String, val course: String, val text: String, val createdAt: Long)

    /** Announcements posted after [since] across active courses, newest first. */
    suspend fun fetchAnnouncements(since: Long): List<Announcement> = coroutineScope {
        val courses = parseCourses(
            calls.run(
                COURSES_LIST,
                buildJsonObject {
                    put("studentId", "me")
                    put("courseStates", buildJsonArray { add("ACTIVE") })
                    put("pageSize", 30)
                }
            )
        ).take(MAX_COURSES)
        val gate = Semaphore(4)
        courses.map { course ->
            async {
                gate.withPermit {
                    runCatching {
                        parseAnnouncements(
                            calls.run(
                                ANNOUNCEMENTS_LIST,
                                buildJsonObject {
                                    put("courseId", course.id)
                                    put("orderBy", "updateTime desc")
                                    put("pageSize", 5)
                                }
                            ),
                            course
                        )
                    }.getOrDefault(emptyList())
                }
            }
        }.awaitAll().flatten().filter { it.createdAt > since }.sortedByDescending { it.createdAt }
    }

    suspend fun fetch(now: Long = System.currentTimeMillis()): List<Deadline> = coroutineScope {
        val courses = parseCourses(
            calls.run(
                COURSES_LIST,
                buildJsonObject {
                    put("studentId", "me")
                    put("courseStates", buildJsonArray { add("ACTIVE") })
                    put("pageSize", 30)
                }
            )
        ).take(MAX_COURSES)

        // A few courses at a time: fast, without hammering the API.
        val gate = Semaphore(4)
        val perCourse = courses.map { course ->
            async {
                gate.withPermit {
                    val work = runCatching {
                        calls.run(
                            COURSE_WORK_LIST,
                            buildJsonObject {
                                put("courseId", course.id)
                                put("orderBy", "dueDate desc")
                                put("pageSize", 40)
                            }
                        )
                    }.getOrElse { return@withPermit Result.failure<List<Deadline>>(it) }
                    val turnedIn = runCatching {
                        parseTurnedIn(
                            calls.run(
                                SUBMISSIONS_LIST,
                                buildJsonObject {
                                    put("courseId", course.id)
                                    put("courseWorkId", "-")
                                    put("userId", "me")
                                    put("pageSize", 100)
                                }
                            )
                        )
                    }.getOrDefault(emptySet())
                    Result.success(parseCourseWork(work, course, turnedIn, now))
                }
            }
        }.awaitAll()
        // Every course failed: report it instead of wiping the saved list.
        if (perCourse.isNotEmpty() && perCourse.all { it.isFailure }) {
            throw perCourse.first().exceptionOrNull() ?: ApiException(500, "Could not read Classroom.")
        }
        perCourse.flatMap { it.getOrDefault(emptyList()) }
    }

    companion object {
        private const val MAX_COURSES = 15
        private const val LOOKBACK_MS = 14L * 24 * 60 * 60 * 1000

        // Current Composio slugs first, older spellings as a fallback.
        val COURSES_LIST = listOf("GOOGLE_CLASSROOM_COURSES_LIST", "GOOGLECLASSROOM_LIST_COURSES")
        val COURSE_WORK_LIST = listOf("GOOGLE_CLASSROOM_COURSE_WORK_LIST", "GOOGLECLASSROOM_LIST_COURSE_WORK")
        val ANNOUNCEMENTS_LIST = listOf(
            "GOOGLE_CLASSROOM_COURSES_ANNOUNCEMENTS_LIST",
            "GOOGLECLASSROOM_LIST_ANNOUNCEMENTS"
        )
        val SUBMISSIONS_LIST = listOf(
            "GOOGLE_CLASSROOM_COURSE_WORK_STUDENT_SUBMISSIONS_LIST",
            "GOOGLECLASSROOM_LIST_STUDENT_SUBMISSIONS"
        )

        fun parseCourses(element: JsonElement): List<Course> =
            ComposioJson.findArray(element, setOf("courses")).orEmpty().mapNotNull { node ->
                val obj = ComposioJson.unwrap(node) as? JsonObject ?: return@mapNotNull null
                val id = obj.str("id") ?: return@mapNotNull null
                val state = obj.str("courseState")
                if (state != null && state != "ACTIVE") return@mapNotNull null
                Course(id, obj.str("name", "descriptionHeading") ?: "Class")
            }

        fun parseAnnouncements(element: JsonElement, course: Course): List<Announcement> =
            ComposioJson.findArray(element, setOf("announcements")).orEmpty().mapNotNull { node ->
                val obj = ComposioJson.unwrap(node) as? JsonObject ?: return@mapNotNull null
                val id = obj.str("id") ?: return@mapNotNull null
                if (obj.str("state")?.let { it != "PUBLISHED" } == true) return@mapNotNull null
                val created = GmailSync.parseTime(obj.str("creationTime", "updateTime"))
                Announcement(
                    id = id,
                    course = course.name,
                    text = obj.str("text")?.replace(Regex("\\s+"), " ")?.trim().orEmpty().ifBlank { "New announcement" },
                    createdAt = created
                )
            }

        /** Course work ids the user has turned in (or that were returned). */
        fun parseTurnedIn(element: JsonElement): Set<String> =
            ComposioJson.findArray(element, setOf("studentSubmissions", "student_submissions", "submissions"))
                .orEmpty()
                .mapNotNull { node ->
                    val obj = ComposioJson.unwrap(node) as? JsonObject ?: return@mapNotNull null
                    val state = obj.str("state")
                    if (state == "TURNED_IN" || state == "RETURNED") obj.str("courseWorkId") else null
                }
                .toSet()

        fun parseCourseWork(
            element: JsonElement,
            course: Course,
            turnedIn: Set<String>,
            now: Long,
            zone: ZoneId = ZoneId.systemDefault()
        ): List<Deadline> =
            ComposioJson.findArray(element, setOf("courseWork", "course_work", "coursework")).orEmpty()
                .mapNotNull { node ->
                    val obj = ComposioJson.unwrap(node) as? JsonObject ?: return@mapNotNull null
                    val id = obj.str("id") ?: return@mapNotNull null
                    if (obj.str("state")?.let { it != "PUBLISHED" } == true) return@mapNotNull null
                    val date = obj.obj("dueDate") ?: return@mapNotNull null
                    val year = date.long("year")?.toInt() ?: return@mapNotNull null
                    val month = date.long("month")?.toInt() ?: return@mapNotNull null
                    val day = date.long("day")?.toInt() ?: return@mapNotNull null
                    val local = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return@mapNotNull null
                    val time = obj.obj("dueTime")
                    // Classroom stores due date and time in UTC.
                    val dueAt = if (time != null) {
                        local.atTime((time.long("hours") ?: 0).toInt(), (time.long("minutes") ?: 0).toInt())
                            .toInstant(ZoneOffset.UTC).toEpochMilli()
                    } else {
                        DeadlineStore.endOfDay(local, zone)
                    }
                    if (dueAt < now - LOOKBACK_MS) return@mapNotNull null
                    Deadline(
                        id = "cw:${course.id}:$id",
                        title = obj.str("title") ?: "Assignment",
                        course = course.name,
                        dueAt = dueAt,
                        hasTime = time != null,
                        source = Deadline.SOURCE_CLASSROOM,
                        link = obj.str("alternateLink"),
                        notes = obj.str("description")?.take(500).orEmpty(),
                        turnedIn = id in turnedIn
                    )
                }
    }
}
