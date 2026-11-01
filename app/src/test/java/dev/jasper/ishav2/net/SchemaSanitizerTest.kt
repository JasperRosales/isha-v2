package dev.jasper.ishav2.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaSanitizerTest {
    private fun schema(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `removes keys gemini rejects and keeps useful structure`() {
        val cleaned = SchemaSanitizer.sanitize(
            schema(
                """
                {
                  "type": "object",
                  "additionalProperties": false,
                  "${'$'}defs": {
                    "item": {
                      "type": "object",
                      "properties": { "id": { "type": "string" } }
                    }
                  },
                  "properties": {
                    "name": { "type": ["string", "null"] },
                    "items": {
                      "type": "array",
                      "items": { "${'$'}ref": "#/${'$'}defs/item" }
                    },
                    "mode": { "oneOf": [{ "const": "fast" }, { "const": "slow" }] }
                  },
                  "required": ["name"]
                }
                """.trimIndent()
            )
        )
        val text = cleaned.toString()
        assertFalse(text.contains("additionalProperties"))
        assertFalse(text.contains("${'$'}ref"))
        assertFalse(text.contains("${'$'}defs"))

        assertEquals("object", cleaned["type"]?.jsonPrimitive?.content)
        val properties = cleaned["properties"]!!.jsonObject
        assertTrue(properties.containsKey("name"))
        assertTrue(properties.containsKey("items"))
        val name = properties["name"]!!.jsonObject
        assertEquals("string", name["type"]?.jsonPrimitive?.content)
        assertTrue(name["nullable"]?.jsonPrimitive?.boolean == true)
        val item = properties["items"]!!.jsonObject["items"]!!.jsonObject
        assertEquals("object", item["type"]?.jsonPrimitive?.content)
        assertTrue(item["properties"]!!.jsonObject.containsKey("id"))
        val mode = properties["mode"]!!.jsonObject
        assertTrue(mode.containsKey("anyOf"))
    }

    @Test
    fun `folds allOf properties and required lists`() {
        val cleaned = SchemaSanitizer.sanitize(
            schema(
                """
                {
                  "allOf": [
                    { "type": "object", "properties": { "a": { "type": "string" } }, "required": ["a"] },
                    { "properties": { "b": { "type": "integer" } }, "required": ["b"] }
                  ]
                }
                """.trimIndent()
            )
        )
        val properties = cleaned["properties"]!!.jsonObject
        assertTrue(properties.containsKey("a"))
        assertTrue(properties.containsKey("b"))
        val required = cleaned["required"].toString()
        assertTrue(required.contains("a"))
        assertTrue(required.contains("b"))
    }

    @Test
    fun `empty or broken schema becomes an empty object`() {
        val cleaned = SchemaSanitizer.sanitize(schema("""{"type":"string"}"""))
        // Non-object roots are still returned with their type; the request builder
        // only relies on Gemini receiving a valid schema.
        assertTrue(cleaned.containsKey("type"))

        val fallback = SchemaSanitizer.sanitize(null)
        assertEquals("object", fallback["type"]?.jsonPrimitive?.content)
        assertTrue(fallback.containsKey("properties"))
    }
}
