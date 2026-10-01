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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pane.android.R
import app.pane.android.ui.actions.openInAppLabelRes
import app.pane.android.ui.model.CommentUiModel
import app.pane.android.ui.model.UiImage
import app.pane.android.ui.model.ViewerPostUiModel
import app.pane.android.ui.theme.Geist
import app.pane.android.ui.theme.GeistMono
import app.pane.android.ui.theme.Inter
import app.pane.android.ui.theme.LocalPaneColors
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
    onOpenInApp: suspend () -> Unit = {},
    modifier: Modifier = Modifier,
    canCopyMedia: Boolean = true,
    canDownload: Boolean = true,
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
                postUrl = post.sourceUrl,
                onCopyLink = onCopyLink,
                onSharePost = onSharePost,
                onOpenInApp = onOpenInApp,
                onCopyMedia = onCopyMedia,
                onDownload = onDownload,
                onShare = onShare,
                canCopyMedia = canCopyMedia,
                canDownload = canDownload,
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
    postUrl: String,
    onCopyLink: suspend () -> Unit,
    onSharePost: suspend () -> Unit,
    onOpenInApp: suspend () -> Unit,
    onCopyMedia: suspend () -> Unit,
    onDownload: suspend () -> Unit,
    onShare: suspend () -> Unit,
    canCopyMedia: Boolean,
    canDownload: Boolean,
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
        UtilityActionButton(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(openInAppLabelRes(postUrl)), onOpenInApp, Modifier.weight(1f))
        if (canCopyMedia) {
            UtilityActionButton(Icons.Rounded.ContentCopy, stringResource(R.string.copy_media), onCopyMedia, Modifier.weight(1f))
        }
        if (canDownload) {
            UtilityActionButton(Icons.Rounded.Download, stringResource(R.string.download), onDownload, Modifier.weight(1f))
            UtilityActionButton(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.share), onShare, Modifier.weight(1f))
        }
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
fun CaptionText(post: ViewerPostUiModel) {
    Text(
        text = autolinkedCaption(post.title, PaneAccent),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        color = PaneInk,
        style = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 16.sp),
    )
}

/**
 * The author's own chain, root first. The opened status is highlighted.
 * Jump to start brings the first post into the framed scroll.
 */
@Composable
fun AuthorThreadSection(post: ViewerPostUiModel) {
    val posts = post.authorThread
    if (posts.size < 2) return
    val openedIndex = posts.indexOfFirst { it.opened }.let { index -> if (index < 0) 0 else index }
    val labelColor = if (LocalPaneColors.current.night) PaneAccent.copy(alpha = 0.7f) else PaneMuted
    val start = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.part_of_a_thread),
                color = labelColor,
                style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
            Text(
                text = stringResource(R.string.thread_position, openedIndex + 1, posts.size),
                color = PaneMuted,
                style = TextStyle(fontFamily = Inter, fontSize = 12.sp),
            )
        }
        Box(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) {
                    scope.launch { start.bringIntoView() }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = stringResource(R.string.jump_to_start),
                modifier = Modifier.padding(horizontal = 4.dp),
                color = PaneAccent,
                style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            posts.forEachIndexed { index, item ->
                val opened = item.opened
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (index == 0) Modifier.bringIntoViewRequester(start) else Modifier)
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (opened) {
                                Modifier.background(PaneTile).border(1.dp, PaneBorder, RoundedCornerShape(12.dp))
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = item.author,
                        color = PaneMuted,
                        style = TextStyle(fontFamily = Inter, fontSize = 12.sp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = autolinkedCaption(item.text, PaneAccent),
                        color = PaneInk,
                        style = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 20.sp),
                    )
                }
            }
        }
        if (post.authorThreadPartial) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.author_thread_partial),
                modifier = Modifier.padding(horizontal = 4.dp),
                color = PaneMuted,
                style = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 16.sp),
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
) {
    val threadLabel = if (LocalPaneColors.current.night) PaneAccent.copy(alpha = 0.7f) else PaneMuted
    val showThreadBody = post.comments.isNotEmpty() ||
        post.commentCount > 0 ||
        post.canLoadMoreComments ||
        post.commentsTruncated
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(PaneBorder))
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.thread),
            color = threadLabel,
            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
        if (showThreadBody) {
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(PaneTile)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (post.comments.isEmpty() && post.commentCount > 0) {
                    Text(
                        text = stringResource(R.string.replies_unavailable),
                        color = PaneMuted,
                        style = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 16.sp),
                    )
                }
                post.comments.forEachIndexed { index, comment ->
                    CommentThread(comment, accentLine = index == 0)
                }
                if (post.commentsTruncated) {
                    Text(
                        text = stringResource(R.string.replies_partial),
                        color = PaneMuted,
                        style = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 16.sp),
                    )
                }
                if (post.canLoadMoreComments) {
                    CommentPaginationSentinel(
                        isLoading = isLoadingMore,
                        scrollOffset = scrollOffset,
                        onLoadMore = onLoadMore,
                    )
                }
            }
        }
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
fun CommentThread(comment: CommentUiModel, accentLine: Boolean, depth: Int = 0) {
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
            .padding(start = step.dp)
            .drawBehind {
                drawLine(lineColor, start = Offset(0f, 0f), end = Offset(0f, size.height), strokeWidth = 2.dp.toPx())
            }
            .padding(start = 10.dp, top = 2.dp, bottom = 2.dp),
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
        )
        if (expanded) {
            comment.replies.forEach { reply ->
                CommentThread(reply, accentLine = false, depth = depth + 1)
            }
        }
    }
}

@Composable
fun CommentRow(
    comment: CommentUiModel,
    folded: Boolean = false,
    onToggleFold: (() -> Unit)? = null,
) {
    val toggleLabel = stringResource(if (folded) R.string.expand_replies else R.string.collapse_replies)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onToggleFold != null) {
                    Modifier.clickable(role = Role.Button, onClickLabel = toggleLabel, onClick = onToggleFold)
                } else {
                    Modifier
                },
            ),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(comment.author, color = PaneMuted, style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Medium))
            Text(comment.age, color = PaneMuted, style = TextStyle(fontFamily = Inter, fontSize = 12.sp))
            if (folded) {
                Text(
                    text = "+",
                    color = PaneMuted,
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
        Text(
            text = redditCommentAnnotated(
                source = comment.body,
                linkColor = PaneAccent,
                quoteColor = PaneSecondary,
                codeFont = GeistMono,
            ),
            modifier = Modifier.fillMaxWidth(),
            color = PaneInk,
            style = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 21.sp),
        )
    }
}
