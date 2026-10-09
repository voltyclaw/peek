package app.pane.android.ui.media

/**
 * One playback owner for a post. Inline and fullscreen acquire the same player,
 * so position, volume, and play/pause survive the handoff. A new post replaces it.
 */
internal class PlaybackSession<T : ContinuablePlayer>(
    private val factory: (url: String) -> T,
) {
    var player: T? = null
        private set
    private var key: String? = null
    private var postUrl: String? = null
    private val positions = mutableMapOf<String, PlaybackCarry>()

    data class Acquired<T>(val player: T, val reused: Boolean)

    fun acquire(postUrl: String, mediaKey: String, url: String, freshMuted: Boolean): Acquired<T> {
        val id = "$postUrl|$mediaKey"
        val existing = player
        if (existing != null && key == id) {
            return Acquired(existing, reused = true)
        }
        if (existing != null && key != null) {
            positions[key!!] = existing.carry()
            existing.release()
        }
        val saved = positions[id]
        val created = factory(url)
        if (saved == null) {
            created.volume = if (freshMuted) 0f else 1f
            created.playWhenReady = false
            created.load(url, 0L)
            created.playWhenReady = true
        } else {
            created.load(url, saved.positionMs.coerceAtLeast(0L))
            created.volume = if (freshMuted) 0f else saved.volume
            created.playWhenReady = saved.playWhenReady
        }
        player = created
        key = id
        this.postUrl = postUrl
        return Acquired(created, reused = false)
    }

    /** Quality changes stay on the same player. Position, volume, and play/pause are put back after the load. */
    fun switchUrl(postUrl: String, mediaKey: String, url: String): Boolean {
        val current = player ?: return false
        if (key != "$postUrl|$mediaKey") return false
        if (current.mediaUrl == url) return false
        val carry = current.carry()
        applyCarry(current, url, carry)
        return true
    }

    fun retainOnly(activePostUrl: String?) {
        if (activePostUrl == null || activePostUrl != postUrl) release()
    }

    /** Off-screen: drop the player and keep each item's position for this post. */
    fun park() {
        val id = key ?: return
        player?.let { positions[id] = it.carry() }
        player?.release()
        player = null
        key = null
    }

    fun release() {
        player?.release()
        player = null
        key = null
        postUrl = null
        positions.clear()
    }
}

internal interface ContinuablePlayer {
    var mediaUrl: String?
    var positionMs: Long
    var playWhenReady: Boolean
    var volume: Float
    fun load(url: String, positionMs: Long)
    fun release()
}

internal data class PlaybackCarry(
    val positionMs: Long,
    val playWhenReady: Boolean,
    val volume: Float,
) {
    val muted: Boolean get() = volume <= MUTED_VOLUME
}

internal fun ContinuablePlayer.carry(): PlaybackCarry = PlaybackCarry(
    positionMs = positionMs.coerceAtLeast(0L),
    playWhenReady = playWhenReady,
    volume = volume,
)

/** Load (which seeks) happens before playWhenReady, so a recreated player does not start at 0:00. */
internal fun applyCarry(target: ContinuablePlayer, url: String, carry: PlaybackCarry) {
    target.load(url, carry.positionMs.coerceAtLeast(0L))
    target.volume = carry.volume
    target.playWhenReady = carry.playWhenReady
}

private const val MUTED_VOLUME = 0.001f
