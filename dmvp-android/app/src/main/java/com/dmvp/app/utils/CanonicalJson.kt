package com.dmvp.app.utils

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive  // ── Step: Added for number handling ──

object CanonicalJson {

    private val gson = GsonBuilder().disableHtmlEscaping().create()

    fun canonicalize(
        value: Any?,
        excludeSignature: Boolean = false
    ): String {
        val json = gson.toJson(value)
        val element = JsonParser().parse(json)
        val sorted = sortJson(element, excludeSignature)
        return gson.toJson(sorted)
    }

    fun canonicalizeToUtf8(
        value: Any?,
        excludeSignature: Boolean = false
    ): ByteArray {
        return canonicalize(value, excludeSignature).toByteArray(Charsets.UTF_8)
    }

    private fun sortJson(
        element: JsonElement,
        excludeSignature: Boolean
    ): JsonElement {
        return when {
            element.isJsonObject -> {
                val source = element.asJsonObject
                val sorted = JsonObject()

                source.entrySet()
                    .map { it.key }
                    .filterNot { excludeSignature && it == "signature" }
                    .sorted()
                    .forEach { key ->
                        sorted.add(key, sortJson(source.get(key), excludeSignature))
                    }

                sorted
            }

            element.isJsonArray -> {
                val sortedArray = JsonArray()

                for (item in element.asJsonArray) {
                    sortedArray.add(sortJson(item, excludeSignature))
                }

                sortedArray
            }

            // ── Step: Handle JsonPrimitive numbers correctly ──
            element.isJsonPrimitive -> {
                val p = element.asJsonPrimitive
                if (p.isNumber) {
                    val d = p.asDouble
                    if (d.isFinite() && d % 1.0 == 0.0) {
                        JsonPrimitive(d.toLong())
                    } else {
                        p
                    }
                } else {
                    p
                }
            }

            else -> element
        }
    }
}
