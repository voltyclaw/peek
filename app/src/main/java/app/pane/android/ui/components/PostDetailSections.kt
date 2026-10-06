package app.pane.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pane.android.R
import app.pane.android.ui.model.CommentUiModel
import app.pane.android.ui.model.UiImage
import app.pane.android.ui.model.ViewerPostUiModel
import app.pane.android.ui.theme.Geist
import app.pane.android.ui.theme.GeistMono
import app.pane.android.ui.theme.Inter
import app.pane.android.ui.theme.PaneAccent
import app.pane.android.ui.theme.PaneBorder
import app.pane.android.ui.theme.PaneGround
import app.pane.android.ui.theme.PaneInk
import app.pane.android.ui.theme.PaneMuted
import app.pane.android.ui.theme.PaneSecondary
import app.pane.android.ui.theme.PaneTile
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch

@Composable
fun AuthorByline(
    post: ViewerPostUiModel,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(PaneTile), contentAlignment = Alignment.Center) {
            val avatar = post.authorAvatar
            if (avatar != null && (avatar !is UiImage.Url || avatar.value.isNotBlank())) {
                PeekImage(
                    image = avatar,
                    contentDescription = post.authorName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = PaneMuted, modifier = Modifier.size(18.dp))
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = post.authorName,
                color = PaneInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
            if (post.authorMetadata.isNotBlank()) {
                Text(
                    text = post.authorMetadata,
                    color = PaneMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp),
                )
            }
        }
    }
}

@Composable
fun PostActionsRow(
    onCopyLink: suspend () -> Unit,
    onSharePost: suspend () -> Unit,
    onOpenInApp: suspend () -> Unit,
    onDownload: suspend () -> Unit,
    canDownload: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        LabeledAction(Icons.Rounded.Share, stringResource(R.string.share), onSharePost)
        LabeledAction(Icons.Rounded.Link, stringResource(R.string.copy_link), onCopyLink)
        LabeledAction(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.open_in_app), onOpenInApp)
        if (canDownload) {
            LabeledAction(Icons.Rounded.Download, stringResource(R.string.download), onDownload)
        }
    }
}

@Composable
private fun LabeledAction(
    icon: ImageVector,
    label: String,
    onClick: suspend () -> Unit,
) {
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .height(48.dp)
            .clickable(enabled = !running, role = Role.Button, onClick = {
                if (running) return@clickable
                running = true
                scope.launch {
                    try {
                        onClick()
                    } finally {
                        running = false
                    }
                }
            }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = PaneInk, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            color = PaneInk,
            maxLines = 1,
            style = TextStyle(fontFamily = Inter, fontSize = 12.sp),
        )
    }
}

@Composable
fun CaptionText(post: ViewerPostUiModel) {
    var expanded by rememberSaveable(post.sourceUrl) { mutableStateOf(false) }
    var overflow by remember(post.title) { mutableStateOf(false) }
    SelectionContainer(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = autolinkedCaption(post.title, PaneAccent),
                modifier = Modifier.fillMaxWidth(),
                color = PaneInk,
                maxLines = if (expanded) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { layout ->
                    if (!expanded) overflow = layout.hasVisualOverflow
                },
                style = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 22.sp),
            )
            if (overflow && !expanded) {
                Text(
                    text = stringResource(R.string.caption_more),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(PaneGround)
                        .clickable(role = Role.Button) { expanded = true }
                        .padding(start = 6.dp),
                    color = PaneAccent,
                    style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

/**
 * The author's own chain, root first. The opened status is highlighted.
 * The reader scrolls the sequence; there is no separate jump control.
 */
@Composable
fun AuthorThreadSection(post: ViewerPostUiModel) {
    val posts = post.authorThread
    if (posts.size < 2) return
    val openedIndex = posts.indexOfFirst { it.opened }.let { index -> if (index < 0) 0 else index }
    val labelColor = PaneMuted
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
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            posts.forEachIndexed { _, item ->
                val opened = item.opened
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
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
    val threadLabel = PaneMuted
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
fun CommentThread(comment: CommentUiModel, @Suppress("UNUSED_PARAMETER") accentLine: Boolean, depth: Int = 0) {
    var collapsed by rememberSaveable(comment.id) { mutableStateOf(false) }
    var showDeeper by rememberSaveable(comment.id) { mutableStateOf(false) }
    val canFold = depth == 0 && comment.replies.isNotEmpty()
    val expanded = commentBranchExpanded(
        collapsedIds = if (collapsed) setOf(comment.id) else emptySet(),
        commentId = comment.id,
    )
    val buryReplies = depth >= 1 && comment.replies.isNotEmpty() && !showDeeper
    val step = commentNestingStepDp(depth)
    val lineColor = PaneSecondary
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
        if (buryReplies) {
            Text(
                text = stringResource(R.string.view_more_replies, comment.replies.size),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { showDeeper = true }
                    .padding(vertical = 8.dp),
                color = PaneAccent,
                style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
        } else if (expanded) {
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(PaneBorder),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = comment.initial.take(1),
                    color = PaneInk,
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = comment.author,
                        color = PaneInk,
                        style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    )
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
                    style = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp),
                )
            }
        }
    }
}
