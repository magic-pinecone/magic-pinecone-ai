package org.mpc.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import dev.icerock.moko.resources.compose.stringResource
import org.mpc.domain.model.AnnouncementScope
import org.mpc.domain.model.AnnouncementSummary
import org.mpc.domain.model.UserAffiliation
import org.mpc.presentation.theme.MpcShapes
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme
import org.mpc.shared.MR
import org.mpc.shared.announcements_attachment_title
import org.mpc.shared.announcements_open_attachment
import org.mpc.shared.announcements_pinned
import org.mpc.shared.announcements_summary_line

@Composable
internal fun AnnouncementCard(
    announcement: AnnouncementSummary,
    onOpenUrl: (title: String, url: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MpcShapes.card)
            .clickable { onOpenUrl(announcement.title, announcement.url) },
        color = MpcTheme.colors.surface,
        shape = MpcShapes.card,
    ) {
        Column(
            modifier = Modifier.padding(MpcSpacing.card),
            verticalArrangement = Arrangement.spacedBy(MpcSpacing.space2),
        ) {
            AnnouncementHeaderRow(announcement = announcement)

            Text(
                text = announcement.title,
                style = MpcTheme.typography.headline,
                color = MpcTheme.colors.label,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            AnnouncementMetaTags(announcement = announcement)

            if (announcement.summary.isNotEmpty()) {
                AnnouncementSummaryBlock(summary = announcement.summary)
            }

            val attachmentUrl = announcement.attachmentUrl
            if (!attachmentUrl.isNullOrBlank()) {
                val attachmentTitle = stringResource(MR.strings.announcements_attachment_title, announcement.title)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = { onOpenUrl(attachmentTitle, attachmentUrl) },
                    ) {
                        Text(
                            text = stringResource(MR.strings.announcements_open_attachment),
                            style = MpcTheme.typography.label,
                            color = MpcTheme.colors.accent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementHeaderRow(announcement: AnnouncementSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (announcement.isPinned) {
            AnnouncementBadge(
                text = stringResource(MR.strings.announcements_pinned),
                backgroundColor = MpcTheme.colors.accentContainer,
                textColor = MpcTheme.colors.onAccentContainer,
            )
        }
        AnnouncementBadge(
            text = announcement.category,
            backgroundColor = MpcTheme.colors.fillQuiet,
            textColor = MpcTheme.colors.onFillQuiet,
        )
        Text(
            text = announcement.departmentName.ifBlank { announcement.unitName },
            style = MpcTheme.typography.caption,
            color = MpcTheme.colors.labelSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(modifier = Modifier.weight(1f))
        val dateText = announcement.publishedDate
        if (!dateText.isNullOrBlank()) {
            Text(
                text = dateText,
                style = MpcTheme.typography.caption,
                color = MpcTheme.colors.labelTertiary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnnouncementMetaTags(announcement: AnnouncementSummary) {
    val specificAffiliations = announcement.affiliations.filter { it != UserAffiliation.ALL }
    val showCollege = announcement.scope == AnnouncementScope.DEPARTMENT && announcement.collegeName.isNotBlank()
    if (specificAffiliations.isEmpty() && !showCollege) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space1),
        verticalArrangement = Arrangement.spacedBy(MpcSpacing.space1),
    ) {
        if (showCollege) {
            AnnouncementBadge(
                text = announcement.collegeName,
                backgroundColor = MpcTheme.colors.fillQuiet,
                textColor = MpcTheme.colors.labelSecondary,
            )
        }
        specificAffiliations.forEach { affiliation ->
            AnnouncementBadge(
                text = affiliation.label,
                backgroundColor = MpcTheme.colors.accentContainer,
                textColor = MpcTheme.colors.onAccentContainer,
            )
        }
    }
}

@Composable
private fun AnnouncementSummaryBlock(summary: Map<String, String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MpcShapes.badge)
            .background(MpcTheme.colors.fillQuiet)
            .padding(horizontal = MpcSpacing.space3, vertical = MpcSpacing.space2),
        verticalArrangement = Arrangement.spacedBy(MpcSpacing.space1),
    ) {
        summary.entries.forEach { (key, value) ->
            if (value.isNotBlank()) {
                Text(
                    text = stringResource(MR.strings.announcements_summary_line, key, value),
                    style = MpcTheme.typography.supporting,
                    color = MpcTheme.colors.labelSecondary,
                )
            }
        }
    }
}

@Composable
private fun AnnouncementBadge(
    text: String,
    backgroundColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
) {
    Box(
        modifier = Modifier
            .clip(MpcShapes.badge)
            .background(backgroundColor)
            .padding(horizontal = MpcSpacing.space2, vertical = MpcSpacing.space1),
    ) {
        Text(
            text = text,
            style = MpcTheme.typography.micro,
            color = textColor,
        )
    }
}
