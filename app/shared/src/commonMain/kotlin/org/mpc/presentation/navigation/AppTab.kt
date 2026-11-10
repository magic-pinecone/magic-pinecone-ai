package org.mpc.presentation.navigation

import dev.icerock.moko.resources.StringResource
import org.mpc.shared.MR
import org.mpc.shared.shell_tab_course_selection
import org.mpc.shared.shell_tab_home
import org.mpc.shared.shell_tab_news
import org.mpc.shared.shell_tab_portal
import org.mpc.shared.shell_tab_settings

enum class AppTab(
    val title: StringResource,
) {
    HOME(MR.strings.shell_tab_home),
    NEWS(MR.strings.shell_tab_news),
    PORTAL(MR.strings.shell_tab_portal),
    COURSE_SELECTION(MR.strings.shell_tab_course_selection),
    SETTINGS(MR.strings.shell_tab_settings),
}
