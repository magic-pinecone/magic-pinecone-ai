package org.mpc.data.portal

import dev.icerock.moko.resources.StringResource
import org.mpc.domain.model.PORTAL_HOME_URL
import org.mpc.domain.model.PortalShortcutDestination
import org.mpc.domain.model.PortalShortcutIcon
import org.mpc.domain.model.PortalShortcutItem
import org.mpc.domain.model.PortalShortcutSection
import org.mpc.domain.model.PortalShortcutTint
import org.mpc.domain.model.portalUrl
import org.mpc.shared.MR
import org.mpc.shared.portal_section_accounts
import org.mpc.shared.portal_section_campus
import org.mpc.shared.portal_section_courses
import org.mpc.shared.portal_section_records
import org.mpc.shared.portal_section_tuition
import org.mpc.shared.portal_shortcut_counseling
import org.mpc.shared.portal_shortcut_course_selection
import org.mpc.shared.portal_shortcut_dorm_network
import org.mpc.shared.portal_shortcut_eeclass
import org.mpc.shared.portal_shortcut_google_workspace
import org.mpc.shared.portal_shortcut_grades
import org.mpc.shared.portal_shortcut_graduation_review
import org.mpc.shared.portal_shortcut_it_help
import org.mpc.shared.portal_shortcut_library
import org.mpc.shared.portal_shortcut_lost_student_id
import org.mpc.shared.portal_shortcut_mail
import org.mpc.shared.portal_shortcut_midterm_warnings
import org.mpc.shared.portal_shortcut_office365
import org.mpc.shared.portal_shortcut_payouts
import org.mpc.shared.portal_shortcut_personnel
import org.mpc.shared.portal_shortcut_scholarships
import org.mpc.shared.portal_shortcut_service_desk
import org.mpc.shared.portal_shortcut_student_records
import org.mpc.shared.portal_shortcut_study_subsidies
import org.mpc.shared.portal_shortcut_tuition_bill
import org.mpc.shared.portal_shortcut_tuition_receipt

/** Every Portal service the app lists, each once, grouped by what it is for. */
private val catalog: List<CatalogSection> = listOf(
    CatalogSection(
        title = MR.strings.portal_section_courses,
        tint = PortalShortcutTint.BLUE,
        items = listOf(
            portalShortcut(MR.strings.portal_shortcut_eeclass, PortalShortcutIcon.BOOK, "/system/ncueeclass"),
            CatalogItem(MR.strings.portal_shortcut_course_selection, PortalShortcutIcon.EVENT, "https://cis.ncu.edu.tw/Course/"),
            portalShortcut(MR.strings.portal_shortcut_grades, PortalShortcutIcon.GRADE, "/system/incu-studentscore"),
            portalShortcut(MR.strings.portal_shortcut_midterm_warnings, PortalShortcutIcon.WARNING, "/system/incu-ewarningstudent"),
        ),
    ),
    CatalogSection(
        title = MR.strings.portal_section_records,
        tint = PortalShortcutTint.PURPLE,
        items = listOf(
            portalShortcut(MR.strings.portal_shortcut_student_records, PortalShortcutIcon.ACCOUNT_BALANCE, "/system/incu-registrationflow"),
            portalShortcut(MR.strings.portal_shortcut_graduation_review, PortalShortcutIcon.SCHOOL, "/system/incu-graduate-2"),
            portalShortcut(MR.strings.portal_shortcut_lost_student_id, PortalShortcutIcon.CREDIT_CARD_OFF, "/system/32"),
        ),
    ),
    CatalogSection(
        title = MR.strings.portal_section_tuition,
        tint = PortalShortcutTint.GREEN,
        items = listOf(
            portalShortcut(MR.strings.portal_shortcut_tuition_bill, PortalShortcutIcon.REQUEST_QUOTE, "/system/82"),
            portalShortcut(MR.strings.portal_shortcut_tuition_receipt, PortalShortcutIcon.REQUEST_QUOTE, "/system/tuition"),
            portalShortcut(MR.strings.portal_shortcut_scholarships, PortalShortcutIcon.WORKSPACE_PREMIUM, "/system/134"),
            portalShortcut(MR.strings.portal_shortcut_study_subsidies, PortalShortcutIcon.SAVINGS, "/system/42"),
            portalShortcut(MR.strings.portal_shortcut_payouts, PortalShortcutIcon.ACCOUNT_BALANCE, "/system/46"),
        ),
    ),
    CatalogSection(
        title = MR.strings.portal_section_campus,
        tint = PortalShortcutTint.ORANGE,
        items = listOf(
            portalShortcut(MR.strings.portal_shortcut_service_desk, PortalShortcutIcon.SUPPORT_AGENT, "/system/incu"),
            portalShortcut(MR.strings.portal_shortcut_library, PortalShortcutIcon.LOCAL_LIBRARY, "/system/library-cloud-services"),
            portalShortcut(MR.strings.portal_shortcut_counseling, PortalShortcutIcon.SUPPORT_AGENT, "/system/consult"),
            portalShortcut(MR.strings.portal_shortcut_personnel, PortalShortcutIcon.BADGE, "/system/humanoauth"),
        ),
    ),
    CatalogSection(
        title = MR.strings.portal_section_accounts,
        tint = PortalShortcutTint.TEAL,
        items = listOf(
            portalShortcut(MR.strings.portal_shortcut_mail, PortalShortcutIcon.MAIL, "/system/129"),
            portalShortcut(MR.strings.portal_shortcut_dorm_network, PortalShortcutIcon.WIFI, "/system/dormnet"),
            portalShortcut(MR.strings.portal_shortcut_office365, PortalShortcutIcon.WORKSPACES, "/system/office365"),
            portalShortcut(MR.strings.portal_shortcut_google_workspace, PortalShortcutIcon.APPS, "/system/gsuite"),
            portalShortcut(MR.strings.portal_shortcut_it_help, PortalShortcutIcon.HEADSET, "/system/sdsystem"),
        ),
    ),
)

