package dev.jasper.ishav2.net

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Turns arbitrary JSON Schema (Composio tools) into the subset Gemini accepts.
 * Resolves local $refs and folds allOf/oneOf/const into forms the API knows.
 */
object SchemaSanitizer {
    private val allowed = setOf(
        "type", "title", "description", "format", "nullable", "enum", "maxItems", "minItems",
        "properties", "required", "minProperties", "maxProperties", "minLength", "maxLength",
        "pattern", "example", "anyOf", "propertyOrdering", "default", "items", "minimum",
        "maximum"
    )

    fun sanitize(schema: JsonObject?): JsonObject {
        if (schema == null) return emptyObject()
        val dereferenced = dereference(schema, schema, 0)
        val cleaned = clean(dereferenced, 0) as? JsonObject ?: return emptyObject()
        val map = cleaned.toMutableMap()
        val type = map["type"]
        if (type == null) map["type"] = JsonPrimitive("object")
        if (typeText(type) == "object" && map["properties"] == null) {
            map["properties"] = buildJsonObject { }
        }
        return JsonObject(map)
    }

    private fun emptyObject(): JsonObject = buildJsonObject { put("type", "object"); put("properties", buildJsonObject { }) }

    private fun typeText(element: JsonElement?): String? = when (element) {
        is JsonPrimitive -> element.contentOrNull
        is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .firstOrNull { it != "null" }
        else -> null
    }

    private fun dereference(node: JsonElement, root: JsonObject, depth: Int): JsonElement {
        if (depth > 12) return node
        if (node is JsonArray) {
            return JsonArray(node.map { dereference(it, root, depth + 1) })
        }
        if (node !is JsonObject) return node
        val ref = (node["\$ref"] as? JsonPrimitive)?.contentOrNull
        if (ref != null && ref.startsWith("#/")) {
            var target: JsonElement = root
            for (part in ref.removePrefix("#/").split('/')) {
                val key = part.replace("~1", "/").replace("~0", "~")
                target = (target as? JsonObject)?.get(key) ?: return node
            }
            val merged = (target as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            node.forEach { (key, value) -> if (key != "\$ref") merged[key] = value }
            return dereference(JsonObject(merged), root, depth + 1)
        }
        return JsonObject(node.mapValues { (_, value) -> dereference(value, root, depth + 1) })
    }

    private fun clean(node: JsonElement, depth: Int): JsonElement {
        if (depth > 12) return node
        if (node is JsonArray) return JsonArray(node.map { clean(it, depth + 1) })
        if (node !is JsonObject) return node

        val out = linkedMapOf<String, JsonElement>()
        val allOf = node["allOf"] as? JsonArray
        if (allOf != null) {
            for (branch in allOf) {
                val part = clean(branch, depth + 1) as? JsonObject ?: continue
                for ((key, value) in part) {
                    when (key) {
                        "properties" -> {
                            val existing = out["properties"] as? JsonObject
                            val merge = value as? JsonObject ?: continue
                            out["properties"] = JsonObject((existing ?: emptyObject()) + merge)
                        }
                        "required" -> {
                            val existing = (out["required"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
                            val add = (value as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
                            out["required"] = JsonArray((existing + add).distinct().map { JsonPrimitive(it) })
                        }
                        else -> if (!out.containsKey(key)) out[key] = value
                    }
                }
            }
        }

        for ((key, value) in node) {
            when (key) {
                "\$ref", "\$schema", "\$id", "\$defs", "definitions", "allOf", "oneOf", "const" -> Unit
                "properties" -> {
                    val properties = value as? JsonObject ?: continue
                    out["properties"] = JsonObject(
                        properties.mapValues { (_, sub) -> clean(sub, depth + 1) }
                    )
                }
                "items" -> {
                    val item = if (value is JsonArray) value.firstOrNull() else value
                    if (item != null) out["items"] = clean(item, depth + 1)
                }
                "anyOf" -> {
                    val branches = value as? JsonArray ?: continue
                    out["anyOf"] = JsonArray(branches.map { clean(it, depth + 1) })
                }
                "enum", "default", "example" -> out[key] = value
                "type" -> {
                    val type = normalizeType(value)
                    if (type != null) {
                        out["type"] = JsonPrimitive(type)
                        val nullable = value is JsonArray &&
                            value.any { (it as? JsonPrimitive)?.contentOrNull == "null" }
                        if (nullable) out["nullable"] = JsonPrimitive(true)
                    }
                }
                else -> if (allowed.contains(key)) {
                    out[key] = clean(value, depth + 1)
                }
            }
        }

        // Fold const into enum.
        (node["const"] as? JsonElement)?.let { constant ->
            if (constant !is JsonNull) out["enum"] = buildJsonArray { add(constant) }
        }
        // Fold oneOf into anyOf.
        (node["oneOf"] as? JsonArray)?.let { branches ->
            out["anyOf"] = JsonArray(branches.map { clean(it, depth + 1) })
        }

        return JsonObject(out)
    }

    private fun normalizeType(value: JsonElement): String? {
        val allowedTypes = setOf("object", "array", "string", "number", "integer", "boolean")
        return when (value) {
            is JsonPrimitive -> value.contentOrNull?.takeIf { allowedTypes.contains(it) }
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .firstOrNull { allowedTypes.contains(it) }
            else -> null
        }
    }
}
