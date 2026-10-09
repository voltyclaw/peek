package app.pane.android.data.reddit

import app.pane.android.data.resolver.UrlResolver

fun interface RedditPageLoader : UrlResolver<ParsedRedditPost>
