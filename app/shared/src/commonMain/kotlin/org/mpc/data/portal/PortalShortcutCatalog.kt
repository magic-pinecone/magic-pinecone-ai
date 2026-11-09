package org.mpc.data.portal

import org.mpc.domain.model.PORTAL_HOME_URL
import org.mpc.domain.model.PortalShortcutDestination
import org.mpc.domain.model.PortalShortcutIcon
import org.mpc.domain.model.PortalShortcutItem
import org.mpc.domain.model.PortalShortcutSection
import org.mpc.domain.model.PortalShortcutTint
import org.mpc.domain.model.portalUrl

/** Every Portal service the app lists, each once, grouped by what it is for. */
val defaultPortalShortcutSections: List<PortalShortcutSection> = listOf(
    section(
        title = "課務",
        tint = PortalShortcutTint.BLUE,
        items = listOf(
            portalShortcut("新 ee-class", PortalShortcutIcon.BOOK, "/system/ncueeclass"),
            shortcut("選課系統", PortalShortcutIcon.EVENT, "https://cis.ncu.edu.tw/Course/"),
            portalShortcut("成績查詢", PortalShortcutIcon.GRADE, "/system/incu-studentscore"),
            portalShortcut("期中預警查詢", PortalShortcutIcon.WARNING, "/system/incu-ewarningstudent"),
        ),
    ),
    section(
        title = "學籍",
        tint = PortalShortcutTint.PURPLE,
        items = listOf(
            portalShortcut("學籍系統", PortalShortcutIcon.ACCOUNT_BALANCE, "/system/incu-registrationflow"),
            portalShortcut("畢業資格審查", PortalShortcutIcon.SCHOOL, "/system/incu-graduate-2"),
            portalShortcut("學生證掛失", PortalShortcutIcon.CREDIT_CARD_OFF, "/system/32"),
        ),
    ),
    section(
        title = "學費與補助",
        tint = PortalShortcutTint.GREEN,
        items = listOf(
            portalShortcut("學費繳費單", PortalShortcutIcon.REQUEST_QUOTE, "/system/82"),
            portalShortcut("學費繳費證明", PortalShortcutIcon.REQUEST_QUOTE, "/system/tuition"),
            portalShortcut("獎助學金暨工讀管理系統", PortalShortcutIcon.WORKSPACE_PREMIUM, "/system/134"),
            portalShortcut("就學補助系統", PortalShortcutIcon.SAVINGS, "/system/42"),
            portalShortcut("撥帳系統", PortalShortcutIcon.ACCOUNT_BALANCE, "/system/46"),
        ),
    ),
    section(
        title = "校園服務",
        tint = PortalShortcutTint.ORANGE,
        items = listOf(
            portalShortcut("服務櫃台", PortalShortcutIcon.SUPPORT_AGENT, "/system/incu"),
            portalShortcut("圖書館服務平台", PortalShortcutIcon.LOCAL_LIBRARY, "/system/library-cloud-services"),
            portalShortcut("個別諮商", PortalShortcutIcon.SUPPORT_AGENT, "/system/consult"),
            portalShortcut("人事系統", PortalShortcutIcon.BADGE, "/system/humanoauth"),
        ),
    ),
    section(
        title = "網路與帳號",
        tint = PortalShortcutTint.TEAL,
        items = listOf(
            portalShortcut("NCU Mail", PortalShortcutIcon.MAIL, "/system/129"),
            portalShortcut("宿舍網路", PortalShortcutIcon.WIFI, "/system/dormnet"),
            portalShortcut("Office 365", PortalShortcutIcon.WORKSPACES, "/system/office365"),
            portalShortcut("Google Workspace", PortalShortcutIcon.APPS, "/system/gsuite"),
            portalShortcut("電算中心客服", PortalShortcutIcon.HEADSET, "/system/sdsystem"),
        ),
    ),
)

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

fun portalLoginDestination(url: String = PORTAL_HOME_URL): PortalShortcutDestination = PortalShortcutDestination(
    title = "Portal 登入",
    url = url,
)

private typealias TintedShortcut = (PortalShortcutTint) -> PortalShortcutItem

private fun section(
    title: String,
    tint: PortalShortcutTint,
    items: List<TintedShortcut>,
): PortalShortcutSection = PortalShortcutSection(title = title, items = items.map { it(tint) })

private fun portalShortcut(
    label: String,
    icon: PortalShortcutIcon,
    path: String,
): TintedShortcut = shortcut(label, icon, portalUrl(path))

private fun shortcut(
    label: String,
    icon: PortalShortcutIcon,
    url: String,
): TintedShortcut = { tint ->
    PortalShortcutItem(
        label = label,
        icon = icon,
        tint = tint,
        destination = PortalShortcutDestination(title = label, url = url),
    )
}