/** Every text the catalog shows, so Compose can resolve them all with `stringResource`. */
val portalShortcutTexts: List<StringResource> = catalog.flatMap { section -> listOf(section.title) + section.items.map { it.label } }

/** The catalog with its titles and labels resolved by [text], normally in the app language. */
fun portalShortcutSections(text: (StringResource) -> String): List<PortalShortcutSection> = catalog.map { section ->
    PortalShortcutSection(
        title = text(section.title),
        items = section.items.map { item ->
            val label = text(item.label)
            PortalShortcutItem(
                label = label,
                icon = item.icon,
                tint = section.tint,
                destination = PortalShortcutDestination(title = label, url = item.url),
            )
        },
    )
}

/** Favorites for someone who has not customized them yet. */
val defaultPortalFavoriteIds: List<String> = listOf(
    portalUrl("/system/ncueeclass"),
    "https://cis.ncu.edu.tw/Course/",
    portalUrl("/system/129"),
    portalUrl("/system/incu-studentscore"),
    portalUrl("/system/incu"),
    portalUrl("/system/library-cloud-services"),
)

fun filterPortalShortcutSections(
    sections: List<PortalShortcutSection>,
    query: String,
): List<PortalShortcutSection> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return sections

    return sections.mapNotNull { section ->
        val filteredItems = section.items.filter { item ->
            item.label.contains(normalizedQuery, ignoreCase = true)
        }
        section.takeIf { filteredItems.isNotEmpty() }?.copy(items = filteredItems)
    }
}

fun portalLoginDestination(title: String, url: String = PORTAL_HOME_URL): PortalShortcutDestination = PortalShortcutDestination(
    title = title,
    url = url,
)

private class CatalogSection(
    val title: StringResource,
    val tint: PortalShortcutTint,
    val items: List<CatalogItem>,
)

private class CatalogItem(
    val label: StringResource,
    val icon: PortalShortcutIcon,
    val url: String,
)

private fun portalShortcut(
    label: StringResource,
    icon: PortalShortcutIcon,
    path: String,
): CatalogItem = CatalogItem(label, icon, portalUrl(path))
