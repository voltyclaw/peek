package app.pane.android

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ManifestQueriesTest {
    @Test
    fun mergedManifestQueriesSourcePackagesAndBrowsableHttps() {
        val manifest = manifestText()
        listOf(
            "com.facebook.katana",
            "com.facebook.lite",
            "com.instagram.android",
            "com.instagram.barcelona",
            "com.twitter.android",
            "com.reddit.frontpage",
        ).forEach { packageName ->
            assertTrue(
                "missing package query $packageName",
                manifest.contains("android:name=\"$packageName\""),
            )
        }
        assertTrue(manifest.contains("android.intent.action.VIEW"))
        assertTrue(manifest.contains("android.intent.category.BROWSABLE"))
        assertTrue(manifest.contains("android:scheme=\"https\""))
    }

    private fun manifestText(): String {
        val root = File(System.getProperty("user.dir").orEmpty())
        val candidates = listOf(
            File(root, "build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml"),
            File(root, "build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"),
            File(root, "app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml"),
            File(root, "src/main/AndroidManifest.xml"),
            File(root, "app/src/main/AndroidManifest.xml"),
        )
        val chosen = candidates.firstOrNull { it.isFile } ?: error("AndroidManifest.xml not found from $root")
        return chosen.readText()
    }
}
