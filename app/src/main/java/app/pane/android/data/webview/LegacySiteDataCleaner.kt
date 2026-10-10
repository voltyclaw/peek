package app.pane.android.data.webview

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebStorage
import android.webkit.WebView
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Live embed and scraper WebViews. IndexedDB files are deleted only while this count is zero,
 * after those WebViews have been destroyed.
 */
internal object EmbedWebViewLiveness {
    private val live = AtomicInteger(0)

    fun acquire() {
        live.incrementAndGet()
    }

    fun release() {
        live.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    fun liveCount(): Int = live.get()
}

internal enum class LegacyClearResult { FilesCleared, UnexpectedLayout, Deferred }

internal interface PendingOriginStore {
    fun read(): Set<String>
    fun write(origins: Set<String>)
}

internal class FilePendingOriginStore(private val file: File) : PendingOriginStore {
    override fun read(): Set<String> {
        if (!file.isFile) return emptySet()
        return file.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    override fun write(origins: Set<String>) {
        if (origins.isEmpty()) {
            file.delete()
            return
        }
        file.parentFile?.mkdirs()
        file.writeText(origins.joinToString(separator = "\n"))
    }
}

/**
 * Pre-119 withdraw for one source.
 *
 * Order: spike [WebStorage.deleteOrigin] for each origin, clear the app HTTP cache, then
 * delete that source's IndexedDB folders. File deletion waits until no WebView is alive.
 * An unexpected Chromium layout deletes nothing and stays on the partial withdraw path.
 * This class does not wipe every origin's storage or cookies.
 */
internal class LegacySiteDataCleaner(
    private val webViewRoot: File,
    private val pending: PendingOriginStore,
    private val liveWebViews: () -> Int,
    private val deleteOrigin: (String) -> Unit,
    private val clearHttpCache: () -> Unit,
) {
    fun clear(origins: List<String>): LegacyClearResult {
        val targets = origins.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        targets.forEach { origin -> runCatching { deleteOrigin(origin) } }
        runCatching { clearHttpCache() }
        return deleteIndexedDbIfIdle(targets)
    }

    /** Finishes a file delete that waited because a WebView was still alive. Does not build a WebView. */
    fun resumeDeferred(): LegacyClearResult? {
        val queued = pending.read()
        if (queued.isEmpty()) return null
        val result = deleteIndexedDbIfIdle(queued.toList())
        if (result != LegacyClearResult.Deferred) pending.write(emptySet())
        return result
    }

    private fun deleteIndexedDbIfIdle(origins: List<String>): LegacyClearResult {
        if (liveWebViews() > 0) {
            queue(origins)
            return LegacyClearResult.Deferred
        }
        val plan = when (val located = locateIndexedDb(webViewRoot)) {
            IndexedDbLocation.Unexpected -> return LegacyClearResult.UnexpectedLayout
            is IndexedDbLocation.Directory -> planIndexedDbDeletion(located.dir, origins)
        }
        return when (plan) {
            IndexedDbPlan.Unexpected -> LegacyClearResult.UnexpectedLayout
            is IndexedDbPlan.Ready -> {
                if (liveWebViews() > 0) {
                    queue(origins)
                    LegacyClearResult.Deferred
                } else {
                    plan.delete.forEach { folder -> runCatching { folder.deleteRecursively() } }
                    LegacyClearResult.FilesCleared
                }
            }
        }
    }

    private fun queue(origins: List<String>) {
        pending.write(pending.read() + origins.toSet())
    }
}

internal sealed class IndexedDbLocation {
    data class Directory(val dir: File) : IndexedDbLocation()
    data object Unexpected : IndexedDbLocation()
}

internal sealed class IndexedDbPlan {
    data class Ready(val delete: List<File>) : IndexedDbPlan()
    data object Unexpected : IndexedDbPlan()
}

/** `app_webview/Default/IndexedDB`. A stray `app_webview/IndexedDB` is an unexpected layout. */
internal fun locateIndexedDb(webViewRoot: File): IndexedDbLocation {
    val profile = File(webViewRoot, "Default")
    if (profile.exists() && !profile.isDirectory) return IndexedDbLocation.Unexpected
    if (File(webViewRoot, "IndexedDB").exists()) return IndexedDbLocation.Unexpected
    return IndexedDbLocation.Directory(File(profile, "IndexedDB"))
}

/**
 * Deletes only `https_<host>_0.indexeddb.leveldb` and `.blob` for [origins].
 * Any other IndexedDB entry for those hosts is an unexpected layout, and nothing is deleted.
 */
internal fun planIndexedDbDeletion(indexedDbDir: File, origins: List<String>): IndexedDbPlan {
    val hosts = origins.mapNotNull(::originHost).toSet()
    if (hosts.isEmpty()) return IndexedDbPlan.Ready(emptyList())
    if (!indexedDbDir.exists()) return IndexedDbPlan.Ready(emptyList())
    if (!indexedDbDir.isDirectory) return IndexedDbPlan.Unexpected
    val root = indexedDbDir.canonicalFile
    val children = indexedDbDir.listFiles() ?: return IndexedDbPlan.Unexpected
    val toDelete = mutableListOf<File>()
    for (child in children) {
        val owner = hosts.firstOrNull { host -> indexedDbEntryBelongsToHost(child.name, host) } ?: continue
        val childCanon = child.canonicalFile
        val inside = childCanon.path.startsWith(root.path + File.separator)
        val expected = child.name in expectedIndexedDbNames(owner)
        if (!inside || !expected || !child.isDirectory) return IndexedDbPlan.Unexpected
        toDelete += child
    }
    return IndexedDbPlan.Ready(toDelete)
}

internal fun originHost(origin: String): String? =
    runCatching { java.net.URI(origin).host }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()

internal fun expectedIndexedDbNames(host: String): Set<String> = setOf(
    "https_${host}_0.indexeddb.leveldb",
    "https_${host}_0.indexeddb.blob",
)

internal fun indexedDbEntryBelongsToHost(name: String, host: String): Boolean {
    val lower = name.lowercase()
    return lower.startsWith("https_${host.lowercase()}_") && ".indexeddb." in lower
}

/** Android entry for [LegacySiteDataCleaner]. Withdraw copy stays partial either way. */
internal object LegacySiteData {
    fun clear(context: Context, origins: List<String>): LegacyClearResult =
        cleaner(context).clear(origins)

    fun resume(context: Context) {
        cleaner(context).resumeDeferred()
    }

    private fun cleaner(context: Context): LegacySiteDataCleaner {
        val appContext = context.applicationContext
        return LegacySiteDataCleaner(
            webViewRoot = appContext.getDir(WEBVIEW_DIR, Context.MODE_PRIVATE),
            pending = FilePendingOriginStore(File(appContext.filesDir, PENDING_FILE)),
            liveWebViews = { EmbedWebViewLiveness.liveCount() },
            deleteOrigin = { origin -> WebStorage.getInstance().deleteOrigin(origin) },
            clearHttpCache = { clearAppHttpCache(appContext) },
        )
    }

    private fun clearAppHttpCache(context: Context) {
        val clear = Runnable {
            val webView = WebView(context)
            try {
                webView.clearCache(true)
            } finally {
                webView.destroy()
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            clear.run()
            return
        }
        val done = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            try {
                clear.run()
            } finally {
                done.countDown()
            }
        }
        done.await(CACHE_WAIT_SECONDS, TimeUnit.SECONDS)
    }

    private const val WEBVIEW_DIR = "webview"
    private const val PENDING_FILE = "pane-legacy-idb-pending.txt"
    private const val CACHE_WAIT_SECONDS = 3L
}
