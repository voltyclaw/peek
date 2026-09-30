package com.mustafashakir.peek.data.reddit

import com.mustafashakir.peek.data.resolver.UrlResolver

fun interface RedditPageLoader : UrlResolver<ParsedRedditPost>
