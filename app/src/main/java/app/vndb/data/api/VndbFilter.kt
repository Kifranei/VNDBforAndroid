package app.vndb.data.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = VndbFilterSerializer::class)
sealed class VndbFilter {
    data class Pred(
        val field: String,
        val op: String,
        val value: JsonElement,
    ) : VndbFilter()

    data class And(val children: List<VndbFilter>) : VndbFilter()
    data class Or(val children: List<VndbFilter>) : VndbFilter()

    companion object {
        fun pred(field: String, op: String, value: String) =
            Pred(field, op, JsonPrimitive(value))

        fun pred(field: String, op: String, value: Int) =
            Pred(field, op, JsonPrimitive(value))

        fun pred(field: String, op: String, value: Boolean) =
            Pred(field, op, JsonPrimitive(if (value) 1 else 0))

        fun pred(field: String, op: String, value: JsonElement) =
            Pred(field, op, value)

        fun search(query: String) = pred("search", "=", query)

        fun id(value: String) = pred("id", "=", value)

        fun and(vararg filters: VndbFilter?): VndbFilter {
            val items = filters.filterNotNull()
            return when (items.size) {
                0 -> Pred("id", "!=", JsonPrimitive(""))
                1 -> items.first()
                else -> And(items)
            }
        }

        fun or(vararg filters: VndbFilter?): VndbFilter {
            val items = filters.filterNotNull()
            return when (items.size) {
                0 -> Pred("id", "=", JsonPrimitive("__none__"))
                1 -> items.first()
                else -> Or(items)
            }
        }
    }
}

object VndbFilterSerializer : KSerializer<VndbFilter> {
    override val descriptor = JsonArray.serializer().descriptor

    override fun serialize(encoder: Encoder, value: VndbFilter) {
        val json = encoder as JsonEncoder
        json.encodeJsonElement(value.toJson())
    }

    override fun deserialize(decoder: Decoder): VndbFilter {
        val json = decoder as JsonDecoder
        return fromJson(json.decodeJsonElement().jsonArray)
    }

    private fun VndbFilter.toJson(): JsonArray = when (this) {
        is VndbFilter.Pred -> buildJsonArray {
            add(JsonPrimitive(field))
            add(JsonPrimitive(op))
            add(value)
        }
        is VndbFilter.And -> buildJsonArray {
            add(JsonPrimitive("and"))
            children.forEach { add(it.toJson()) }
        }
        is VndbFilter.Or -> buildJsonArray {
            add(JsonPrimitive("or"))
            children.forEach { add(it.toJson()) }
        }
    }

    private fun fromJson(array: JsonArray): VndbFilter {
        val head = array.first().jsonPrimitive.content
        return when (head) {
            "and" -> VndbFilter.And(array.drop(1).map { fromJson(it.jsonArray) })
            "or" -> VndbFilter.Or(array.drop(1).map { fromJson(it.jsonArray) })
            else -> VndbFilter.Pred(head, array[1].jsonPrimitive.content, array[2])
        }
    }
}

@Serializable
data class VndbQuery(
    val filters: VndbFilter? = null,
    val fields: String = "",
    val sort: String? = null,
    val reverse: Boolean? = null,
    val results: Int = 20,
    val page: Int = 1,
    val user: String? = null,
    val count: Boolean = false,
)

@Serializable
data class VndbPage<T>(
    val results: List<T> = emptyList(),
    val more: Boolean = false,
    val count: Int? = null,
)

@Serializable
data class UlistPatch(
    val vote: Int? = null,
    val notes: String? = null,
    val labels: List<Int>? = null,
    val labels_set: List<Int>? = null,
    val labels_unset: List<Int>? = null,
)
