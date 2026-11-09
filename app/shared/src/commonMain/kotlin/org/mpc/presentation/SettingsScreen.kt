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
import org.mpc.domain.model.AppSettings
import org.mpc.domain.model.AppThemeMode
import org.mpc.domain.model.PortalAuthenticationMode
import org.mpc.domain.model.TimetableColorMode
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme

@Composable
fun SettingsScreen(
    settings: AppSettings,
    settingsWriteFailed: Boolean = false,
    modifier: Modifier = Modifier,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onPortalAuthenticationModeSelected: (PortalAuthenticationMode) -> Unit,
    onTimetableColorModeSelected: (TimetableColorMode) -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = MpcSpacing.space3),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (settingsWriteFailed) {
            Text(
                text = "設定無法儲存，請稍後再試。",
                color = MpcTheme.colors.destructive,
                style = MpcTheme.typography.supporting,
            )
        }

        SettingsSectionTitle(
            title = "外觀",
            description = "選擇 App 使用系統、淺色或深色配色。",
        )
        SettingsChoiceRow(
            title = "跟隨系統",
            description = "使用裝置目前的外觀設定",
            selected = settings.themeMode == AppThemeMode.SYSTEM,
            onClick = { onThemeModeSelected(AppThemeMode.SYSTEM) },
        )
        SettingsChoiceRow(
            title = "淺色",
            description = "固定使用淺色配色",
            selected = settings.themeMode == AppThemeMode.LIGHT,
            onClick = { onThemeModeSelected(AppThemeMode.LIGHT) },
        )
        SettingsChoiceRow(
            title = "深色",
            description = "固定使用深色配色",
            selected = settings.themeMode == AppThemeMode.DARK,
            onClick = { onThemeModeSelected(AppThemeMode.DARK) },
        )

        HorizontalDivider()

        SettingsSectionTitle(
            title = "課表配色",
            description = "選擇課表中課程的配色方式。",
        )
        SettingsChoiceRow(
            title = "依課程類別",
            description = "依課程類別使用對應的配色。",
            selected = settings.timetableColorMode == TimetableColorMode.CATEGORY,
            onClick = { onTimetableColorModeSelected(TimetableColorMode.CATEGORY) },
        )
        SettingsChoiceRow(
            title = "隨機配色",
            description = "隨機為每門課程指定固定顏色；同一門課每次上課及畫面重繪都維持相同顏色。",
            selected = settings.timetableColorMode == TimetableColorMode.SHUFFLED,
            onClick = { onTimetableColorModeSelected(TimetableColorMode.SHUFFLED) },
        )

        HorizontalDivider()

        SettingsSectionTitle(
            title = "Portal 登入方式",
            description = "目前可在 App 內手動登入 Portal。安全儲存登入資訊尚未開放。",
        )
        SettingsChoiceRow(
            title = "安全儲存登入資訊（尚未開放）",
            description = "需要先完成平台憑證儲存與登入整合",
            selected = settings.portalAuthenticationMode == PortalAuthenticationMode.SECURE_CREDENTIALS,
            enabled = false,
            onClick = {
                onPortalAuthenticationModeSelected(PortalAuthenticationMode.SECURE_CREDENTIALS)
            },
        )
        SettingsChoiceRow(
            title = "Portal 手動登入",
            description = "在 App 內的 Portal WebView 完成登入",
            selected = settings.portalAuthenticationMode == PortalAuthenticationMode.MANUAL_PORTAL,
            onClick = {
                onPortalAuthenticationModeSelected(PortalAuthenticationMode.MANUAL_PORTAL)
            },
        )
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
