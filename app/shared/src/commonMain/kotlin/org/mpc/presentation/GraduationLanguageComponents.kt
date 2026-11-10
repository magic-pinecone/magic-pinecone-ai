package org.mpc.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import org.mpc.domain.model.GraduationLanguageSource
import org.mpc.presentation.theme.MpcShapes
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme

@Composable
internal fun GraduationLanguageCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MpcShapes.card)
            .background(MpcTheme.colors.surface)
            .padding(MpcSpacing.card),
        verticalArrangement = Arrangement.spacedBy(MpcSpacing.space2),
    ) {
        content()
    }
}

@Composable
internal fun Subheading(title: String) {
    Text(
        text = title,
        style = MpcTheme.typography.label,
        color = MpcTheme.colors.labelSecondary,
        modifier = Modifier.padding(top = MpcSpacing.space1).semantics { heading() },
    )
}

@Composable
internal fun Paragraph(
    text: String,
    secondary: Boolean = false,
) {
    Text(
        text = text,
        style = MpcTheme.typography.supporting,
        color = if (secondary) MpcTheme.colors.labelSecondary else MpcTheme.colors.label,
    )
}

@Composable
internal fun ScoreRow(
    name: String,
    value: String,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = MpcSpacing.space1),
            horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space3),
        ) {
            Text(name, style = MpcTheme.typography.supporting, color = MpcTheme.colors.label, modifier = Modifier.weight(1f))
            Text(
                text = value,
                style = MpcTheme.typography.headline,
                color = MpcTheme.colors.label,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = MpcTheme.colors.separator)
    }
}

@Composable
internal fun Bullets(
    title: String,
    lines: List<String>,
) {
    if (lines.isEmpty()) return
    Subheading(title)
    lines.forEach { Paragraph("• $it") }
}

/** Sets off what applies to international students from the rules for everyone. */
@Composable
internal fun Callout(
    title: String,
    lines: List<String>,
) {
    if (lines.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MpcShapes.badge)
            .background(MpcTheme.colors.accentContainer)
            .padding(MpcSpacing.space3),
        verticalArrangement = Arrangement.spacedBy(MpcSpacing.space1),
    ) {
        Text(title, style = MpcTheme.typography.label, color = MpcTheme.colors.onAccentContainer)
        lines.forEach {
            Text("• $it", style = MpcTheme.typography.supporting, color = MpcTheme.colors.onAccentContainer)
        }
    }
}

@Composable
internal fun SourceLink(source: GraduationLanguageSource) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MpcShapes.badge)
            .clickable { uriHandler.openUri(source.url) }
            .padding(vertical = MpcSpacing.space1),
        horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${source.org} · ${source.title}",
            style = MpcTheme.typography.caption,
            color = MpcTheme.colors.accent,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MpcTheme.colors.accent)
    }
}
