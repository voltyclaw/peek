package app.pane.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pane.android.R
import app.pane.android.ui.theme.Inter
import app.pane.android.ui.theme.PaneBorder
import app.pane.android.ui.theme.PaneGround
import app.pane.android.ui.theme.PaneInk
import app.pane.android.ui.theme.PaneMuted

@Composable
fun SourceSettingsGroup(
    rows: List<SourceRowUi>,
    onShown: (id: String, on: Boolean) -> Unit,
    onWithdraw: (id: String) -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    var detailId by remember { mutableStateOf<String?>(null) }
    var withdrawId by remember { mutableStateOf<String?>(null) }
    val detail = rows.firstOrNull { it.id == detailId }?.detail
    val withdraw = rows.firstOrNull { it.id == withdrawId }
    Column(Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .then(
                            if (row.detail != null) {
                                Modifier.clickable(role = Role.Button) { detailId = row.id }
                            } else {
                                Modifier
                            },
                        )
                        .padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (row.mark != null) {
                        Icon(
                            painter = painterResource(row.mark),
                            contentDescription = null,
                            tint = PaneInk,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = row.title,
                            color = PaneInk,
                            style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                        )
                        val subtitle = row.subtitle
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                color = PaneMuted,
                                style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp),
                            )
                        }
                    }
                }
                val showLabel = stringResource(R.string.source_show_in_pane)
                Switch(
                    checked = row.shown,
                    onCheckedChange = { onShown(row.id, it) },
                    modifier = Modifier.semantics {
                        contentDescription = row.switchLabel
                        this.stateDescription = showLabel
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = PaneInk,
                        uncheckedTrackColor = PaneBorder,
                        checkedThumbColor = PaneGround,
                        uncheckedThumbColor = PaneInk,
                        checkedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        uncheckedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                )
            }
        }
    }
    if (detail != null && detailId != null) {
        SourceDetailPage(
            title = rows.first { it.id == detailId }.title,
            detail = detail,
            onBack = { detailId = null },
            onWithdraw = { withdrawId = detailId },
            onOpenExternal = onOpenExternal,
        )
    }
    if (withdraw?.detail != null && withdraw.detail.canWithdraw) {
        AlertDialog(
            onDismissRequest = { withdrawId = null },
            title = { Text(withdraw.detail.withdrawTitle) },
            text = { Text(withdraw.detail.withdrawBody) },
            confirmButton = {
                TextButton(onClick = {
                    val id = withdraw.id
                    withdrawId = null
                    detailId = null
                    onWithdraw(id)
                }) { Text(stringResource(R.string.consent_withdraw)) }
            },
            dismissButton = {
                TextButton(onClick = { withdrawId = null }) { Text(stringResource(R.string.consent_withdraw_keep)) }
            },
        )
    }
}

@Composable
private fun SourceDetailPage(
    title: String,
    detail: SourceDetailUi,
    onBack: () -> Unit,
    onWithdraw: () -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(PaneGround)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = PaneInk,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(8.dp),
            )
            Text(
                text = title,
                color = PaneInk,
                style = TextStyle(fontFamily = Inter, fontSize = 20.sp, fontWeight = FontWeight.Medium),
            )
        }
        Text(
            text = detail.body,
            modifier = Modifier.padding(top = 20.dp),
            color = PaneInk,
            style = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 22.sp),
        )
        val status = detail.status
        if (!status.isNullOrBlank()) {
            Text(
                text = status,
                modifier = Modifier.padding(top = 8.dp),
                color = PaneMuted,
                style = TextStyle(fontFamily = Inter, fontSize = 14.sp),
            )
        }
        Text(
            text = stringResource(R.string.source_detail_links_heading),
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
            color = PaneMuted,
            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
        detail.links.forEach { link ->
            Text(
                text = link.label,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { onOpenExternal(link.url) }
                    .padding(vertical = 12.dp),
                color = PaneInk,
                style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            )
        }
        if (detail.canWithdraw) {
            Text(
                text = stringResource(R.string.consent_withdraw),
                modifier = Modifier
                    .padding(top = 12.dp)
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onWithdraw)
                    .padding(vertical = 12.dp),
                color = PaneInk,
                style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}
