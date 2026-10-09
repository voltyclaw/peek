package app.pane.android.data.history

/** Blocking SQL session. Callers serialize access. Android and tests each supply one. */
internal interface HistorySql {
    fun exec(sql: String, args: List<Any?> = emptyList())
    fun query(sql: String, args: List<Any?> = emptyList()): List<HistorySqlRow>
    fun <T> transaction(block: () -> T): T
}

internal class HistorySqlRow(private val values: Map<String, Any?>) {
    fun text(name: String): String? = values[name]?.toString()

    fun long(name: String): Long? = when (val value = values[name]) {
        null -> null
        is Number -> value.toLong()
        else -> value.toString().toLongOrNull()
    }
}
