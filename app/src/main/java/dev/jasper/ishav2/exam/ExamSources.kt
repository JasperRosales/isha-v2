package dev.jasper.ishav2.exam

import dev.jasper.ishav2.data.ExamSourceFile
import java.io.File
import java.util.zip.ZipFile

/**
 * Study material ready for the question writer: text read on the phone, plus
 * files only Gemini can read (PDFs and photos).
 */
data class PreparedSource(
    val text: String,
    val media: List<ExamSourceFile>,
    /** Parts of the material that could not be used, for a friendly note. */
    val skipped: List<String> = emptyList()
) {
    val isEmpty: Boolean get() = text.isBlank() && media.isEmpty()
}

object ExamSources {
    /** Types the file picker offers. */
    val PICKER_TYPES = arrayOf(
        "text/*",
        "application/pdf",
        "image/*",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        // Many file managers report .md as a generic binary.
        "application/octet-stream"
    )

    const val MAX_TEXT_CHARS = 60_000
    const val MAX_MEDIA_BYTES = 15L * 1024 * 1024

    fun prepare(files: List<ExamSourceFile>, pasted: String): PreparedSource {
        val text = StringBuilder()
        val media = mutableListOf<ExamSourceFile>()
        val skipped = mutableListOf<String>()
        if (pasted.isNotBlank()) text.append(pasted.trim()).append("\n\n")
        var mediaBytes = 0L
        for (source in files) {
            val file = File(source.path)
            if (!file.exists()) {
                skipped += "${source.name} (missing)"
                continue
            }
            when (kindOf(source)) {
                Kind.TEXT -> text.append("# ${source.name}\n\n").append(readText(file, source.mime)).append("\n\n")
                Kind.DOCX -> text.append("# ${source.name}\n\n").append(docxText(file)).append("\n\n")
                Kind.PPTX -> text.append("# ${source.name}\n\n").append(pptxText(file)).append("\n\n")
                Kind.MEDIA -> {
                    if (mediaBytes + file.length() > MAX_MEDIA_BYTES) {
                        skipped += "${source.name} (too large together with the other files)"
                    } else {
                        mediaBytes += file.length()
                        media += source
                    }
                }
                Kind.UNSUPPORTED -> skipped += "${source.name} (unsupported type)"
            }
        }
        val clean = text.toString().replace(Regex("\n{3,}"), "\n\n").trim()
        if (clean.length > MAX_TEXT_CHARS) skipped += "the end of very long text (only the first ${MAX_TEXT_CHARS / 1000}k characters are used)"
        return PreparedSource(clean.take(MAX_TEXT_CHARS), media, skipped)
    }

    enum class Kind { TEXT, DOCX, PPTX, MEDIA, UNSUPPORTED }

    fun kindOf(source: ExamSourceFile): Kind {
        val ext = source.name.substringAfterLast('.', "").lowercase()
        val mime = source.mime.lowercase()
        return when {
            ext in TEXT_EXTENSIONS || mime.startsWith("text/") ||
                mime in setOf("application/json", "application/xml", "application/x-markdown") -> Kind.TEXT
            ext == "docx" || mime.endsWith("wordprocessingml.document") -> Kind.DOCX
            ext == "pptx" || mime.endsWith("presentationml.presentation") -> Kind.PPTX
            mime == "application/pdf" || ext == "pdf" || mime.startsWith("image/") -> Kind.MEDIA
            else -> Kind.UNSUPPORTED
        }
    }

    private val TEXT_EXTENSIONS = setOf("md", "markdown", "txt", "text", "csv", "tsv", "json", "html", "htm", "xml", "rtf", "tex", "org", "rst")

    private fun readText(file: File, mime: String): String {
        val raw = file.readText()
        return if (mime.contains("html") || file.extension.lowercase() in setOf("html", "htm")) {
            htmlText(raw)
        } else raw
    }

    fun htmlText(html: String): String = html
        .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
        .replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</h\\d>|</li>"), "\n")
        .replace(Regex("<[^>]+>"), " ")
        .let(::decodeEntities)
        .replace(Regex("[ \\t]+"), " ")
        .trim()

    private fun docxText(file: File): String = ZipFile(file).use { zip ->
        val entry = zip.getEntry("word/document.xml") ?: return ""
        officeXmlText(zip.getInputStream(entry).bufferedReader().readText(), paragraphTag = "w:p", textTag = "w:t")
    }

    private fun pptxText(file: File): String = ZipFile(file).use { zip ->
        val slides = zip.entries().asSequence()
            .filter { SLIDE.matches(it.name) }
            .sortedBy { SLIDE.find(it.name)!!.groupValues[1].toInt() }
            .toList()
        slides.mapIndexed { i, entry ->
            val text = officeXmlText(zip.getInputStream(entry).bufferedReader().readText(), paragraphTag = "a:p", textTag = "a:t")
            "Slide ${i + 1}:\n$text"
        }.joinToString("\n\n")
    }

    private val SLIDE = Regex("ppt/slides/slide(\\d+)\\.xml")

    /** Text from Office XML: [textTag] runs joined, one line per [paragraphTag]. */
    fun officeXmlText(xml: String, paragraphTag: String, textTag: String): String {
        val paragraph = Regex("<$paragraphTag[\\s>][\\s\\S]*?</$paragraphTag>|<$paragraphTag/>")
        val run = Regex("<$textTag(?:\\s[^>]*)?>([\\s\\S]*?)</$textTag>")
        return paragraph.findAll(xml)
            .map { p -> run.findAll(p.value).joinToString("") { decodeEntities(it.groupValues[1]) }.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    private fun decodeEntities(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&#39;", "'").replace("&nbsp;", " ")
        .replace("&amp;", "&")
}
