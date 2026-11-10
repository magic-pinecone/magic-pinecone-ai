package org.mpc.presentation.preview

import org.mpc.domain.model.ChineseRequirements
import org.mpc.domain.model.Degree
import org.mpc.domain.model.GraduateOverview
import org.mpc.domain.model.GraduationLanguage
import org.mpc.domain.model.GraduationLanguageGeneral
import org.mpc.domain.model.GraduationLanguageRule
import org.mpc.domain.model.GraduationLanguageSource
import org.mpc.domain.model.LanguageThreshold
import org.mpc.domain.model.LocalizedText

/**
 * An excerpt of `backend/app/data/graduation_language/requirements.json` for previews: 7 of its 48 rules and
 * the first 2 entries of each overview list, copied as served by `GET /api/v1/intl/graduation-language`.
 * Nothing here is invented; regenerate it from the backend data rather than editing a number by hand.
 */
internal object GraduationLanguagePreviewFixture {
    val requirements = GraduationLanguage(
        verifiedOn = "2026-11-10",
        disclaimer = LocalizedText(zh = "本資料由人工自中央大學官方頁面整理，僅供參考；實際標準以語言中心與各院系公告為準。", en = "Transcribed by hand from official NCU pages for reference only. The Language Center's and your college's own announcements prevail."),
        tests = mapOf(
            "toefl_ibt" to LocalizedText(zh = "托福網路測驗", en = "TOEFL iBT"),
            "toefl_itp" to LocalizedText(zh = "托福紙筆測驗", en = "TOEFL ITP"),
            "toeic_lr" to LocalizedText(zh = "多益（聽讀）測驗", en = "TOEIC Listening & Reading"),
            "gept_hi_first" to LocalizedText(zh = "全民英檢中高級初試", en = "GEPT High-Intermediate (first stage)"),
            "ielts" to LocalizedText(zh = "雅思", en = "IELTS"),
            "linguaskill_lr" to LocalizedText(zh = "領思（聽讀）測驗", en = "Linguaskill (Listening & Reading)"),
            "bestep" to LocalizedText(zh = "培力英檢", en = "BESTEP"),
            "bestep_lr" to LocalizedText(zh = "培力英檢（聽讀測驗）", en = "BESTEP (Listening & Reading)"),
        ),
        general = GraduationLanguageGeneral(
            summary = LocalizedText(zh = "大學部學生畢業前須通過所屬學院訂定的英文（部分學院可用第二外語）檢定標準，並完成畢業門檻審核；標準以入學年度為準。", en = "Undergraduates must meet their college's English proficiency standard (some colleges also accept a second foreign language) and have it verified before graduating. The standard of your admission year applies."),
            procedure = listOf(
                "英檢合格者填寫所屬學院的審核申請表，並檢附成績單正本（檢核後歸還；影本或網路成績不受理，托福、雅思、領思可出示電子成績單）。",
                "資電、管理、生醫理工、客家學院學生到語言中心辦理；理學院、工學院學生洽院辦；地科學院、文學院學生洽系辦。",
            ),
            alternatives = listOf(
                "未通過英文能力鑑定的大學部學生，須選修一學年的「進修英文」（兩學期、共 4 學分）且成績及格，才能畢業。",
                "選修進修英文的前提是入學後參加過細則第 3 條所列的任一項英文鑑定考試；從未參加過者不得修習。未達合格標準者，可持成績單正本到語言中心加選。",
            ),
            internationalStudents = listOf(
                "學則、英文能力鑑定細則、語言中心頁面與各學院表單都沒有把外籍生排除在英文畢業門檻之外；教務處的學位證書公告對所有學士班學生一體適用。就現有官方文件看，外籍學士班學生同樣要通過門檻或修進修英文。",
                "沒有學測或指考成績的學生，只要入學後參加過一次細則所列的英文鑑定考試，就具備進修英文的修課資格。",
            ),
            legalBasis = listOf(
                "學則第 48 條第 2 款：學士班學生須「選修『進修英文』成績及格，或通過本校語言中心認定之校內外英文能力鑑定考試合格」才能畢業；各學院自訂外文能力鑑定合格標準並經教務會議通過者，從其規定。",
                "英文能力鑑定暨進修英文課程實施細則第 3 條列出各學院的合格分數，並規定三種例外：英美語文學系畢業生認定為通過；各學院自訂標準者從其規定；身心障礙生得專案處理或免予鑑定。條文沒有外籍生的例外。",
            ),
            graduate = GraduateOverview(
                summary = LocalizedText(zh = "研究所沒有全校性的語言畢業門檻；學則第 58 條把研究生的畢業條件交給各系所自訂。目前有院級規定的是工學院碩士班、管理學院碩士班與永續與綠能科技研究學院。", en = "There is no university-wide language requirement for graduate degrees: Study Regulations art. 58 leaves graduation conditions to each department. College-level rules exist for master's programs in Engineering and Management and for the Graduate College of Sustainability and Green Energy."),
                internationalStudents = listOf(
                    "明文讓外籍生免除英文門檻的有：機械系碩士班、光機電工程碩士班、能源工程研究所碩士班（外籍學位生不受限）；人工智慧國際碩士學位學程與網路學習科技研究所博士班（英語母語或在英語系國家取得學位者可申請免除）。",
                    "對外籍生另加華語要求的有：環境工程研究所（兩門華語課或 TOCFL 入門級）、客家研究博士班（TOCFL 進階級）、物理系 TIGP-MST 與跨領域神經科學 TIGP-INS（初級中文課）。",
                ),
                noRuleFound = listOf(
                    "資訊電機學院、理學院、地球科學學院、文學院、生醫理工學院沒有找到院級的研究所語言門檻。",
                    "查過系所修業辦法而沒有語言門檻的包括：資工（碩、博）、電機（碩、博）、通訊碩、網學所碩、數學（碩、博）、統計所、物理（碩、博）、化學（碩、博）、光電（碩、博）、天文所、認知神經所（碩、博）、大氣（碩、博）、地球物理碩、應用地質所（碩、博）、水文與海洋所（碩、博）、遙測科技碩士學程、環境科技博士學程、企管博、工管所博、產經所博、土木（碩、博）、材料所博、生科（碩、博）、生物醫學工程碩、系統生物與生物資訊、跨領域轉譯醫學博、英文碩、中文博、歷史所碩、藝術學所碩、學習與教學所。",
                ),
                notChecked = listOf(
                    "還沒取得規定的系所：營建管理研究所（網站只提供 HTTP）、太空科學與工程學系（修業規定頁面無法以程式讀取）、地球物理博士班的現行版本（連結失效，只取得 106 年版，該版沒有語言規定）、各在職專班。",
                    "沒有列出的系所不代表沒有門檻，請以系所修業辦法為準；`/intl/programs` 的 `regulation_url` 提供各系所修業規定的連結。",
                ),
            ),
        ),
        chinese = ChineseRequirements(
            summary = LocalizedText(zh = "中央大學沒有全校性的華語能力畢業門檻（例如 TOCFL 等級）。與畢業有關的華語規定是學士班的「國文」共同必修：外籍生以華語或僑生國文課程替代。", en = "NCU has no university-wide Chinese proficiency level (such as a TOCFL level) required for graduation. What does affect graduation is the undergraduate common requirement in Chinese, which international students fulfil with Chinese-as-a-second-language courses instead."),
            requirements = listOf(
                "大一國文修課實施辦法第 5 點：非中文系外籍生不需修中文系開設的大一國文，但須修語言中心開設的華語課程 6 學分，並在畢業前辦理國文課程抵修手續。",
                "同辦法第 5 點：中文系外籍生須參加中文能力分級測驗；初級組除修中文系大一國文外，還要依程度選修語言中心課程（不列入畢業學分），通過後才能修「僑生國文」。",
            ),
            openQuestions = listOf(
                "上面兩份法規對非中文系外籍生的寫法不一致（華語課程 6 學分，或僑生國文 5 學分）。實際做法請向教務處或中文系確認。",
                "研究所沒有學校層級的華語要求；各系所是否另有規定，要看系所的修業規定。",
            ),
            notGraduationRequirements = listOf(
                "外籍生獎助學金：申請時須有通過本校一門華語課程的成績及格證明，或華語文溝通能力證明（秋季班入學者申請第一學年第二學期時可免）。這是領獎助學金的條件，不是畢業條件；碩士階段修過的華語課不能沿用到博士階段。",
                "華語文能力測驗獎勵金：修過語言中心華語課程並通過 TOCFL 高階級（Level 4）以上者，可申請 2,000 元獎勵。",
            ),
        ),
        rules = listOf(
            GraduationLanguageRule(
                id = "bachelor-eecs",
                degree = Degree.BACHELOR,
                college = LocalizedText(zh = "資訊電機學院", en = "College of Electrical Engineering and Computer Science"),
                applyAt = LocalizedText(zh = "語言中心", en = "Language Center"),
                english = mapOf(
                    "toefl_ibt" to listOf(LanguageThreshold(value = "4 級")),
                    "toefl_itp" to listOf(LanguageThreshold(value = "483", cohortMax = 114), LanguageThreshold(value = "543", cohortMin = 115)),
                    "toeic_lr" to listOf(LanguageThreshold(value = "600", cohortMax = 114), LanguageThreshold(value = "785（聽力 400、閱讀 385）", cohortMin = 115)),
                    "gept_hi_first" to listOf(LanguageThreshold(value = "143", cohortMax = 114), LanguageThreshold(value = "160", cohortMin = 115)),
                    "ielts" to listOf(LanguageThreshold(value = "5.0", cohortMax = 114), LanguageThreshold(value = "5.5", cohortMin = 115)),
                    "linguaskill_lr" to listOf(LanguageThreshold(value = "145", cohortMax = 114), LanguageThreshold(value = "150", cohortMin = 115)),
                    "bestep" to listOf(LanguageThreshold(value = "聽說讀寫總分 700，且聽力 90、閱讀 90、口說 260、寫作 260")),
                ),
                notes = listOf(
                    "表單寫「114 年前適用／115 年後適用」並註明「以入學年度標準為主」；這裡解讀為 114 學年度（含）以前入學與 115 學年度（含）以後入學，送件前請向語言中心確認。",
                    "英文能力鑑定細則（114-06-18 核備）對資電學院仍列舊標準（例如多益 600 分）；785 分等新標準出自語言中心 115 學年度起適用的表單與圖表，屬於細則第 3 條第 2 款的學院自訂標準。",
                ),
                sources = listOf("lc-threshold-chart", "lc-form-general", "aa-english-proficiency-rules"),
            ),
            GraduationLanguageRule(
                id = "bachelor-management",
                degree = Degree.BACHELOR,
                college = LocalizedText(zh = "管理學院", en = "College of Management"),
                applyAt = LocalizedText(zh = "語言中心", en = "Language Center"),
                english = mapOf(
                    "toefl_ibt" to listOf(LanguageThreshold(value = "3.5 級")),
                    "toefl_itp" to listOf(LanguageThreshold(value = "543")),
                    "toeic_lr" to listOf(LanguageThreshold(value = "700")),
                    "gept_hi_first" to listOf(LanguageThreshold(value = "160")),
                    "ielts" to listOf(LanguageThreshold(value = "5.5")),
                    "linguaskill_lr" to listOf(LanguageThreshold(value = "150")),
                    "bestep" to listOf(LanguageThreshold(value = "聽說讀寫總分 700")),
                ),
                notes = listOf(
                    "托福網路測驗：細則寫 72 分（舊制），語言中心圖表與表單寫新制 3.5 級。",
                ),
                sources = listOf("lc-threshold-chart", "lc-form-general", "aa-english-proficiency-rules"),
            ),
            GraduationLanguageRule(
                id = "bachelor-engineering",
                degree = Degree.BACHELOR,
                college = LocalizedText(zh = "工學院", en = "College of Engineering"),
                applyAt = LocalizedText(zh = "工學院辦公室", en = "College of Engineering office"),
                english = mapOf(
                    "toefl_ibt" to listOf(LanguageThreshold(value = "3.5 級")),
                    "toefl_itp" to listOf(LanguageThreshold(value = "483")),
                    "toeic_lr" to listOf(LanguageThreshold(value = "600")),
                    "gept_hi_first" to listOf(LanguageThreshold(value = "143")),
                    "ielts" to listOf(LanguageThreshold(value = "5.0")),
                    "linguaskill_lr" to listOf(LanguageThreshold(value = "145")),
                    "bestep" to listOf(LanguageThreshold(value = "聽說讀寫總分 690，且聽力 85、閱讀 85、口說 260、寫作 260", cohortMin = 114, note = "114 學年度起入學適用聽讀說寫測驗"), LanguageThreshold(value = "113 學年度（含）以前入學適用聽讀測驗；分數未列在表單或語言中心圖表上", cohortMax = 113)),
                ),
                otherLanguagesWithoutThreshold = listOf(
                    "日文：日本語能力試驗、實用日本語運用能力試驗",
                    "德文：歌德學院德語檢定測驗、德福考試",
                    "韓文：韓國語文能力測驗",
                    "法文：法語鑑定文憑、法語能力測驗",
                    "西班牙文：西班牙語檢定考試、西語職場能力檢定",
                    "泰文：泰語能力檢定、泰國語文檢定測驗",
                    "越南文：國際越南語認證",
                    "英文另接受 EnglishScore（聽讀測驗）",
                ),
                notes = listOf(
                    "工學院表單只列出可採計的檢定名稱；第二外語與 EnglishScore 的合格分數不在表單上，請洽工學院辦公室。",
                ),
                sources = listOf("lc-threshold-chart", "lc-form-engineering", "aa-english-proficiency-rules"),
            ),
            GraduationLanguageRule(
                id = "master-engineering",
                degree = Degree.MASTER,
                college = LocalizedText(zh = "工學院", en = "College of Engineering"),
                summary = LocalizedText(zh = "110 學年度起入學的工學院碩士班學生，申請學位考試前須達到下列任一英文檢定分數。", en = "Master's students of the College of Engineering admitted from academic year 110 must reach one of these English test scores before applying for the degree examination."),
                english = mapOf(
                    "toeic_lr" to listOf(LanguageThreshold(value = "600", cohortMin = 110)),
                    "toefl_itp" to listOf(LanguageThreshold(value = "483", cohortMin = 110)),
                    "toefl_ibt" to listOf(LanguageThreshold(value = "64 分；2026-01-21 起的新制成績為 3.5 級", cohortMin = 110)),
                    "ielts" to listOf(LanguageThreshold(value = "5.0", cohortMin = 110)),
                    "gept_hi_first" to listOf(LanguageThreshold(value = "143", cohortMin = 110)),
                    "bestep_lr" to listOf(LanguageThreshold(value = "170", cohortMin = 110)),
                ),
                alternatives = listOf(
                    "未達標者可憑取得入學資格後參加英檢的成績證明，修習一學期工學院碩士班進修英文課程，成績 70 分以上視為通過。",
                ),
                internationalStudents = listOf(
                    "學院標準本身沒有外籍生條款；第 5 條允許特殊情形由各系所專案處理。各系的修業辦法可能另有規定（例如機械系明文排除外籍學位生）。",
                ),
                sources = listOf("coe-master-english-standard"),
            ),
            GraduationLanguageRule(
                id = "master-management-115",
                degree = Degree.MASTER,
                college = LocalizedText(zh = "管理學院", en = "College of Management"),
                summary = LocalizedText(zh = "115 學年度起入學的管理學院碩士生須修一學年碩士班進修英文（MT4001、MT4002）且成績 70 分以上；英檢達 CEFR B2 者經系所審核可免修。IMBA 不適用。", en = "Management master's students admitted from academic year 115 must pass a one-year Remedial English course (MT4001, MT4002) with 70 or above; a CEFR B2 test score waives it after department review. IMBA is excluded."),
                english = mapOf(
                    "toeic_lr" to listOf(LanguageThreshold(value = "785", cohortMin = 115)),
                    "toefl_ibt" to listOf(LanguageThreshold(value = "4–4.5", cohortMin = 115)),
                    "toefl_itp" to listOf(LanguageThreshold(value = "543", cohortMin = 115)),
                    "ielts" to listOf(LanguageThreshold(value = "5.5（含）以上", cohortMin = 115)),
                    "gept_hi_first" to listOf(LanguageThreshold(value = "聽、讀通過", cohortMin = 115)),
                    "bestep_lr" to listOf(LanguageThreshold(value = "聽力 100–114、閱讀 100–114", cohortMin = 115)),
                ),
                alternatives = listOf(
                    "表列分數是免修門檻；沒有達到的人修完一學年進修英文課程即可。",
                    "各系所如自訂更嚴格的標準，從其規定。",
                ),
                internationalStudents = listOf(
                    "辦法沒有外籍生條款，只排除國際經營管理碩士學位學程（IMBA）的研究生；特殊情形得由各系所專案處理。",
                ),
                notes = listOf(
                    "B2 對照分數依管理學院公告，會隨公告調整（對照表 2026-02-09 更新）。",
                ),
                sources = listOf("mgt-master-english-115", "mgt-cefr-b2-table-115"),
            ),
            GraduationLanguageRule(
                id = "master-management-110",
                degree = Degree.MASTER,
                college = LocalizedText(zh = "管理學院", en = "College of Management"),
                summary = LocalizedText(zh = "110 至 114 學年度入學的管理學院碩士生適用舊版：須修一學年碩士班進修英文且成績 70 分以上，英檢達下列分數者可免修。IMBA 不適用。", en = "Management master's students admitted in academic years 110–114 follow the earlier version: a one-year Remedial English course with 70 or above, waived by one of these scores. IMBA is excluded."),
                english = mapOf(
                    "toeic_lr" to listOf(LanguageThreshold(value = "600", cohortMin = 110, cohortMax = 114)),
                    "toefl_itp" to listOf(LanguageThreshold(value = "483", cohortMin = 110, cohortMax = 114)),
                    "toefl_ibt" to listOf(LanguageThreshold(value = "64（舊制）", cohortMin = 110, cohortMax = 114)),
                    "ielts" to listOf(LanguageThreshold(value = "5.0", cohortMin = 110, cohortMax = 114)),
                    "gept_hi_first" to listOf(LanguageThreshold(value = "143", cohortMin = 110, cohortMax = 114)),
                ),
                notes = listOf(
                    "適用入學年度依管理學院規章頁的版本標示；送件前請向系所確認適用版本。",
                ),
                sources = listOf("mgt-master-english-110"),
            ),
            GraduationLanguageRule(
                id = "phd-information-management",
                degree = Degree.PHD,
                college = LocalizedText(zh = "管理學院", en = "College of Management"),
                department = LocalizedText(zh = "資訊管理學系", en = "Department of Information Management"),
                summary = LocalizedText(zh = "資訊系統組與管理組畢業時須達到各項英語能力檢測第三級／中高級的對應成績（例如多益 750 分）。", en = "The Information Systems and Management tracks require an English score at the high-intermediate level of the listed tests (for example TOEIC 750)."),
                english = mapOf(
                    "toeic_lr" to listOf(LanguageThreshold(value = "750")),
                ),
                alternatives = listOf(
                    "多益 730 分以上未達 750 分者，學位考試前在符合條件的國際學術會議以英文發表論文，視同符合英文畢業門檻。",
                ),
                notes = listOf(
                    "其他測驗的對應分數列在原文的對照表；該表的文字層排版錯亂，請直接看原文 PDF。",
                ),
                sources = listOf("im-phd-rules"),
            ),
        ),
        sources = listOf(
            GraduationLanguageSource(id = "lc-threshold-chart", title = "大學部英文畢業門檻（圖表）", url = "https://www.lc.ncu.edu.tw/files/system/images/self-study/英文畢業門檻英檢成績_R_2061020.png", org = "語言中心"),
            GraduationLanguageSource(id = "lc-form-general", title = "國立中央大學大學部「英文能力鑑定」審核申請表", url = "https://www.lc.ncu.edu.tw/files/system/files/國立中央大學大學部英文畢業門檻審核申請表(1151學期起適用)_0827.pdf", org = "語言中心"),
            GraduationLanguageSource(id = "lc-form-engineering", title = "工學院「外文能力鑑定」暨「外文能力獎勵」審核申請表", url = "https://www.lc.ncu.edu.tw/files/system/files/國立中央大學工學院(109學年起適用)「外文能力鑑定」暨「外文能力獎勵」審核申請表_v20241226(2).pdf", org = "工學院"),
            GraduationLanguageSource(id = "aa-english-proficiency-rules", title = "國立中央大學英文能力鑑定暨進修英文課程實施細則", url = "https://pdc.adm.ncu.edu.tw/static/file/19/1019/img/418/205291087.pdf", org = "教務處"),
            GraduationLanguageSource(id = "coe-master-english-standard", title = "國立中央大學工學院碩士班學生英文能力合格標準（115.06.10教務會議核備，中英對照）", url = "https://www.me.ncu.edu.tw/wp-content/uploads/NCUCE碩士班學生英文能力合格標準_v20260610教務會議核通.pdf", org = "工學院"),
            GraduationLanguageSource(id = "mgt-master-english-115", title = "國立中央大學管理學院碩士班進修英文課程實施辦法（115學年度新生適用）", url = "https://mgt.ncu.edu.tw/files/system/files/碩士班進修英文課程實施辦法(115學年度適用).pdf", org = "管理學院"),
            GraduationLanguageSource(id = "mgt-master-english-110", title = "國立中央大學管理學院碩士班進修英文課程實施辦法（110學年度入學適用）", url = "https://mgt.ncu.edu.tw/files/system/files/碩士班進修英文課程實施辦法(110學年適用).pdf", org = "管理學院"),
            GraduationLanguageSource(id = "mgt-cefr-b2-table-115", title = "《管院》英語免修門檻CEFR B2成績對照表（115學年度新生適用）", url = "https://mgt.ncu.edu.tw/files/system/files/表格/CEFR B2成績對照表115適用.pdf", org = "管理學院"),
            GraduationLanguageSource(id = "im-phd-rules", title = "國立中央大學資訊管理學系博士班研究生修業辦法（115學年度起適用）", url = "https://im.mgt.ncu.edu.tw/download/course/rules/資訊管理學系博士班研究生修業辦法_1150610.pdf", org = "管理學院／資訊管理學系"),
        ),
        en = mapOf(
            "英檢合格者填寫所屬學院的審核申請表，並檢附成績單正本（檢核後歸還；影本或網路成績不受理，托福、雅思、領思可出示電子成績單）。" to "Students who pass a test fill in their college's verification form and attach the original score report (returned after checking; photocopies and online scores are not accepted, though TOEFL, IELTS and Linguaskill may be shown as electronic score reports).",
            "資電、管理、生醫理工、客家學院學生到語言中心辦理；理學院、工學院學生洽院辦；地科學院、文學院學生洽系辦。" to "Students of the EECS, Management, Health Sciences and Technology, and Hakka colleges apply at the Language Center; Science and Engineering students apply at their college office; Earth Sciences and Liberal Arts students apply at their department office.",
            "未通過英文能力鑑定的大學部學生，須選修一學年的「進修英文」（兩學期、共 4 學分）且成績及格，才能畢業。" to "Undergraduates who have not passed the English proficiency requirement must take one academic year of Remedial English (進修英文; two semesters, 4 credits in total) and pass it in order to graduate.",
            "選修進修英文的前提是入學後參加過細則第 3 條所列的任一項英文鑑定考試；從未參加過者不得修習。未達合格標準者，可持成績單正本到語言中心加選。" to "You may take Remedial English only after sitting at least one of the English tests listed in art. 3 of the rules following admission; students who never sat one may not enrol. Students below the passing score can bring the original score report to the Language Center to add the course.",
            "學則、英文能力鑑定細則、語言中心頁面與各學院表單都沒有把外籍生排除在英文畢業門檻之外；教務處的學位證書公告對所有學士班學生一體適用。就現有官方文件看，外籍學士班學生同樣要通過門檻或修進修英文。" to "Neither the Study Regulations, the English proficiency rules, the Language Center pages nor the college forms exclude international students from the English graduation threshold, and the Academic Affairs notice on degree certificates applies to all bachelor's students alike. On the official documents available, international bachelor's students must also meet the threshold or take Remedial English.",
            "沒有學測或指考成績的學生，只要入學後參加過一次細則所列的英文鑑定考試，就具備進修英文的修課資格。" to "Students without a GSAT or AST score (most international students) are eligible for Remedial English once they have sat one of the English tests listed in the rules after admission.",
            "學則第 48 條第 2 款：學士班學生須「選修『進修英文』成績及格，或通過本校語言中心認定之校內外英文能力鑑定考試合格」才能畢業；各學院自訂外文能力鑑定合格標準並經教務會議通過者，從其規定。" to "Study Regulations art. 48(2): to graduate, bachelor's students must pass Remedial English or pass an English proficiency test recognised by the Language Center; where a college sets its own foreign-language standard approved by the Academic Affairs Committee, that standard applies.",
            "英文能力鑑定暨進修英文課程實施細則第 3 條列出各學院的合格分數，並規定三種例外：英美語文學系畢業生認定為通過；各學院自訂標準者從其規定；身心障礙生得專案處理或免予鑑定。條文沒有外籍生的例外。" to "Art. 3 of the English proficiency and Remedial English rules lists each college's passing scores and three exceptions: English Department graduates are deemed to have passed; colleges with their own standard follow it; students with disabilities may be handled case by case or exempted. There is no exception for international students.",
            "明文讓外籍生免除英文門檻的有：機械系碩士班、光機電工程碩士班、能源工程研究所碩士班（外籍學位生不受限）；人工智慧國際碩士學位學程與網路學習科技研究所博士班（英語母語或在英語系國家取得學位者可申請免除）。" to "These explicitly exempt international students from the English threshold: the master's programs in Mechanical Engineering, Opto-Mechatronics Engineering and Energy Engineering (international degree students are not subject to it); and the International Graduate Program in Artificial Intelligence and the Network Learning Technology PhD program (native English speakers, or holders of a degree from an English-speaking country, may apply for a waiver).",
            "對外籍生另加華語要求的有：環境工程研究所（兩門華語課或 TOCFL 入門級）、客家研究博士班（TOCFL 進階級）、物理系 TIGP-MST 與跨領域神經科學 TIGP-INS（初級中文課）。" to "These add a Chinese requirement for international students: the Graduate Institute of Environmental Engineering (two Chinese courses or TOCFL Band A Novice), the PhD Program in Hakka Studies (TOCFL Band B Advanced), and the TIGP-MST (Physics) and TIGP-INS (Neuroscience) programs (Elementary Chinese).",
            "資訊電機學院、理學院、地球科學學院、文學院、生醫理工學院沒有找到院級的研究所語言門檻。" to "No college-level graduate language threshold was found for the colleges of EECS, Science, Earth Sciences, Liberal Arts, and Health Sciences and Technology.",
            "查過系所修業辦法而沒有語言門檻的包括：資工（碩、博）、電機（碩、博）、通訊碩、網學所碩、數學（碩、博）、統計所、物理（碩、博）、化學（碩、博）、光電（碩、博）、天文所、認知神經所（碩、博）、大氣（碩、博）、地球物理碩、應用地質所（碩、博）、水文與海洋所（碩、博）、遙測科技碩士學程、環境科技博士學程、企管博、工管所博、產經所博、土木（碩、博）、材料所博、生科（碩、博）、生物醫學工程碩、系統生物與生物資訊、跨領域轉譯醫學博、英文碩、中文博、歷史所碩、藝術學所碩、學習與教學所。" to "Department rules checked that contain no language threshold: Computer Science (master's, PhD), Electrical Engineering (master's, PhD), Communication Engineering (master's), Network Learning Technology (master's), Mathematics (master's, PhD), Statistics, Physics (master's, PhD), Chemistry (master's, PhD), Optics and Photonics (master's, PhD), Astronomy, Cognitive Neuroscience (master's, PhD), Atmospheric Sciences (master's, PhD), Geophysics (master's), Applied Geology (master's, PhD), Hydrological and Oceanic Sciences (master's, PhD), Remote Sensing master's program, Environmental Science and Technology PhD program, Business Administration (PhD), Industrial Management (PhD), Industrial Economics (PhD), Civil Engineering (master's, PhD), Materials Science (PhD), Life Sciences (master's, PhD), Biomedical Engineering (master's), Systems Biology and Bioinformatics, Translational and Interdisciplinary Medicine (PhD), English (master's), Chinese Literature (PhD), History (master's), Art Studies (master's), Learning and Instruction.",
            "還沒取得規定的系所：營建管理研究所（網站只提供 HTTP）、太空科學與工程學系（修業規定頁面無法以程式讀取）、地球物理博士班的現行版本（連結失效，只取得 106 年版，該版沒有語言規定）、各在職專班。" to "Rules not yet obtained: Construction Management (its site serves plain HTTP only), Space Science and Engineering (the rules page cannot be read programmatically), the current Geophysics PhD rules (broken link; only the 2017 version was obtained, which has no language rule), and in-service programs.",
            "沒有列出的系所不代表沒有門檻，請以系所修業辦法為準；`/intl/programs` 的 `regulation_url` 提供各系所修業規定的連結。" to "A department that is not listed may still have a threshold; follow the department's own rules. `regulation_url` in `/intl/programs` links to each department's rules.",
            "大一國文修課實施辦法第 5 點：非中文系外籍生不需修中文系開設的大一國文，但須修語言中心開設的華語課程 6 學分，並在畢業前辦理國文課程抵修手續。" to "Freshman Chinese implementation rules, point 5: international students outside the Chinese Department do not take the department's Freshman Chinese; instead they must take 6 credits of Chinese language courses at the Language Center and complete the course-substitution procedure before graduating.",
            "同辦法第 5 點：中文系外籍生須參加中文能力分級測驗；初級組除修中文系大一國文外，還要依程度選修語言中心課程（不列入畢業學分），通過後才能修「僑生國文」。" to "Same rules, point 5: international students in the Chinese Department take a Chinese placement test; those placed at beginner level take the department's Freshman Chinese and also Language Center courses by level (not counted toward graduation credits) before they may take “Chinese for Overseas Chinese Students” (僑生國文).",
            "上面兩份法規對非中文系外籍生的寫法不一致（華語課程 6 學分，或僑生國文 5 學分）。實際做法請向教務處或中文系確認。" to "The two regulations above disagree for international students outside the Chinese Department (6 credits of Chinese language courses, or 5 credits of 僑生國文). Confirm the actual practice with the Office of Academic Affairs or the Chinese Department.",
            "研究所沒有學校層級的華語要求；各系所是否另有規定，要看系所的修業規定。" to "There is no university-level Chinese requirement for graduate students; whether a department has one depends on its own rules.",
            "外籍生獎助學金：申請時須有通過本校一門華語課程的成績及格證明，或華語文溝通能力證明（秋季班入學者申請第一學年第二學期時可免）。這是領獎助學金的條件，不是畢業條件；碩士階段修過的華語課不能沿用到博士階段。" to "NCU International Student Scholarship: applicants need a passing grade in one NCU Chinese course, or proof of Chinese communication ability (fall entrants are exempt when applying for the second semester of their first year). This is a condition for the scholarship, not for graduating; a Chinese course taken during a master's program does not carry over to a PhD.",
            "華語文能力測驗獎勵金：修過語言中心華語課程並通過 TOCFL 高階級（Level 4）以上者，可申請 2,000 元獎勵。" to "TOCFL reward: students who took a Language Center Chinese course and pass TOCFL Level 4 or above may apply for a NT\$2,000 reward.",
            "4 級" to "Level 4",
            "785（聽力 400、閱讀 385）" to "785 (listening 400, reading 385)",
            "聽說讀寫總分 700，且聽力 90、閱讀 90、口說 260、寫作 260" to "Four-skill total 700, with listening 90, reading 90, speaking 260, writing 260",
            "表單寫「114 年前適用／115 年後適用」並註明「以入學年度標準為主」；這裡解讀為 114 學年度（含）以前入學與 115 學年度（含）以後入學，送件前請向語言中心確認。" to "The form says “applies up to 114 / from 115” and notes that the standard of your admission year applies. This is read here as admitted in academic year 114 or earlier versus 115 or later; confirm with the Language Center before applying.",
            "英文能力鑑定細則（114-06-18 核備）對資電學院仍列舊標準（例如多益 600 分）；785 分等新標準出自語言中心 115 學年度起適用的表單與圖表，屬於細則第 3 條第 2 款的學院自訂標準。" to "The English proficiency rules (approved 2025-06-18) still list the old standard for this college (for example TOEIC 600). The new figures such as 785 come from the Language Center form and chart in force from academic year 115, as a college-set standard under art. 3(2) of the rules.",
            "3.5 級" to "Level 3.5",
            "聽說讀寫總分 700" to "Four-skill total 700",
            "托福網路測驗：細則寫 72 分（舊制），語言中心圖表與表單寫新制 3.5 級。" to "TOEFL iBT: the rules say 72 (old scale); the Language Center chart and form say level 3.5 on the new scale.",
            "114 學年度起入學適用聽讀說寫測驗" to "Four-skill test, for students admitted from 114",
            "聽說讀寫總分 690，且聽力 85、閱讀 85、口說 260、寫作 260" to "Four-skill total 690, with listening 85, reading 85, speaking 260, writing 260",
            "113 學年度（含）以前入學適用聽讀測驗；分數未列在表單或語言中心圖表上" to "Admitted in 113 or earlier: the listening and reading test applies; its score is not on the form or the Language Center chart",
            "日文：日本語能力試驗、實用日本語運用能力試驗" to "Japanese: JLPT, TOP J",
            "德文：歌德學院德語檢定測驗、德福考試" to "German: Goethe-Zertifikat, TestDaF",
            "韓文：韓國語文能力測驗" to "Korean: TOPIK",
            "法文：法語鑑定文憑、法語能力測驗" to "French: DELF, TCF",
            "西班牙文：西班牙語檢定考試、西語職場能力檢定" to "Spanish: DELE, esPro BULATS",
            "泰文：泰語能力檢定、泰國語文檢定測驗" to "Thai: CUTFL, TLPT",
            "越南文：國際越南語認證" to "Vietnamese: IVPT (International Vietnamese Proficiency Test)",
            "英文另接受 EnglishScore（聽讀測驗）" to "For English, EnglishScore (Listening & Reading) is also accepted",
            "工學院表單只列出可採計的檢定名稱；第二外語與 EnglishScore 的合格分數不在表單上，請洽工學院辦公室。" to "The College of Engineering form lists only the names of accepted tests. Passing scores for second foreign languages and EnglishScore are not on the form; ask the College of Engineering office.",
            "64 分；2026-01-21 起的新制成績為 3.5 級" to "64; level 3.5 on the new scale in use from 2026-01-21",
            "未達標者可憑取得入學資格後參加英檢的成績證明，修習一學期工學院碩士班進修英文課程，成績 70 分以上視為通過。" to "Students below the score may, with a score report from an English test taken after admission, take one semester of the college's Remedial English for Master's Program course; a grade of 70 or above counts as passing.",
            "學院標準本身沒有外籍生條款；第 5 條允許特殊情形由各系所專案處理。各系的修業辦法可能另有規定（例如機械系明文排除外籍學位生）。" to "The college standard itself has no clause on international students; art. 5 lets departments handle special cases individually. A department's own rules may say more (Mechanical Engineering, for example, explicitly exempts international degree students).",
            "4–4.5" to "4–4.5",
            "5.5（含）以上" to "5.5 or above",
            "聽、讀通過" to "Listening and reading passed",
            "聽力 100–114、閱讀 100–114" to "Listening 100–114 and reading 100–114",
            "表列分數是免修門檻；沒有達到的人修完一學年進修英文課程即可。" to "The listed scores are waiver thresholds; students who do not reach them simply complete the one-year Remedial English course.",
            "各系所如自訂更嚴格的標準，從其規定。" to "Where a department sets a stricter standard, the department's standard applies.",
            "辦法沒有外籍生條款，只排除國際經營管理碩士學位學程（IMBA）的研究生；特殊情形得由各系所專案處理。" to "The rule has no clause on international students; it only excludes students of the International MBA (IMBA). Special cases may be handled individually by each department.",
            "B2 對照分數依管理學院公告，會隨公告調整（對照表 2026-02-09 更新）。" to "The B2 score equivalents follow the College of Management's announcement and change with it (table updated 2026-02-09).",
            "64（舊制）" to "64 (old scale)",
            "適用入學年度依管理學院規章頁的版本標示；送件前請向系所確認適用版本。" to "The admission years follow the version labels on the College of Management regulations page; confirm which version applies to you with your department.",
            "多益 730 分以上未達 750 分者，學位考試前在符合條件的國際學術會議以英文發表論文，視同符合英文畢業門檻。" to "Students with TOEIC 730–749 who present a paper in English at a qualifying international conference before the degree examination are treated as meeting the English requirement.",
            "其他測驗的對應分數列在原文的對照表；該表的文字層排版錯亂，請直接看原文 PDF。" to "Equivalent scores for other tests are in a table in the original document; its text layer is scrambled, so read the original PDF.",
        ),
    )
}
