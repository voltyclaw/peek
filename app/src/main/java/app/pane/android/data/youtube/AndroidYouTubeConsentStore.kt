package app.pane.android.data.youtube

import android.content.Context
import app.pane.android.domain.youtube.YouTubeConsent
import app.pane.android.domain.youtube.YouTubeConsentKeys
import app.pane.android.domain.youtube.YouTubeConsentStore

class AndroidYouTubeConsentStore(context: Context) : YouTubeConsentStore {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun read(): YouTubeConsent? {
        val version = preferences.getString(YouTubeConsentKeys.POLICY_VERSION, null) ?: return null
        val at = preferences.getLong(YouTubeConsentKeys.ACCEPTED_AT, 0L)
        if (version.isBlank() || at <= 0L) return null
        return YouTubeConsent(version, at)
    }

    override fun write(consent: YouTubeConsent) {
        preferences.edit()
            .putString(YouTubeConsentKeys.POLICY_VERSION, consent.policyVersion)
            .putLong(YouTubeConsentKeys.ACCEPTED_AT, consent.acceptedAtEpochMillis)
            .apply()
    }

    override fun clear() {
        preferences.edit()
            .remove(YouTubeConsentKeys.POLICY_VERSION)
            .remove(YouTubeConsentKeys.ACCEPTED_AT)
            .apply()
    }

    private companion object {
        const val FILE = "pane_youtube_consent"
    }
}
