package org.mpc.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.compose.stringResource
import org.mpc.domain.model.AppSettings
import org.mpc.domain.model.AppThemeMode
import org.mpc.domain.model.PortalAuthenticationMode
import org.mpc.domain.model.TimetableColorMode
import org.mpc.presentation.state.BackendEndpointUiState
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme
import org.mpc.shared.MR
import org.mpc.shared.settings_appearance
import org.mpc.shared.settings_appearance_description
import org.mpc.shared.settings_course_source
import org.mpc.shared.settings_course_source_description
import org.mpc.shared.settings_portal_login
import org.mpc.shared.settings_portal_login_description
import org.mpc.shared.settings_portal_manual
import org.mpc.shared.settings_portal_manual_description
import org.mpc.shared.settings_portal_secure
import org.mpc.shared.settings_portal_secure_description
import org.mpc.shared.settings_theme_dark
import org.mpc.shared.settings_theme_dark_description
import org.mpc.shared.settings_theme_light
import org.mpc.shared.settings_theme_light_description
import org.mpc.shared.settings_theme_system
import org.mpc.shared.settings_theme_system_description
import org.mpc.shared.settings_timetable_category
import org.mpc.shared.settings_timetable_category_description
import org.mpc.shared.settings_timetable_colors
import org.mpc.shared.settings_timetable_colors_description
import org.mpc.shared.settings_timetable_shuffled
import org.mpc.shared.settings_timetable_shuffled_description
import org.mpc.shared.settings_write_failed

@Composable
fun SettingsScreen(
    settings: AppSettings,
    settingsWriteFailed: Boolean = false,
    modifier: Modifier = Modifier,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onPortalAuthenticationModeSelected: (PortalAuthenticationMode) -> Unit,
    onTimetableColorModeSelected: (TimetableColorMode) -> Unit,
    backendEndpoint: BackendEndpointUiState? = null,
    onUseCourseSource: (String) -> Unit = {},
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = MpcSpacing.space3),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (settingsWriteFailed) {
            Text(
                text = stringResource(MR.strings.settings_write_failed),
                color = MpcTheme.colors.destructive,
                style = MpcTheme.typography.supporting,
            )
        }

        SettingsSectionTitle(
            title = stringResource(MR.strings.settings_appearance),
            description = stringResource(MR.strings.settings_appearance_description),
        )
        SettingsChoiceRow(
            title = stringResource(MR.strings.settings_theme_system),
            description = stringResource(MR.strings.settings_theme_system_description),
            selected = settings.themeMode == AppThemeMode.SYSTEM,
            onClick = { onThemeModeSelected(AppThemeMode.SYSTEM) },
        )
        SettingsChoiceRow(
            title = stringResource(MR.strings.settings_theme_light),
            description = stringResource(MR.strings.settings_theme_light_description),
            selected = settings.themeMode == AppThemeMode.LIGHT,
            onClick = { onThemeModeSelected(AppThemeMode.LIGHT) },
        )
        SettingsChoiceRow(
            title = stringResource(MR.strings.settings_theme_dark),
            description = stringResource(MR.strings.settings_theme_dark_description),
            selected = settings.themeMode == AppThemeMode.DARK,
            onClick = { onThemeModeSelected(AppThemeMode.DARK) },
        )

        HorizontalDivider()

        SettingsSectionTitle(
            title = stringResource(MR.strings.settings_timetable_colors),
            description = stringResource(MR.strings.settings_timetable_colors_description),
        )
        SettingsChoiceRow(
            title = stringResource(MR.strings.settings_timetable_category),
            description = stringResource(MR.strings.settings_timetable_category_description),
            selected = settings.timetableColorMode == TimetableColorMode.CATEGORY,
            onClick = { onTimetableColorModeSelected(TimetableColorMode.CATEGORY) },
        )
        SettingsChoiceRow(
            title = stringResource(MR.strings.settings_timetable_shuffled),
            description = stringResource(MR.strings.settings_timetable_shuffled_description),
            selected = settings.timetableColorMode == TimetableColorMode.SHUFFLED,
            onClick = { onTimetableColorModeSelected(TimetableColorMode.SHUFFLED) },
        )

        HorizontalDivider()

        SettingsSectionTitle(
            title = stringResource(MR.strings.settings_portal_login),
            description = stringResource(MR.strings.settings_portal_login_description),
        )
        SettingsChoiceRow(
            title = stringResource(MR.strings.settings_portal_secure),
            description = stringResource(MR.strings.settings_portal_secure_description),
            selected = settings.portalAuthenticationMode == PortalAuthenticationMode.SECURE_CREDENTIALS,
            enabled = false,
            onClick = {
                onPortalAuthenticationModeSelected(PortalAuthenticationMode.SECURE_CREDENTIALS)
            },
        )
        SettingsChoiceRow(
            title = stringResource(MR.strings.settings_portal_manual),
            description = stringResource(MR.strings.settings_portal_manual_description),
            selected = settings.portalAuthenticationMode == PortalAuthenticationMode.MANUAL_PORTAL,
            onClick = {
                onPortalAuthenticationModeSelected(PortalAuthenticationMode.MANUAL_PORTAL)
            },
        )

        if (backendEndpoint != null) {
            HorizontalDivider()

            SettingsSectionTitle(
                title = stringResource(MR.strings.settings_course_source),
                description = stringResource(MR.strings.settings_course_source_description),
            )
            BackendEndpointSection(
                state = backendEndpoint,
                onUseSource = onUseCourseSource,
            )
        }
    }
}

@Composable
private fun SettingsSectionTitle(
    title: String,
    description: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MpcSpacing.space1)) {
        Text(
            text = title,
            style = MpcTheme.typography.headline,
            color = MpcTheme.colors.label,
        )
        Text(
            text = description,
            style = MpcTheme.typography.supporting,
            color = MpcTheme.colors.labelSecondary,
        )
    }
}

@Composable
private fun SettingsChoiceRow(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = if (enabled) onClick else null,
            enabled = enabled,
        )
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) MpcTheme.colors.label else MpcTheme.colors.labelSecondary,
            )
            Text(
                text = description,
                style = MpcTheme.typography.caption,
                color = MpcTheme.colors.labelSecondary,
            )
        }
    }
}
