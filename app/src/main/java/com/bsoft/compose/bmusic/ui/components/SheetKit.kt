package com.bsoft.compose.bmusic.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bsoft.compose.bmusic.R
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/*
 * Building blocks shared by every bottom sheet: a header (artwork or icon tile, title,
 * subtitle), a row of large quick-action tiles, and a list of icon actions.
 */

/** Sheet body: consistent padding, and clear of the navigation bar. */
@Composable
fun SheetContent(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
        content = content
    )
}

/** The top of a sheet: [leading] (artwork or [SheetIconTile]), a title and an optional subtitle. */
@Composable
fun SheetHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            leading()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    modifier = Modifier.semantics { heading() },
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    }
}

/** A rounded tile with an icon, for sheet headers without artwork. */
@Composable
fun SheetIconTile(@DrawableRes icon: Int, size: Dp = 56.dp) {
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(modifier = Modifier.size(size * 0.5f), imageVector = ImageVector.vectorResource(icon), contentDescription = null)
        }
    }
}

/** A row of [SheetQuickAction] tiles. */
@Composable
fun SheetQuickActions(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

/** A large tile for the most common actions; [active] fills it (e.g. a favourite). */
@Composable
fun SheetQuickAction(
    @DrawableRes icon: Int,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.heightIn(min = 76.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (active) scheme.primaryContainer else scheme.surfaceContainerHigh,
        contentColor = if (active) scheme.onPrimaryContainer else scheme.onSurface,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(icon),
                tint = if (active) scheme.primary else scheme.onSurface,
                contentDescription = null
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** One action in a sheet list. [destructive] actions are red; [chevron] means it opens more. */
@Composable
fun SheetAction(
    @DrawableRes icon: Int,
    label: String,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    chevron: Boolean = false,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = when {
        destructive -> scheme.error
        highlighted -> scheme.primary
        else -> scheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Icon(imageVector = ImageVector.vectorResource(icon), tint = tint, contentDescription = null)
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                destructive -> scheme.error
                highlighted -> scheme.primary
                else -> scheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (chevron) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ms_chevron_right),
                tint = scheme.onSurfaceVariant,
                contentDescription = null
            )
        }
    }
}

/** A small caption above a group of sheet actions. */
@Composable
fun SheetSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}
