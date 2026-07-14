package com.dmvp.app.utils

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object CanonicalJson {

    private val gson = Gson()

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

            else -> element
        }
    }
}
