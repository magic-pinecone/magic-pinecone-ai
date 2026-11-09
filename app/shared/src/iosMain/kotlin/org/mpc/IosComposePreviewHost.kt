package org.mpc

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import org.mpc.bridge.CourseCatalogSnapshot
import org.mpc.domain.model.AppThemeMode
import org.mpc.domain.model.TimetableColorMode
import org.mpc.presentation.preview.ComposePreviewFixtures
import org.mpc.presentation.preview.ComposePreviewScenario
import org.mpc.presentation.preview.ComposePreviewScenarioContent
import org.mpc.presentation.theme.MpcTheme
import platform.UIKit.UIViewController

object IosComposePreviewHost {
    val catalogSnapshot: CourseCatalogSnapshot
        get() = CourseCatalogSnapshot(
            semester = ComposePreviewFixtures.courseResult.semester,
            courses = ComposePreviewFixtures.courses,
            isLoading = false,
        )

    fun createController(
        scenario: ComposePreviewScenario,
        isDarkTheme: Boolean,
    ): UIViewController = ComposeUIViewController {
        MpcTheme(
            themeMode = if (isDarkTheme) AppThemeMode.DARK else AppThemeMode.LIGHT,
            timetableColorMode = TimetableColorMode.CATEGORY,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MpcTheme.colors.background,
            ) {
                ComposePreviewScenarioContent(scenario)
            }
        }
    }
}
