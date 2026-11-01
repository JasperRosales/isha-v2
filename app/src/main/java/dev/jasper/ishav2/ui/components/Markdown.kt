package dev.jasper.ishav2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jasper.ishav2.ui.theme.IshaAccent
import dev.jasper.ishav2.ui.theme.IshaCodeBg
import dev.jasper.ishav2.ui.theme.IshaInk
import dev.jasper.ishav2.ui.theme.IshaMuted
import dev.jasper.ishav2.ui.theme.IshaPalette
import dev.jasper.ishav2.ui.theme.IshaStroke
import dev.jasper.ishav2.ui.theme.LocalIshaPalette

/** A small, dependency-free Markdown renderer for chat replies. */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = IshaInk
) {
    val blocks = remember(text) { parseBlocks(text) }
    SelectionContainer {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
            for (block in blocks) {
                when (block) {
                    is Block.Paragraph -> InlineText(
                        text = block.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = color
                    )
                    is Block.Heading -> InlineText(
                        text = block.text,
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineMedium
                            2 -> MaterialTheme.typography.titleLarge
                            else -> MaterialTheme.typography.titleMedium
                        },
                        color = color
                    )
                    is Block.Code -> CodeBlock(block)
                    is Block.ListBlock -> ListBlock(block, color)
                    is Block.Quote -> Row(Modifier.fillMaxWidth()) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .background(IshaAccent.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        InlineText(
                            text = block.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = IshaMuted,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Block.Rule -> HorizontalDivider(color = IshaStroke, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun ListBlock(block: Block.ListBlock, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        block.items.forEachIndexed { index, item ->
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = if (block.ordered) "${index + 1}." else "•",
                    color = IshaAccent,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.width(24.dp)
                )
                InlineText(
                    text = item,
                    style = MaterialTheme.typography.bodyLarge,
                    color = color,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(block: Block.Code) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(IshaCodeBg)
            .border(1.dp, IshaStroke, shape)
            .padding(12.dp)
    ) {
        if (!block.language.isNullOrBlank()) {
            Text(
                text = block.language!!,
                color = IshaMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Text(
                text = block.text,
                color = IshaInk,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                softWrap = false
            )
        }
    }
}

@Composable
private fun InlineText(
    text: String,
    style: TextStyle,
    color: Color = IshaInk,
    modifier: Modifier = Modifier
) {
    val palette = LocalIshaPalette.current
    val annotated = remember(text, color, palette) { inlineMarkdown(text, color, palette) }
    Text(
        text = annotated,
        style = style.copy(color = color),
        modifier = modifier
    )
}

private fun inlineMarkdown(text: String, color: Color, palette: IshaPalette): AnnotatedString = buildAnnotatedString {
    appendInline(this, text, color, palette)
}

private fun appendInline(
    builder: AnnotatedString.Builder,
    source: String,
    color: Color,
    palette: IshaPalette
) {
    var i = 0
    while (i < source.length) {
        when {
            source.startsWith("**", i) || source.startsWith("__", i) -> {
                val marker = source.substring(i, i + 2)
                val end = source.indexOf(marker, i + 2)
                if (end < 0) {
                    builder.append(source[i])
                    i++
                } else {
                    builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color))
                    appendInline(builder, source.substring(i + 2, end), color, palette)
                    builder.pop()
                    i = end + 2
                }
            }
            source[i] == '`' -> {
                val end = source.indexOf('`', i + 1)
                if (end < 0) {
                    builder.append(source[i])
                    i++
                } else {
                    builder.pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = palette.codeBg,
                            color = palette.ink,
                            fontSize = 13.sp
                        )
                    )
                    builder.append(source.substring(i + 1, end))
                    builder.pop()
                    i = end + 1
                }
            }
            source.startsWith("[", i) -> {
                val labelEnd = source.indexOf("](", i + 1)
                val urlEnd = if (labelEnd > 0) source.indexOf(')', labelEnd + 2) else -1
                if (labelEnd > 0 && urlEnd > 0) {
                    val label = source.substring(i + 1, labelEnd)
                    val url = source.substring(labelEnd + 2, urlEnd).trim()
                    if (url.isNotBlank()) {
                        builder.withLink(
                            LinkAnnotation.Url(
                                url = url,
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = palette.accent,
                                        textDecoration = TextDecoration.Underline
                                    )
                                )
                            )
                        ) {
                            appendInline(this, label, color, palette)
                        }
                    } else {
                        builder.append(label)
                    }
                    i = urlEnd + 1
                } else {
                    builder.append(source[i])
                    i++
                }
            }
            source.startsWith("https://", i) || source.startsWith("http://", i) -> {
                var end = i
                while (end < source.length &&
                    !source[end].isWhitespace() &&
                    source[end] != ')' &&
                    source[end] != ']'
                ) {
                    end++
                }
                val url = source.substring(i, end)
                builder.withLink(
                    LinkAnnotation.Url(
                        url = url,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = palette.accent,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                    )
                ) {
                    append(url)
                }
                i = end
            }
            (source[i] == '*' || source[i] == '_') && i + 1 < source.length -> {
                val marker = source[i]
                val end = source.indexOf(marker, i + 1)
                if (end < 0 || end == i + 1) {
                    builder.append(source[i])
                    i++
                } else {
                    builder.pushStyle(
                        SpanStyle(
                            fontStyle = FontStyle.Italic,
                            color = color
                        )
                    )
                    appendInline(builder, source.substring(i + 1, end), color, palette)
                    builder.pop()
                    i = end + 1
                }
            }
            else -> {
                builder.append(source[i])
                i++
            }
        }
    }
}

private sealed interface Block {
    data class Paragraph(val text: String) : Block
    data class Heading(val level: Int, val text: String) : Block
    data class Code(val language: String?, val text: String) : Block
    data class ListBlock(val items: List<String>, val ordered: Boolean) : Block
    data class Quote(val text: String) : Block
    data object Rule : Block
}

private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val BULLET = Regex("^[-*+]\\s+(.*)$")
private val ORDERED = Regex("^\\d+[.)]\\s+(.*)$")
private val RULES = setOf("---", "***", "___")

private fun isBlockStart(line: String): Boolean {
    val trimmed = line.trimStart()
    return line.isBlank() ||
        trimmed.startsWith("```") ||
        HEADING.matches(trimmed) ||
        BULLET.matches(trimmed) ||
        ORDERED.matches(trimmed) ||
        trimmed.startsWith(">") ||
        trimmed.trim() in RULES
}

private fun parseBlocks(input: String): List<Block> {
    val lines = input.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<Block>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i].trimEnd()
        val trimmed = line.trimStart()
        when {
            line.isBlank() -> i++

            trimmed.startsWith("```") -> {
                val language = trimmed.removePrefix("```").trim().ifBlank { null }
                val code = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    code += lines[i]
                    i++
                }
                if (i < lines.size) i++
                blocks += Block.Code(language, code.joinToString("\n"))
            }

            HEADING.matches(line) -> {
                val match = HEADING.find(line)!!
                blocks += Block.Heading(match.groupValues[1].length, match.groupValues[2].trim())
                i++
            }

            trimmed in RULES -> {
                blocks += Block.Rule
                i++
            }

            trimmed.startsWith(">") -> {
                val quote = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    quote += lines[i].trimStart().removePrefix(">").trimStart()
                    i++
                }
                blocks += Block.Quote(quote.joinToString("\n"))
            }

            BULLET.matches(trimmed) || ORDERED.matches(trimmed) -> {
                val ordered = ORDERED.matches(trimmed)
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val candidate = lines[i].trimStart()
                    val match = if (ordered) ORDERED.find(candidate) else BULLET.find(candidate)
                    if (match == null) break
                    items += match.groupValues[1].trim()
                    i++
                }
                blocks += Block.ListBlock(items, ordered)
            }

            else -> {
                val paragraph = mutableListOf<String>()
                while (i < lines.size && !isBlockStart(lines[i])) {
                    paragraph += lines[i].trim()
                    i++
                }
                if (paragraph.isEmpty()) {
                    i++
                } else {
                    blocks += Block.Paragraph(paragraph.joinToString(" "))
                }
            }
        }
    }
    return blocks
}
