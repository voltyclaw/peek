package app.pane.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.pane.android.R
import coil3.compose.AsyncImage
import app.pane.android.ui.model.CommentUiModel
import app.pane.android.ui.model.UiImage
import app.pane.android.ui.model.ViewerArticleUiModel
import app.pane.android.ui.model.ViewerLinkCardUiModel
import app.pane.android.ui.model.ViewerPostUiModel
import app.pane.android.ui.model.ViewerQuoteUiModel
import app.pane.android.ui.text.MentionNetwork
import app.pane.android.ui.text.mentionNetwork
import app.pane.android.ui.actions.openInAppPackages
import androidx.compose.ui.platform.LocalContext
import app.pane.android.ui.theme.Geist
import app.pane.android.ui.theme.GeistMono
import app.pane.android.ui.theme.Inter
import app.pane.android.ui.theme.LocalPaneColors
import app.pane.android.ui.theme.PaneDisplay
import app.pane.android.ui.actions.RecoveryReason
import app.pane.android.ui.actions.packageInstalled
import app.pane.android.ui.actions.recoveryPresentation
import app.pane.android.ui.viewer.sourceDisplayNameFallback
import app.pane.android.ui.viewer.sourceDisplayNameRes
import app.pane.android.ui.viewer.threadMicroLabel
import app.pane.android.ui.viewer.ThreadLabel
import app.pane.android.ui.theme.PaneAccent
import app.pane.android.ui.theme.PaneBorder
import app.pane.android.ui.theme.PaneChip
import app.pane.android.ui.theme.PaneGround
import app.pane.android.ui.theme.PaneInk
import app.pane.android.ui.theme.PaneMuted
import app.pane.android.ui.theme.PaneSecondary
import app.pane.android.ui.theme.PaneTile
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AuthorByline(
    post: ViewerPostUiModel,
    onCopyLink: suspend () -> Unit,
    onCopyMedia: suspend () -> Unit,
    onDownload: suspend () -> Unit,
    onShare: suspend () -> Unit,
    onSharePost: suspend () -> Unit = {},
    modifier: Modifier = Modifier,
    canCopyMedia: Boolean = true,
    canDownload: Boolean = true,
    onLeave: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    var nameExpanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(PaneTile), contentAlignment = Alignment.Center) {
                val avatar = post.authorAvatar
                if (avatar != null && (avatar !is UiImage.Url || avatar.value.isNotBlank())) {
                    PeekImage(
                        image = avatar,
                        contentDescription = post.authorName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                } else {
                    Icon(Icons.Rounded.Person, contentDescription = null, tint = PaneSecondary, modifier = Modifier.size(17.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = post.authorName,
                    modifier = Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.show_full_name),
                        onClick = { nameExpanded = !nameExpanded },
                    ),
                    color = PaneInk,
                    maxLines = if (nameExpanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                )
                Text(post.authorMetadata, color = PaneMuted, style = TextStyle(fontFamily = GeistMono, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp))
            }
            EllipsisToggleButton(expanded = expanded, onToggle = { expanded = !expanded })
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(180)),
            exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(120)),
        ) {
            UtilityActionsRow(
                onCopyLink = onCopyLink,
                onSharePost = onSharePost,
                onCopyMedia = onCopyMedia,
                onDownload = onDownload,
                onShare = onShare,
                canCopyMedia = canCopyMedia,
                canDownload = canDownload,
                onLeave = onLeave,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun EllipsisToggleButton(expanded: Boolean, onToggle: () -> Unit) {
    val backgroundColor by animateColorAsState(if (expanded) PaneAccent else PaneGround, label = "ellipsisBackground")
    val iconTint by animateColorAsState(if (expanded) PaneGround else PaneAccent, label = "ellipsisTint")
    val borderAlpha by animateFloatAsState(if (expanded) 0f else 1f, label = "ellipsisBorderAlpha")
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(1.dp, PaneBorder.copy(alpha = borderAlpha), RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.media_options), onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.MoreHoriz, contentDescription = stringResource(R.string.media_options), tint = iconTint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun UtilityActionsRow(
    onCopyLink: suspend () -> Unit,
    onSharePost: suspend () -> Unit,
    onCopyMedia: suspend () -> Unit,
    onDownload: suspend () -> Unit,
    onShare: suspend () -> Unit,
    canCopyMedia: Boolean,
    canDownload: Boolean,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PaneChip)
            .border(1.dp, PaneBorder, RoundedCornerShape(12.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        UtilityActionButton(Icons.Rounded.Link, stringResource(R.string.copy_link), onCopyLink, Modifier.weight(1f))
        UtilityActionButton(Icons.Rounded.Share, stringResource(R.string.share_post), onSharePost, Modifier.weight(1f))
        if (canCopyMedia) {
            UtilityActionButton(Icons.Rounded.ContentCopy, stringResource(R.string.copy_media), onCopyMedia, Modifier.weight(1f))
        }
        if (canDownload) {
            UtilityActionButton(Icons.Rounded.Download, stringResource(R.string.download), onDownload, Modifier.weight(1f))
            UtilityActionButton(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.share), onShare, Modifier.weight(1f))
        }
        UtilityActionButton(Icons.Rounded.Close, stringResource(R.string.leave), { onLeave() }, Modifier.weight(1f))
    }
}

@Composable
private fun UtilityActionButton(
    icon: ImageVector,
    label: String,
    onClick: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    var activated by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val backgroundColor by animateColorAsState(if (activated) PaneAccent else PaneGround, label = "actionBackground")
    val contentColor by animateColorAsState(if (activated) PaneGround else PaneSecondary, label = "actionContent")
    val scale by animateFloatAsState(if (activated) 0.96f else 1f, label = "actionScale")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .clickable(enabled = !processing, role = Role.Button, onClick = {
                activated = true
                processing = true
                scope.launch {
                    try {
                        onClick()
                    } finally {
                        processing = false
                        delay(120)
                        activated = false
                    }
                }
            }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(17.dp))
        Text(
            text = label,
            color = contentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = GeistMono, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, lineHeight = 9.sp),
        )
        AnimatedVisibility(
            visible = processing,
            enter = fadeIn(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(120)),
        ) {
            LinearProgressIndicator(
                modifier = Modifier.padding(top = 5.dp).width(24.dp).height(2.dp).clip(RoundedCornerShape(999.dp)),
                color = contentColor,
                trackColor = contentColor.copy(alpha = 0.22f),
                strokeCap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun CaptionText(
    post: ViewerPostUiModel,
    large: Boolean = false,
    onOpen: (String) -> Unit = {},
) {
    val caption = post.title
    if (caption.isBlank() || caption == post.article?.title) return
    Text(
        text = autolinkedCaption(caption, PaneInk, mentionNetwork(post.sourceUrl), onOpen),
        modifier = Modifier.fillMaxWidth(),
        color = PaneInk,
        style = TextStyle(
            fontFamily = Inter,
            fontSize = if (large) 20.5.sp else 16.sp,
            lineHeight = if (large) 30.sp else 22.sp,
            fontWeight = FontWeight.Normal,
        ),
    )
}

/**
 * The author's own chain, root first. The opened status is highlighted.
 * The reader scrolls the sequence; there is no separate jump control.
 */
@Composable
fun AuthorThreadSection(
    post: ViewerPostUiModel,
    inlineImage: UiImage? = null,
    onOpen: (String) -> Unit = {},
) {
    val posts = post.authorThread
    if (posts.size < 2) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.thread_posts, posts.size),
            color = PaneMuted,
            style = TextStyle(fontFamily = Inter, fontSize = 13.sp),
        )
        Spacer(Modifier.height(14.dp))
        posts.forEachIndexed { index, item ->
            Row(Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(18.dp)) {
                    Box(Modifier.padding(top = 6.dp).size(9.dp).clip(CircleShape).background(PaneMuted))
                    if (index < posts.lastIndex || post.authorThreadPartial) {
                        Box(Modifier.padding(top = 4.dp).width(2.dp).height(28.dp).background(PaneBorder))
                    }
                }
                Column(Modifier.weight(1f).padding(start = 10.dp, bottom = 16.dp)) {
                    Text(
                        text = stringResource(R.string.thread_index, index + 1, posts.size),
                        color = PaneMuted,
                        style = TextStyle(fontFamily = Inter, fontSize = 12.sp),
                    )
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = autolinkedCaption(item.text, PaneInk, mentionNetwork(post.sourceUrl), onOpen),
                            modifier = Modifier.weight(1f).padding(top = 4.dp),
                            color = PaneInk,
                            style = TextStyle(fontFamily = Inter, fontSize = 14.5.sp, lineHeight = 21.sp),
                        )
                        if (item.opened && inlineImage != null) {
                            PeekImage(
                                image = inlineImage,
                                contentDescription = post.mediaDescription,
                                modifier = Modifier.padding(start = 10.dp).size(width = 68.dp, height = 56.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            )
                        }
                    }
                }
            }
        }
        if (post.authorThreadPartial) {
            Text(
                text = stringResource(R.string.author_thread_partial),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PaneTile).padding(14.dp),
                color = PaneMuted,
                style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp),
            )
        }
    }
}

