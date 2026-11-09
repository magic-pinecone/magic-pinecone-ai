package org.mpc.presentation

import org.mpc.domain.model.Degree

internal interface GraduationLanguageLabels {
    val title: String
    val otherLanguage: String
    val allColleges: String
    val admissionYear: String
    val earlierYear: String
    val laterYear: String
    val applyAt: String
    val englishTests: String
    val otherLanguages: String
    val internationalStudents: String
    val alternatives: String
    val procedure: String
    val notes: String
    val sources: String
    val noRule: String
    val noRuleFound: String
    val notChecked: String
    val chinese: String
    val chineseRequirements: String
    val notGraduationRequirements: String
    val openQuestions: String
    val verifiedOn: String
    val needsBackend: String
    val failed: String
    val retry: String

    fun degree(degree: Degree): String

    fun cohort(year: Int): String
}

internal object EnglishLabels : GraduationLanguageLabels {
    override val title = "Graduation language requirements"
    override val otherLanguage = "中文"
    override val allColleges = "All colleges"
    override val admissionYear = "Admission year (ROC academic year)"
    override val earlierYear = "Earlier year"
    override val laterYear = "Later year"
    override val applyAt = "Where to apply:"
    override val englishTests = "English tests accepted (any one)"
    override val otherLanguages = "Other languages accepted"
    override val internationalStudents = "For international students"
    override val alternatives = "If you do not reach the score"
    override val procedure = "How to get it verified"
    override val notes = "Notes"
    override val sources = "Official sources"
    override val noRule = "No rule was found for this selection. That does not mean there is no requirement: " +
        "check your department's own regulations."
    override val noRuleFound = "Checked, no language rule found"
    override val notChecked = "Not checked yet"
    override val chinese = "Chinese language"
    override val chineseRequirements = "What affects graduation"
    override val notGraduationRequirements = "Often mistaken for graduation requirements"
    override val openQuestions = "Still to confirm with the university"
    override val verifiedOn = "English text is an unofficial translation; the Chinese original prevails. Checked against official sources on"
    override val needsBackend = "This page needs a Magic Pinecone backend. Enter its address under Settings › 課程資料來源."
    override val failed = "Could not load the requirements."
    override val retry = "Try again"

    override fun degree(degree: Degree) = when (degree) {
        Degree.BACHELOR -> "Bachelor"
        Degree.MASTER -> "Master"
        Degree.PHD -> "PhD"
    }

    override fun cohort(year: Int) = "$year (${year + ROC_OFFSET})"
}

internal object ChineseLabels : GraduationLanguageLabels {
    override val title = "畢業語言門檻"
    override val otherLanguage = "English"
    override val allColleges = "全部學院"
    override val admissionYear = "入學學年度"
    override val earlierYear = "前一學年度"
    override val laterYear = "後一學年度"
    override val applyAt = "辦理單位："
    override val englishTests = "可採計的英文檢定（擇一）"
    override val otherLanguages = "可採計的其他外語"
    override val internationalStudents = "外籍生須知"
    override val alternatives = "未達標時"
    override val procedure = "審核方式"
    override val notes = "備註"
    override val sources = "官方來源"
    override val noRule = "這個條件下沒有找到規定。這不代表沒有門檻，請查閱系所的修業規定。"
    override val noRuleFound = "已查閱、沒有語言規定"
    override val notChecked = "尚未查閱"
    override val chinese = "華語"
    override val chineseRequirements = "與畢業有關的規定"
    override val notGraduationRequirements = "常被誤認為畢業條件"
    override val openQuestions = "仍待向學校確認"
    override val verifiedOn = "與官方來源核對日期："
    override val needsBackend = "這個頁面需要神奇松果後端。請到「設定 › 課程資料來源」填入後端位址。"
    override val failed = "無法載入畢業語言門檻。"
    override val retry = "重試"

    override fun degree(degree: Degree) = when (degree) {
        Degree.BACHELOR -> "學士班"
        Degree.MASTER -> "碩士班"
        Degree.PHD -> "博士班"
    }

    override fun cohort(year: Int) = "$year 學年度"
}

private const val ROC_OFFSET = 1911
