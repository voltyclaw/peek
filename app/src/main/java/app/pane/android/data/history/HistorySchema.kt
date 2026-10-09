package app.pane.android.data.history

/**
 * History schema v1.
 *
 * P2 search reads [history_fts] (FTS4 over title, author, handle, caption, note).
 * P3 notes use [history.note]. Tags use [tag] and [history_tag].
 * The prune index covers unstarred rows with an empty note; tagged rows are excluded in the query.
 *
 * Created in v1 so later phases do not rebuild the table. Nothing in the UI reads these yet.
 */
internal object HistorySchema {
    const val VERSION = 1

    val statements: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS history (
          url TEXT NOT NULL PRIMARY KEY,
          source TEXT NOT NULL,
          source_app TEXT NOT NULL,
          title TEXT NOT NULL,
          author_name TEXT NOT NULL,
          handle TEXT NOT NULL,
          caption TEXT NOT NULL,
          thumb_url TEXT,
          pfp_url TEXT,
          pinned_thumb_path TEXT,
          pinned_pfp_path TEXT,
          media_type TEXT NOT NULL,
          note TEXT,
          first_viewed_at INTEGER NOT NULL,
          last_viewed_at INTEGER NOT NULL,
          view_count INTEGER NOT NULL,
          starred_at INTEGER
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS index_history_last_viewed ON history(last_viewed_at)",
        "CREATE INDEX IF NOT EXISTS index_history_starred_at ON history(starred_at)",
        "CREATE INDEX IF NOT EXISTS index_history_source_app ON history(source_app)",
        """
        CREATE INDEX IF NOT EXISTS index_history_source_starred_viewed
        ON history(source_app, starred_at, last_viewed_at)
        """.trimIndent(),
        """
        CREATE INDEX IF NOT EXISTS index_history_prune ON history(last_viewed_at)
        WHERE starred_at IS NULL AND (note IS NULL OR note = '')
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS tag (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          name TEXT NOT NULL,
          name_norm TEXT NOT NULL UNIQUE
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS history_tag (
          url TEXT NOT NULL,
          tag_id INTEGER NOT NULL,
          PRIMARY KEY (url, tag_id),
          FOREIGN KEY (url) REFERENCES history(url) ON DELETE CASCADE,
          FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE CASCADE
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS index_history_tag_tag_id ON history_tag(tag_id)",
        """
        CREATE VIRTUAL TABLE IF NOT EXISTS history_fts USING fts4(
          title, author_name, handle, caption, note,
          content='history',
          tokenize=unicode61
        )
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS history_ai AFTER INSERT ON history BEGIN
          INSERT INTO history_fts(docid, title, author_name, handle, caption, note)
          VALUES (new.rowid, new.title, new.author_name, new.handle, new.caption, new.note);
        END
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS history_bd BEFORE DELETE ON history BEGIN
          DELETE FROM history_fts WHERE docid = old.rowid;
        END
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS history_bu BEFORE UPDATE ON history BEGIN
          DELETE FROM history_fts WHERE docid = old.rowid;
        END
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS history_au AFTER UPDATE ON history BEGIN
          INSERT INTO history_fts(docid, title, author_name, handle, caption, note)
          VALUES (new.rowid, new.title, new.author_name, new.handle, new.caption, new.note);
        END
        """.trimIndent(),
    )

    fun apply(sql: HistorySql) {
        statements.forEach { sql.exec(it) }
    }
}