@Composable
fun CommentsSection(
    post: ViewerPostUiModel,
    isLoadingMore: Boolean,
    scrollOffset: Int,
    onLoadMore: () -> Unit,
    host: String = "",
    onOpenSource: () -> Unit = {},
    onOpen: (String) -> Unit = {},
) {
    val sourceHost = host.ifBlank { post.sourceUrl }
    val x = isXHost(sourceHost)
    // T5: no replies and no guest wall — omit the section, including the THREAD label.
    val showThreadBody = if (x) {
        post.comments.isNotEmpty() || post.canLoadMoreComments || post.commentsTruncated
    } else {
        post.comments.isNotEmpty() ||
            post.commentCount > 0 ||
            post.canLoadMoreComments ||
            post.commentsTruncated
    }
    if (!showThreadBody) return
    val loaded = countComments(post.comments)
    val total = maxOf(post.commentCount, loaded)
    val label = when (threadMicroLabel(sourceHost)) {
        ThreadLabel.TopComments -> R.string.top_comments
        ThreadLabel.Comments -> R.string.comments_label
        ThreadLabel.Thread -> R.string.thread_label
    }
    val remainder = post.commentsTruncated || (post.commentCount > loaded && loaded > 0)
    val showEndCap = !x && !post.canLoadMoreComments && remainder
    val showReplyList = post.comments.isNotEmpty() || (!x && post.commentCount > 0) || post.canLoadMoreComments
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(label),
                color = PaneMuted,
                style = TextStyle(fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.14.em),
            )
            if (total > 0) {
                Text(
                    text = stringResource(R.string.reply_count_of, loaded, total),
                    color = PaneMuted,
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp),
                )
            }
        }
        if (showReplyList) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PaneTile),
            ) {
                if (!x && post.comments.isEmpty() && post.commentCount > 0) {
                    Text(
                        text = stringResource(R.string.replies_unavailable),
                        modifier = Modifier.padding(14.dp),
                        color = PaneMuted,
                        style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp),
                    )
                }
                post.comments.forEachIndexed { index, comment ->
                    if (index > 0) Box(Modifier.padding(start = 52.dp).fillMaxWidth().height(1.dp).background(PaneBorder))
                    CommentThread(comment, accentLine = false, profile = x, onOpen = onOpen)
                }
                if (post.canLoadMoreComments) {
                    if (x) {
                        ReplyFetchSpinner(
                            isLoading = isLoadingMore,
                            scrollOffset = scrollOffset,
                            onLoadMore = onLoadMore,
                        )
                    } else {
                        CommentPaginationSentinel(
                            isLoading = isLoadingMore,
                            scrollOffset = scrollOffset,
                            onLoadMore = onLoadMore,
                        )
                    }
                }
            }
        }
        if (x && post.commentsTruncated && !post.canLoadMoreComments) {
            MoreRepliesOnX(
                url = post.sourceUrl,
                onOpen = onOpenSource,
                modifier = Modifier.padding(top = if (showReplyList) 14.dp else 10.dp),
            )
        }
        if (showEndCap) {
            val sourceName = sourceDisplayNameRes(sourceHost)?.let { stringResource(it) }
                ?: sourceDisplayNameFallback(sourceHost)
            Text(
                text = stringResource(R.string.rest_of_thread, sourceName),
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                color = PaneMuted.copy(alpha = 0.75f),
                style = TextStyle(fontFamily = Inter, fontSize = 13.sp),
            )
        }
    }
}

