package to.wim.watchlist.data.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// SIMKL's responses are loosely typed (ids are sometimes numbers, sometimes strings; optional
// blocks may be missing or null), so responses are read as JsonElements through these
// forgiving accessors instead of strict data classes.

fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

fun JsonElement?.asArray(): JsonArray? = this as? JsonArray

fun JsonElement?.objects(): List<JsonObject> = (this as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

fun JsonObject.str(key: String): String? {
    val p = this[key] as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.content.takeIf { it.isNotBlank() && it != "null" }
}

fun JsonObject.int(key: String): Int? = str(key)?.toDoubleOrNull()?.toInt()

fun JsonObject.long(key: String): Long? = str(key)?.toDoubleOrNull()?.toLong()

fun JsonObject.double(key: String): Double? = str(key)?.toDoubleOrNull()

fun JsonObject.bool(key: String): Boolean? =
    when (str(key)?.lowercase()) {
        "true", "yes", "1" -> true
        "false", "no", "0" -> false
        else -> null
    }

fun JsonObject.strings(key: String): List<String> =
    arr(key)?.mapNotNull { (it as? JsonPrimitive)?.takeUnless { p -> p is JsonNull }?.content }.orEmpty()
