package dev.jasper.ishav2.ui.voice

/**
 * Turns a Markdown reply into text that sounds natural when read by text-to-speech:
 * no asterisks, hashes, pipes, raw URLs or emoji names. Code blocks are not read out.
 */
fun speakableText(markdown: String): String {
    var s = markdown.replace("\r\n", "\n")

    // Code blocks are for reading, not listening.
    s = FENCE.replace(s) { "\nThe code is on screen.\n" }

    val lines = s.lines().mapNotNull { raw ->
        var line = raw.trim()
        when {
            line.isEmpty() -> ""
            TABLE_RULE.matches(line) -> null
            line.startsWith("|") -> line.trim('|').split('|')
                .map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
            HR.matches(line) -> ""
            else -> {
                line = line.replace(HEADING, "")
                line = line.replace(QUOTE, "")
                line = line.replace(BULLET, "")
                line = line.replace(TASK_BOX, "")
                line
            }
        }
    }
    s = lines.joinToString("\n")

    s = IMAGE.replace(s) { it.groupValues[1] }
    s = LINK.replace(s) { it.groupValues[1] }
    s = URL.replace(s) { match ->
        val host = match.groupValues[1].removePrefix("www.")
        "a link to $host"
    }
    s = INLINE_CODE.replace(s) { it.groupValues[1] }
    s = s.replace("**", "").replace("__", "").replace("~~", "")
    s = STAR.replace(s, "")
    s = EMOJI.replace(s, "")
    s = s.replace("→", " to ").replace("&", " and ")

    // Tidy whitespace but keep paragraph breaks: they become pauses.
    s = s.lines().joinToString("\n") { it.replace(SPACES, " ").trim() }
    s = s.replace(BLANKS, "\n\n")
    return s.trim()
}

/**
 * Splits speech into pieces no longer than [max] characters (TTS engines reject
 * longer input), breaking at paragraphs, then sentences, then spaces.
 */
fun speechChunks(text: String, max: Int): List<String> {
    require(max > 0)
    val out = mutableListOf<String>()
    for (paragraph in text.split(BLANKS_OR_LINES)) {
        val p = paragraph.trim()
        if (p.isEmpty()) continue
        if (p.length <= max) {
            out += p
            continue
        }
        val current = StringBuilder()
        for (sentence in p.split(SENTENCE_END)) {
            var rest = sentence.trim()
            while (rest.length > max) {
                if (current.isNotEmpty()) {
                    out += current.toString()
                    current.clear()
                }
                val cut = rest.lastIndexOf(' ', max).takeIf { it > 0 } ?: max
                out += rest.substring(0, cut).trim()
                rest = rest.substring(cut).trim()
            }
            if (rest.isEmpty()) continue
            if (current.isNotEmpty() && current.length + 1 + rest.length > max) {
                out += current.toString()
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(rest)
        }
        if (current.isNotEmpty()) out += current.toString()
    }
    return out
}

private val FENCE = Regex("```[\\s\\S]*?(```|$)")
private val TABLE_RULE = Regex("^\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?$")
private val HR = Regex("^([-*_]\\s*){3,}$")
private val HEADING = Regex("^#{1,6}\\s+")
private val QUOTE = Regex("^(>\\s?)+")
private val BULLET = Regex("^[-*+•]\\s+")
private val TASK_BOX = Regex("^\\[[ xX]]\\s+")
private val IMAGE = Regex("!\\[([^\\]]*)]\\([^)]*\\)")
private val LINK = Regex("\\[([^\\]]+)]\\([^)]*\\)")
private val URL = Regex("https?://([^/\\s)]+)[^\\s)]*")
private val INLINE_CODE = Regex("`([^`]*)`")
private val STAR = Regex("(?<![\\w*])\\*(?=\\S)|(?<=\\S)\\*(?![\\w*])")
private val EMOJI = Regex("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2B00}-\\x{2BFF}\\x{FE0F}\\x{200D}]")
private val SPACES = Regex("[ \\t]+")
private val BLANKS = Regex("\\n{3,}")
private val BLANKS_OR_LINES = Regex("\\n+")
private val SENTENCE_END = Regex("(?<=[.!?])\\s+")
