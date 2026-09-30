package com.mustafashakir.peek.ui.home

import android.content.Context
import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Opens this app's system "Open by default" screen.
 *
 * Instagram and Reddit publish `/.well-known/assetlinks.json` for their own packages.
 * This debug build is not in those files. autoVerify stays false so Android does not
 * run that check and then clear the switches. On Android 12 and later a switch still
 * snaps off when another installed app is already verified for that host and still has
 * link handling turned on. Turning that off in the other app drops its approval, which
 * is when a later choice for Peek can stick. Paste does not depend on any of this.
 *
 * A normal app can read that state. It cannot write it; the system settings screen is
 * the only supported way to change the switches.
 */
object LinkSettings {
    val webHosts: List<String> = listOf(
        "www.instagram.com",
        "instagram.com",
        "www.reddit.com",
        "reddit.com",
        "old.reddit.com",
        "np.reddit.com",
        "new.reddit.com",
        "m.reddit.com",
        "redd.it",
        "www.redd.it",
        "www.facebook.com",
        "facebook.com",
        "m.facebook.com",
        "mbasic.facebook.com",
        "fb.com",
        "www.fb.com",
        "fb.watch",
        "x.com",
        "www.x.com",
        "mobile.x.com",
        "twitter.com",
        "www.twitter.com",
        "mobile.twitter.com",
    )

    enum class LinkHandlingReport {
        HandlingOff,
        NoneSelected,
        SomeSelected,
        AllSelected,
    }

    data class LinkHandlingStatus(
        val linkHandlingAllowed: Boolean,
        val enabledHosts: List<String>,
    )

    fun action(sdkInt: Int): String =
        if (sdkInt >= Build.VERSION_CODES.S) {
            Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS
        } else {
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        }

    fun isHostEnabled(state: Int?): Boolean =
        state == DomainVerificationUserState.DOMAIN_STATE_SELECTED ||
            state == DomainVerificationUserState.DOMAIN_STATE_VERIFIED

    fun report(linkHandlingAllowed: Boolean, enabledCount: Int, hostCount: Int): LinkHandlingReport =
        when {
            !linkHandlingAllowed -> LinkHandlingReport.HandlingOff
            enabledCount <= 0 -> LinkHandlingReport.NoneSelected
            enabledCount >= hostCount -> LinkHandlingReport.AllSelected
            else -> LinkHandlingReport.SomeSelected
        }

    fun open(context: Context) {
        val packageUri = Uri.parse("package:${context.packageName}")
        val primary = Intent(action(Build.VERSION.SDK_INT), packageUri)
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        runCatching { context.startActivity(primary) }
            .onFailure { context.startActivity(fallback) }
    }

    /** Null before Android 12, where supported-web-address switches do not exist. */
    fun read(context: Context): LinkHandlingStatus? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val manager = context.getSystemService(DomainVerificationManager::class.java) ?: return null
        val state = manager.getDomainVerificationUserState(context.packageName) ?: return null
        val enabled = webHosts.filter { host -> isHostEnabled(state.hostToStateMap[host]) }
        return LinkHandlingStatus(
            linkHandlingAllowed = state.isLinkHandlingAllowed,
            enabledHosts = enabled,
        )
    }
}
