package org.mpc.domain.model

import kotlin.time.Instant

val DEFAULT_NCU_DEPARTMENTS: List<String> = listOf(
    "資訊工程學系",
    "電機工程學系",
    "通訊工程學系",
    "資訊管理學系",
    "企業管理學系",
    "財務金融學系",
    "經濟學系",
    "機械工程學系",
    "化學工程與材料工程學系",
    "土木工程學系",
    "數學系",
    "物理學系",
    "化學學系",
    "光電科學與工程學系",
    "中國文學系",
    "英美語文學系",
    "法國語文學系",
    "大氣科學學系",
    "地球科學學系",
    "客家語文暨社會科學學系",
    "生命科學系",
    "生醫科學與工程學系",
)

enum class AnnouncementScope(val label: String) {
    AFFILIATION("身分別／處室公告"),
    DEPARTMENT("系所公告"),
}

enum class UserAffiliation(val label: String) {
    ALL("全部身分"),
    UNDERGRADUATE("學士班"),
    GRADUATE("碩博士班"),
    INTERNATIONAL("國際生"),
    FACULTY("教職員"),
}

data class AnnouncementSummary(
    val announcementId: String,
    val scope: AnnouncementScope,
    val sourceId: String,
    val category: String,
    val unitName: String,
    val collegeId: String? = null,
    val collegeName: String = "",
    val departmentId: String? = null,
    val departmentName: String = "",
    val affiliations: List<UserAffiliation> = listOf(UserAffiliation.ALL),
    val title: String,
    val summary: Map<String, String> = emptyMap(),
    val publishedDate: String? = null,
    val url: String,
    val attachmentUrl: String? = null,
    val isPinned: Boolean = false,
)

data class AnnouncementFeed(
    val lastUpdated: Instant,
    val announcements: List<AnnouncementSummary>,
)
