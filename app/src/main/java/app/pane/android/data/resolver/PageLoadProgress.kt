package app.pane.android.data.resolver

import app.pane.android.domain.repository.LoadProgressListener
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Threads a [LoadProgressListener] through a coroutine's context so a [UrlResolver]
 * can report progress without changing its SAM signature.
 */
class PageLoadProgressElement(val listener: LoadProgressListener) :
    AbstractCoroutineContextElement(PageLoadProgressElement) {
    companion object Key : CoroutineContext.Key<PageLoadProgressElement>
}
