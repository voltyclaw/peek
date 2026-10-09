package app.pane.android.data.history

import java.sql.Connection
import java.sql.DriverManager
import java.sql.Types

internal class JdbcHistorySql private constructor(
    private val connection: Connection,
) : HistorySql {
    override fun exec(sql: String, args: List<Any?>) {
        connection.prepareStatement(sql).use { statement ->
            bind(statement, args)
            if (statement.execute()) statement.resultSet?.close()
        }
    }

    override fun query(sql: String, args: List<Any?>): List<HistorySqlRow> {
        connection.prepareStatement(sql).use { statement ->
            bind(statement, args)
            statement.executeQuery().use { results ->
                val meta = results.metaData
                val rows = mutableListOf<HistorySqlRow>()
                while (results.next()) {
                    val values = linkedMapOf<String, Any?>()
                    for (index in 1..meta.columnCount) {
                        values[meta.getColumnLabel(index).lowercase()] = results.getObject(index)
                    }
                    rows += HistorySqlRow(values)
                }
                return rows
            }
        }
    }

    override fun <T> transaction(block: () -> T): T {
        val previous = connection.autoCommit
        connection.autoCommit = false
        try {
            val value = block()
            connection.commit()
            return value
        } catch (error: Throwable) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = previous
        }
    }

    private fun bind(statement: java.sql.PreparedStatement, args: List<Any?>) {
        args.forEachIndexed { index, value ->
            val position = index + 1
            when (value) {
                null -> statement.setNull(position, Types.VARCHAR)
                is Long -> statement.setLong(position, value)
                is Int -> statement.setLong(position, value.toLong())
                else -> statement.setString(position, value.toString())
            }
        }
    }

    companion object {
        fun open(): JdbcHistorySql {
            Class.forName("org.sqlite.JDBC")
            val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
            connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
            val sql = JdbcHistorySql(connection)
            HistorySchema.apply(sql)
            return sql
        }
    }
}
