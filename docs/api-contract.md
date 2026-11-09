# 前後端 API 合約

App（`app/`）會一次下載整個學期的課程目錄，存進裝置上的 Room 資料庫，搜尋與篩選都在裝置上完成。因此後端（`backend/`）提供的是「整份目錄」與「單一課程大綱」兩個端點，JSON 形狀與 [magic-pinecone-lite](https://github.com/magic-pinecone/magic-pinecone-lite) 發布的靜態檔 `courses.json`、`detail/<serial_no>.json` 相同，兩種來源共用同一組 DTO。

所有路徑都在 `/api/v1` 之下。互動式文件：後端啟動後開啟 `http://localhost:8000/docs`。

| 端點 | 用途 | App 端對應 |
|---|---|---|
| `GET /catalog` | 後端目前持有的學期、同步時間、課程數 | （尚未使用，供除錯與之後的學期選擇） |
| `GET /catalog/{semester}/courses` | 整個學期的課程 | `BackendCourseDataSource.getAllCourses` |
| `GET /catalog/{semester}/courses/{serial_no}/detail` | 單一課程大綱 | `BackendCourseDataSource.getCourseDetails` |
| `GET /intl/graduation-language` | 畢業語言門檻（外籍生服務） | （尚未接上） |
| `GET /intl/programs` | 招收外國學位生的系所與授課語言 | （尚未接上） |

`{semester}` 的格式是 `<學年>-<學期>`，例如 `115-1`。

## `GET /catalog/{semester}/courses`

```json
{
  "last_updated": "2026-11-09T19:28:17.336076Z",
  "academic_year": "115",
  "semester": "1",
  "courses": [
    {
      "serial_no": "01001",
      "class_no": "PE1011-A",
      "title": "大一體育",
      "credit": 0.0,
      "password_card": "OPTIONAL",
      "teachers": ["沈淑貞"],
      "class_times": ["1-3", "1-4"],
      "limit_cnt": 0,
      "admit_cnt": 0,
      "wait_cnt": 0,
      "college_name": "中心、處室",
      "department_name": "體育室",
      "course_type": "REQUIRED",
      "detail_url": "https://cis.ncu.edu.tw/Course/main/support/courseDetail.html?crs=01001"
    }
  ]
}
```

App 的 DTO 把每個欄位都當成必填，所以後端保證：

- `serial_no` 一律是五碼字串（補零）。
- `password_card` 只會是 `ALL`、`OPTIONAL`、`NONE`；`course_type` 只會是 `REQUIRED`、`ELECTIVE`、`UNKNOWN`。資料庫裡的其他值會分別落到 `NONE` 與 `UNKNOWN`。
- 人數欄位沒有資料時回 `0`，不回 `null`。
- `class_times` 是 `<星期>-<節次>`，星期 `1`–`7`，節次 `1`–`9`、`Z`、`A`–`D`。
- 跨系所課程的 `department_name`／`college_name` 取 NCU 最後一個列出它的系所，與 lite 一致。

| 狀態碼 | 意義 |
|---|---|
| 200 | 成功 |
| 404 | 後端持有的不是這個學期（訊息會指出目前的學期） |
| 503 | 後端還沒同步過；先呼叫 `POST /scraper/trigger` |

## `GET /catalog/{semester}/courses/{serial_no}/detail`

```json
{
  "serial_no": "01001",
  "objectives": "…",
  "content": "…",
  "books": "",
  "teaching_method": "講授",
  "grading_policy": "…",
  "distribution_conditions": [{ "priority": 1, "rule": "學制:限學士班。" }]
}
```

第一次查詢時後端會即時向 NCU 抓取並存入 `course_details`，之後 `COURSE_DETAIL_CACHE_HOURS`（預設 24）小時內直接回快取。文字欄位沒有內容時回空字串。

| 狀態碼 | 意義 |
|---|---|
| 200 | 成功（NCU 連不上但有舊快取時，也回舊快取） |
| 404 | 學期不符、沒有這門課，或這門課沒有大綱 |
| 502 | NCU 連不上，而且沒有快取 |
| 503 | 後端還沒同步過 |

## `GET /intl/graduation-language`

畢業前要達到的語言門檻。資料不是即時爬取的：中央大學把門檻數字放在圖片與 PDF 表單裡，所以由人工轉錄成 `backend/app/data/graduation_language/requirements.json`，每條規則都列出它的官方來源。來源原文存在同一資料夾的 `corpus/`，可直接當檢索語料。

查詢參數都可省略：

| 參數 | 意義 |
|---|---|
| `degree` | `bachelor`、`master`、`phd` |
| `college` | 規則的 `id`，或學院的中文、英文名稱（不分大小寫） |
| `cohort` | 入學學年度（民國），例如 `115`。給了之後，每項測驗只留下該入學年度適用的門檻 |

```json
{
  "verified_on": "2026-11-10",
  "disclaimer": { "zh": "…", "en": "…" },
  "tests": { "toeic_lr": { "zh": "多益（聽讀）測驗", "en": "TOEIC Listening & Reading" } },
  "general": {
    "summary": { "zh": "…", "en": "…" },
    "procedure": ["…"],
    "alternatives": ["…"],
    "international_students": ["…"],
    "legal_basis": ["…"],
    "graduate": {
      "summary": { "zh": "…", "en": "…" },
      "international_students": ["…"],
      "no_rule_found": ["…"],
      "not_checked": ["…"]
    },
    "sources": ["aa-study-regulations", "lc-threshold-page"]
  },
  "chinese": {
    "summary": { "zh": "…", "en": "…" },
    "requirements": ["…"],
    "open_questions": ["…"],
    "not_graduation_requirements": ["…"],
    "courses": "…",
    "sources": ["aa-freshman-chinese"]
  },
  "rules": [
    {
      "id": "bachelor-eecs",
      "degree": "bachelor",
      "college": { "zh": "資訊電機學院", "en": "College of Electrical Engineering and Computer Science" },
      "department": null,
      "apply_at": { "zh": "語言中心", "en": "Language Center" },
      "summary": null,
      "english": {
        "toeic_lr": [
          { "value": "600", "cohort_min": null, "cohort_max": 114, "note": null },
          { "value": "785（聽力 400、閱讀 385）", "cohort_min": 115, "cohort_max": null, "note": null }
        ]
      },
      "other_languages": [{ "language": "越南文", "test": "國際越南語認證 IVPT", "requirement": "A1 基礎" }],
      "other_languages_without_threshold": [],
      "alternatives": [],
      "international_students": [],
      "notes": ["…"],
      "sources": ["lc-threshold-chart", "lc-form-general"]
    }
  ],
  "sources": [
    { "id": "lc-form-general", "title": "…", "url": "https://www.lc.ncu.edu.tw/…", "org": "語言中心", "lang": "zh", "degree_levels": ["bachelor"], "effective": "115 學年度第 1 學期起", "extract": "pdf" }
  ]
}
```

後端保證：

- `value`、`requirement` 是照官方文件轉錄的文字，不是數字；用來顯示，不要拿來比大小。
- `department` 為 `null` 表示規則適用整個學院；有值時是該系在學院門檻之外另加的規定。
- `english` 的鍵一定出現在 `tests` 裡；`rules[].sources`、`general.sources` 與 `chinese.sources` 的每個 id 一定出現在回應的 `sources` 裡，而 `sources` 只含這次回應引用到的來源。
- `cohort_min`／`cohort_max` 為 `null` 表示該側沒有限制。
- 找不到符合的學院時回 200 與空的 `rules`。
- 給了 `cohort` 時，門檻全部屬於其他入學年度的規則會被略過（例如管理學院碩士班有 110–114 與 115 起兩個版本）；本來就沒有分數門檻的規則照常回傳。
- 研究所規則不完整：`general.graduate.not_checked` 列出還沒查的系所。沒有回傳規則不代表沒有門檻。
- `international_students` 裡用「」括起來的文字是官方條文原文。
- 官方文件沒寫的事項不會出現推測值；`general.international_students` 與 `chinese.open_questions` 會明講哪些事需要向語言中心、教務處或院系確認。
- `chinese.not_graduation_requirements` 列的是常被誤認為畢業條件的華語規定（獎助學金、獎勵金、招生語言證明）。

更新資料：改 `sources.json` 後執行 `uv run python scripts/build_graduation_language_corpus.py` 重建語料（PDF 需要 `pdftotext`，Word 檔需要 LibreOffice 的 `soffice`），再對照語料修改 `requirements.json` 與 `verified_on`。加上 `--check` 只比對官方頁面是否變動，不寫檔。

## `GET /intl/programs`

招收外國學位生的系所，來自招生系統的系所招生資訊頁，由 `backend/scripts/build_intl_programs.py` 抓取（中英文各抓一次後合併）。

| 參數 | 意義 |
|---|---|
| `degree` | `bachelor`、`master`、`phd` |
| `teaching_language` | `chinese`（全中文授課）、`english_sufficient`（英語課程足以滿足畢業條件）、`english`（全英語授課） |
| `q` | 系所或學院名稱的一部分，中英文皆可 |

```json
{
  "intake": "2027 Fall Semester Admission for International Degree Students",
  "source_url": "https://cis.ncu.edu.tw/AdmissionSys/oia/currentPrograms/…",
  "fetched_on": "2026-11-09",
  "programs": [
    {
      "name": { "zh": "國際經營管理碩士學位學程", "en": "International Master of Business Administration program" },
      "degree": "master",
      "teaching_language": "english",
      "college": { "zh": "管理學院", "en": "College of Management" },
      "admission_language_note": { "zh": "…", "en": "…" },
      "graduation": { "years": "4", "credits": "36", "elective_credits": "12", "required_credits": "24" },
      "regulation_url": "https://…",
      "website": "https://…"
    }
  ]
}
```

後端保證：

- `graduation` 的四個欄位是招生系統上的原文字串，系所沒填時是空字串。
- `regulation_url`（系所自己的修業規定）與 `website` 可能是 `null`。
- `admission_language_note` 是入學申請要繳的語言證明，不是畢業條件；系所沒填時是空字串。

## 既有端點

上游原本的 `/courses`（伺服器端搜尋與分頁）、`/courses/colleges`、`/courses/departments`、`/scraper/*` 都保留不變；App 目前沒有使用搜尋端點。

## 修改合約時

兩邊必須一起改，並且各有一個測試釘住合約：

- 後端：`backend/tests/test_catalog.py` 的 `test_catalog_matches_app_contract`、`backend/tests/test_intl.py` 的 `test_graduation_language_matches_app_contract` 與 `test_programs_match_app_contract`。
- App：`app/shared/src/commonTest/kotlin/org/mpc/data/dataSource/BackendCourseDataSourceTest.kt`。
