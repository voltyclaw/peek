package app.pane.android.data.history

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteStatement
import java.util.Locale

internal class AndroidHistorySql(context: Context) : HistorySql {
    private val helper = HistoryOpenHelper(context.applicationContext)

    init {
        helper.writableDatabase
    }

    override fun exec(sql: String, args: List<Any?>) {
        val statement = helper.writableDatabase.compileStatement(sql)
        try {
            bind(statement, args)
            val kind = sql.trimStart().lowercase(Locale.US)
            if (kind.startsWith("insert")) statement.executeInsert() else statement.executeUpdateDelete()
        } finally {
            statement.close()
        }
    }

    override fun query(sql: String, args: List<Any?>): List<HistorySqlRow> {
        val cursor = helper.writableDatabase.rawQuery(sql, args.map { it?.toString().orEmpty() }.toTypedArray())
        cursor.use { return read(it) }
    }

    override fun <T> transaction(block: () -> T): T {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val value = block()
            db.setTransactionSuccessful()
            return value
        } finally {
            db.endTransaction()
        }
    }

    private fun bind(statement: SQLiteStatement, args: List<Any?>) {
        args.forEachIndexed { index, value ->
            val position = index + 1
            when (value) {
                null -> statement.bindNull(position)
                is Long -> statement.bindLong(position, value)
                is Int -> statement.bindLong(position, value.toLong())
                else -> statement.bindString(position, value.toString())
            }
        }
    }

    private fun read(cursor: Cursor): List<HistorySqlRow> {
        val names = Array(cursor.columnCount) { index -> cursor.getColumnName(index).lowercase(Locale.US) }
        val rows = mutableListOf<HistorySqlRow>()
        while (cursor.moveToNext()) {
            val values = linkedMapOf<String, Any?>()
            for (index in names.indices) {
                values[names[index]] = if (cursor.isNull(index)) {
                    null
                } else {
                    when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                        else -> cursor.getString(index)
                    }
                }
            }
            rows += HistorySqlRow(values)
        }
        return rows
    }

    private class HistoryOpenHelper(context: Context) : SQLiteOpenHelper(
        context,
        DATABASE_NAME,
        null,
        HistorySchema.VERSION,
    ) {
        override fun onConfigure(db: SQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true)
        }

        override fun onCreate(db: SQLiteDatabase) {
            HistorySchema.statements.forEach { db.execSQL(it) }
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE history ADD COLUMN cache_fetched_at INTEGER")
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "history.db"
    }
}
