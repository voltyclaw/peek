package app.pane.android.data.sample

import android.content.Context
import java.io.File

/** Copies bundled sample media into app files so images, video, and downloads stay offline. */
object SampleMedia {
    data class Files(
        val ridge: String,
        val link: String,
        val kyoto: String,
        val material: String,
        val video: String,
    )

    fun install(context: Context): Files {
        val dir = File(context.filesDir, "sample-media").apply { mkdirs() }
        fun copy(asset: String, name: String): String {
            val out = File(dir, name)
            context.assets.open(asset).use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            return out.toURI().toString()
        }
        return Files(
            ridge = copy("samples/ridge.jpg", "ridge.jpg"),
            link = copy("samples/link.jpg", "link.jpg"),
            kyoto = copy("samples/kyoto.jpg", "kyoto.jpg"),
            material = copy("samples/material.jpg", "material.jpg"),
            video = copy("samples/reel.mp4", "reel.mp4"),
        )
    }
}