private fun isXHost(host: String): Boolean {
    val value = host.lowercase()
    return value == "x.com" || value.endsWith(".x.com") || value.contains("twitter.com") || value.contains("x.com/")
}

private fun countComments(comments: List<CommentUiModel>): Int =
    comments.sumOf { 1 + countComments(it.replies) }

@Composable
private fun ReplyFetchSpinner(
    isLoading: Boolean,
    scrollOffset: Int,
    onLoadMore: () -> Unit,
) {
    val rootView = LocalView.current
    val loading = stringResource(R.string.loading_more_replies)
    var lastRequestedScrollOffset by remember { mutableIntStateOf(-1) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .semantics { contentDescription = loading }
            .onGloballyPositioned { coordinates ->
                val visible = coordinates.boundsInWindow().top < rootView.height
                if (scrollOffset > lastRequestedScrollOffset && visible && !isLoading) {
                    lastRequestedScrollOffset = scrollOffset
                    onLoadMore()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = PaneMuted,
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun MoreRepliesOnX(url: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val presentation = remember(url) {
        recoveryPresentation(url, RecoveryReason.Other) { packageName ->
            packageInstalled(context, packageName)
        }
    }
    val appName = presentation.appNameRes?.let { stringResource(it) }.orEmpty()
    val openLabel = if (presentation.opensInApp && presentation.appNameRes != null) {
        stringResource(R.string.open_in_named_app, appName)
    } else {
        stringResource(R.string.open_in_browser)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PaneTile)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.more_replies_on_x),
            color = PaneInk,
            style = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        )
        Text(
            text = openLabel,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onOpen)
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = openLabel
                    this.onClick { onOpen(); true }
                },
            color = PaneInk,
            style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun CommentPaginationSentinel(
    isLoading: Boolean,
    scrollOffset: Int,
    onLoadMore: () -> Unit,
) {
    val rootView = LocalView.current
    var lastRequestedScrollOffset by remember { mutableIntStateOf(-1) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clickable(enabled = !isLoading, role = Role.Button, onClick = onLoadMore)
            .onGloballyPositioned { coordinates ->
                val visible = coordinates.boundsInWindow().top < rootView.height
                if (scrollOffset > lastRequestedScrollOffset && visible && !isLoading) {
                    lastRequestedScrollOffset = scrollOffset
                    onLoadMore()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(if (isLoading) R.string.loading_more_comments else R.string.load_more_comments),
            color = PaneMuted,
            style = TextStyle(fontFamily = Inter, fontSize = 12.sp),
        )
    }
}

@Composable
fun CommentThread(
    comment: CommentUiModel,
    accentLine: Boolean,
    depth: Int = 0,
    profile: Boolean = false,
    onOpen: (String) -> Unit = {},
) {
    var collapsed by rememberSaveable(comment.id) { mutableStateOf(false) }
    val canFold = comment.replies.isNotEmpty()
    val expanded = commentBranchExpanded(
        collapsedIds = if (collapsed) setOf(comment.id) else emptySet(),
        commentId = comment.id,
    )
    val lineColor = if (accentLine) PaneMuted else PaneBorder
    val step = commentNestingStepDp(depth)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (profile) 0.dp else step.dp)
            .then(
                if (profile) {
                    Modifier
                } else {
                    Modifier.drawBehind {
                        drawLine(lineColor, start = Offset(0f, 0f), end = Offset(0f, size.height), strokeWidth = 2.dp.toPx())
                    }.padding(start = 10.dp, top = 2.dp, bottom = 2.dp)
                },
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CommentRow(
            comment = comment,
            folded = canFold && !expanded,
            onToggleFold = if (canFold) {
                { collapsed = !collapsed }
            } else {
                null
            },
            profile = profile,
            onOpen = onOpen,
        )
        if (expanded) {
            comment.replies.forEach { reply ->
                CommentThread(reply, accentLine = false, depth = depth + 1, profile = profile, onOpen = onOpen)
            }
        }
    }
}

@Composable
fun CommentRow(
    comment: CommentUiModel,
    folded: Boolean = false,
    onToggleFold: (() -> Unit)? = null,
    profile: Boolean = false,
    onOpen: (String) -> Unit = {},
) {
    val toggleLabel = stringResource(if (folded) R.string.expand_replies else R.string.collapse_replies)
    if (profile) {
        XReplyRow(comment, folded, toggleLabel, onToggleFold, onOpen)
        return
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f).profileTarget(comment.profileUrl, comment.handle, onOpen),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(PaneBorder), contentAlignment = Alignment.Center) {
                    Text(comment.initial, color = PaneMuted, style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                }
                Text(comment.author, color = PaneInk, style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            }
            Text(comment.age, color = PaneMuted, style = TextStyle(fontFamily = Inter, fontSize = 12.sp))
            if (folded && onToggleFold != null) {
                Text(
                    text = "+",
                    modifier = Modifier.clickable(role = Role.Button, onClickLabel = toggleLabel, onClick = onToggleFold).padding(start = 6.dp),
                    color = PaneMuted,
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
        Text(
            text = redditCommentAnnotated(
                source = comment.body,
                linkColor = PaneInk,
                quoteColor = PaneMuted,
                codeFont = Inter,
                network = MentionNetwork.Reddit,
                onOpen = onOpen,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onToggleFold != null) Modifier.clickable(role = Role.Button, onClickLabel = toggleLabel, onClick = onToggleFold) else Modifier),
            color = PaneInk,
            style = TextStyle(fontFamily = Inter, fontSize = 13.5.sp, lineHeight = 19.sp),
        )
        ReplyCard(comment, onOpen)
    }
}

@Composable
private fun XReplyRow(
    comment: CommentUiModel,
    folded: Boolean,
    toggleLabel: String,
    onToggleFold: (() -> Unit)?,
    onOpen: (String) -> Unit,
) {
    val handle = comment.handle?.removePrefix("@")?.takeIf { it.isNotEmpty() }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().profileTarget(comment.profileUrl, handle, onOpen),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileAvatar(
                    image = comment.avatarUrl?.let { UiImage.Url(it) },
                    label = comment.author,
                    size = 32.dp,
                )
                Column(Modifier.weight(1f)) {
                    Text(comment.author, color = PaneInk, style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                    if (handle != null) {
                        Text("@$handle", color = PaneMuted, style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                    }
                }
                if (comment.age.isNotBlank()) {
                    Text(comment.age, color = PaneMuted, style = TextStyle(fontFamily = Inter, fontSize = 12.sp))
                }
                if (folded && onToggleFold != null) {
                    Text(
                        text = "+",
                        modifier = Modifier.clickable(role = Role.Button, onClickLabel = toggleLabel, onClick = onToggleFold),
                        color = PaneMuted,
                        style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    )
                }
            }
            Text(
                text = redditCommentAnnotated(
                    source = comment.body,
                    linkColor = PaneInk,
                    quoteColor = PaneMuted,
                    codeFont = Inter,
                    network = MentionNetwork.X,
                    onOpen = onOpen,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .then(if (onToggleFold != null) Modifier.clickable(role = Role.Button, onClickLabel = toggleLabel, onClick = onToggleFold) else Modifier),
                color = PaneInk,
                style = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp),
            )
            ReplyCard(comment, onOpen)
        }
    }
}

@Composable
private fun Modifier.profileTarget(url: String?, handle: String?, onOpen: (String) -> Unit): Modifier {
    if (url.isNullOrBlank()) return this
    val label = profileOpenLabel(url, handle)
    return this
        .heightIn(min = 48.dp)
        .clickable(role = Role.Button, onClick = { onOpen(url) })
        .clearAndSetSemantics {
            role = Role.Button
            contentDescription = label
            onClick { onOpen(url); true }
        }
}

@Composable
internal fun profileOpenLabel(url: String, handle: String?): String {
    val context = LocalContext.current
    val opens = remember(url) { openInAppPackages(url).any { packageInstalled(context, it) } }
    val token = handle?.removePrefix("@").orEmpty()
    return when (mentionNetwork(url)) {
        MentionNetwork.X -> if (token.isEmpty()) {
            stringResource(R.string.open_profile_on_source, stringResource(R.string.source_x))
        } else if (opens) {
            stringResource(R.string.open_mention_x, token)
        } else {
            stringResource(R.string.open_mention_browser, token)
        }
        MentionNetwork.Instagram -> if (token.isEmpty()) {
            stringResource(R.string.open_profile_on_source, stringResource(R.string.source_instagram))
        } else if (opens) {
            stringResource(R.string.open_mention_instagram, token)
        } else {
            stringResource(R.string.open_mention_browser, token)
        }
        MentionNetwork.Threads -> if (opens) {
            stringResource(R.string.open_mention_threads, token)
        } else {
            stringResource(R.string.open_mention_browser, token)
        }
        MentionNetwork.Reddit -> if (opens) {
            stringResource(R.string.open_mention_reddit_user, token.ifBlank { url.substringAfterLast('/') })
        } else {
            stringResource(R.string.open_mention_reddit_user_browser, token.ifBlank { url.substringAfterLast('/') })
        }
        MentionNetwork.Other -> stringResource(R.string.open_text_link)
    }
}

@Composable
private fun ReplyCard(comment: CommentUiModel, onOpen: (String) -> Unit) {
    val title = comment.cardTitle?.takeIf { it.isNotBlank() } ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PaneGround)
            .border(1.dp, PaneBorder, RoundedCornerShape(12.dp))
            .then(if (comment.cardUrl != null) Modifier.clickable(role = Role.Button) { onOpen(comment.cardUrl) } else Modifier)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = PaneInk, style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Medium))
        if (!comment.cardBody.isNullOrBlank()) {
            Text(comment.cardBody, color = PaneMuted, style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp))
        }
    }
}

@Composable
fun ArticleCard(article: ViewerArticleUiModel, onOpen: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PaneTile)
            .border(1.dp, PaneBorder, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = { onOpen(article.url) })
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val cover = article.coverUrl?.takeIf { it.startsWith("http") }
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = article.title,
                modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Text(article.title, color = PaneInk, style = TextStyle(fontFamily = Inter, fontSize = 18.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp))
        val body = article.body.ifBlank { article.preview }
        if (body.isNotBlank() && body != article.title) {
            Text(body, color = PaneInk, style = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 22.sp))
        }
    }
}

@Composable
fun QuoteCard(quote: ViewerQuoteUiModel, onOpen: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, PaneBorder, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = { onOpen(quote.url) })
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val name = quote.authorName.ifBlank { quote.handle }
        Text(name, color = PaneInk, style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
        if (quote.handle.isNotBlank()) {
            Text("@${quote.handle}", color = PaneMuted, style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        }
        if (quote.text.isNotBlank()) {
            Text(quote.text, color = PaneInk, style = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp))
        }
    }
}

@Composable
fun ExternalLinkCard(card: ViewerLinkCardUiModel, onOpen: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PaneTile)
            .clickable(role = Role.Button, onClick = { onOpen(card.url) })
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(card.label, color = PaneMuted, style = TextStyle(fontFamily = Inter, fontSize = 12.sp))
        if (card.title.isNotBlank()) {
            Text(card.title, color = PaneInk, style = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.Medium))
        }
    }
}

/** Real profile photo. A letter shows only when the URL is missing or the image fails. */
@Composable
fun ProfileAvatar(
    image: UiImage?,
    label: String,
    size: Dp,
) {
    val url = (image as? UiImage.Url)?.value?.takeIf { it.startsWith("http") }
    var failed by remember(url) { mutableStateOf(false) }
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(PaneTile).border(1.dp, PaneBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null && !failed) {
            AsyncImage(
                model = url,
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { failed = true },
            )
        } else {
            val initial = label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString().orEmpty()
            Text(
                text = initial,
                color = PaneMuted,
                style = TextStyle(
                    fontFamily = Inter,
                    fontSize = if (size < 36.dp) 12.sp else 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}
