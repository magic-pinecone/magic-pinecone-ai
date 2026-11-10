# 前後端 API 合約

App（`app/`）會一次下載整個學期的課程目錄，存進裝置上的 Room 資料庫，搜尋與篩選都在裝置上完成。因此後端（`backend/`）提供的是「整份目錄」與「單一課程大綱」兩個端點，JSON 形狀與 [magic-pinecone-lite](https://github.com/magic-pinecone/magic-pinecone-lite) 發布的靜態檔 `courses.json`、`detail/<serial_no>.json` 相同，兩種來源共用同一組 DTO。

所有路徑都在 `/api/v1` 之下。互動式文件：後端啟動後開啟 `http://localhost:8000/docs`。

| 端點 | 用途 | App 端對應 |
|---|---|---|
| `GET /catalog` | 後端目前持有的學期、同步時間、課程數 | （尚未使用，供除錯與之後的學期選擇） |
| `GET /catalog/{semester}/courses` | 整個學期的課程 | `BackendCourseDataSource.getAllCourses` |
| `GET /catalog/{semester}/courses/{serial_no}/detail` | 單一課程大綱 | `BackendCourseDataSource.getCourseDetails` |
| `GET /intl/graduation-language` | 畢業語言門檻（外籍生服務） | `IntlDataSource.getGraduationLanguage` |
| `GET /intl/programs` | 招收外國學位生的系所與授課語言 | （尚未接上） |
| `GET /intl/services/search` | 國際處頁面的段落檢索（保險、居留、工作證） | （尚未接上，供 AI 助理與之後的問答畫面） |
| `GET /intl/contacts` | 依主題查承辦窗口 | （尚未接上） |
| `POST /intl/nhi-enrollment-date` | 估算健保加保日 | （尚未接上） |
| `GET /campus/places` | 校園地點與地圖連結 | （尚未接上） |
| `GET /transit/bus/{route}/arrivals` | 中大校園公車即時到站（TDX） | （尚未接上） |
| `GET /transit/destinations` | 校外常去地點與前往方式 | （尚未接上） |

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
  ],
  "en": { "4 級": "Level 4", "身心障礙生得依個別情況專案處理，或免予鑑定。": "Students with disabilities may be handled case by case or exempted from the requirement." }
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
- `en` 是非官方英譯，以中文原文為鍵（`backend/app/data/graduation_language/translations_en.json`）。回應裡每個含中文的顯示字串都有對應的英譯，由 `test_every_chinese_display_string_has_an_english_rendering` 保證；顯示英文時查不到就顯示原文。
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
- App：`app/shared/src/commonTest/kotlin/org/mpc/data/dataSource/BackendCourseDataSourceTest.kt` 與同資料夾的 `IntlDataSourceTest.kt`。

## 國際生行政事項：`/intl/services/search`、`/intl/contacts`、`/intl/nhi-enrollment-date`

資料在 `backend/app/data/student_services/`，全部來自國際事務處（OIA）英文頁面，不需要資料庫。更新方式見該資料夾的 `README.md`。

### `GET /intl/services/search`

| 查詢參數 | 說明 |
|---|---|
| `q` | 必填。英文關鍵字，例如 `work permit hours per week`。頁面是英文的，其他語言的提問要先轉成英文關鍵字 |
| `topic` | `insurance`、`immigration`、`work_permit`、`exchange`、`general` 其中之一；不認得的值回 422 |
| `limit` | 1–10，預設 4 |

回傳每份文件中與關鍵字重疊最多的一段，命中越多不同的字排越前面：

```json
{
  "topics": ["exchange", "general", "immigration", "insurance", "work_permit"],
  "passages": [
    {
      "id": "oia-work-permit",
      "title": "Work permit for international degree students",
      "topic": "work_permit",
      "url": "https://oia.ncu.edu.tw/p/412-1026-2204.php?Lang=en",
      "fetched_on": "2026-11-10",
      "matched_words": ["work", "permit", "hours", "per", "week"],
      "passage": "…Except during summer and winter vacations, the maximum working hours are 20 hours per week.…"
    }
  ]
}
```

- 這是關鍵字比對，不是語意檢索；沒有任何字命中時 `passages` 是空陣列，代表頁面沒有寫，不是「沒有這項規定」。
- `general` 是原本就收在畢業語言語料裡的國際處文件（獎學金、新生資訊、學生手冊）。
- 表單頁（居留、工作證、交換生）的段落保留了連結，格式是下一行的 `<網址>`。

### `GET /intl/contacts`

`topic` 可以是 `insurance`、`work_permit`、`immigration`、`degree_students`、`exchange`、`overseas_chinese_students`、`visiting_programs`、`admissions`、`general`；不給就回傳全部。

```json
{
  "verified_on": "2026-11-10",
  "switchboard": "+886-3-422-7151",
  "staff_page": "https://oia.ncu.edu.tw/p/412-1026-2187.php?Lang=en",
  "topics": ["admissions", "degree_students", "…"],
  "offices": [
    {
      "id": "oia-degree-students",
      "name": "OIA International Student Services: international degree students",
      "topics": ["insurance", "work_permit", "immigration", "degree_students", "general"],
      "phone": "+886-3-425-4945",
      "extension": "57079",
      "duties": ["…"],
      "location": "1F, Teaching and Research Building, …",
      "place_id": "teaching-research-building",
      "hours": "Mon-Fri 9:00-17:00 (Taipei time, UTC+8)",
      "url": "https://oia.ncu.edu.tw/p/412-1026-2200.php?Lang=en"
    }
  ]
}
```

- 只提供單位層級的電話與分機，不含承辦人姓名與個人信箱；`staff_page` 是國際處的人員頁。
- `extension` 要先撥 `switchboard` 再轉。`place_id` 對應 `GET /campus/places/{id}`。

### `POST /intl/nhi-enrollment-date`

```json
{ "arrival_date": "2026-09-10", "trips_abroad": [{ "departure": "2026-10-01", "reentry": "2026-10-05" }] }
```

```json
{
  "arrival": "2026-09-10",
  "nhi_enrollment_date": "2027-03-15",
  "private_insurance_until": "2027-03-14",
  "counting_restarted": false,
  "steps": ["Arrived 2026-09-10: …", "Trip 2026-10-01 to 2026-10-05 (5 days): …", "NHI enrolment date: 2027-03-15."],
  "assumptions": ["…"],
  "disclaimer": "An estimate from the rule on the OIA insurance page. …",
  "source": { "id": "oia-insurance", "title": "…", "url": "https://oia.ncu.edu.tw/p/412-1026-2201.php?Lang=en" }
}
```

規則照國際處保險頁：連續居留滿六個月才能加保；期間只出境一次且少於 30 天時，加保日順延出境天數；出境滿 30 天或出境第二次，六個月從最後一次入境日重新起算。出境天數含出境日與入境日（頁面範例把 11/1–11/30 算作 30 天）。

- 這是估算，畫面上必須顯示 `disclaimer`，實際加保日以健保署與國際處為準。
- 頁面沒有寫清楚的地方列在 `assumptions`：重新起算之後是否還能有一次短期出境（程式假設可以）。
- 日期不合理（入境早於出境、出境早於抵台、兩趟重疊）回 422。
- `backend/tests/test_student_services.py` 用頁面上的四個範例驗證，並檢查語料裡的範例文字還在；頁面改了，測試會先失敗。

## `GET /campus/places`

校園地點清單：建築、餐廳、宿舍、公車站等，目前 43 筆，資料在 `backend/app/data/campus_places.json`。這個端點不需要資料庫，也不需要先同步。

| 查詢參數 | 說明 |
|---|---|
| `q` | 比對中文名、英文名、建築代碼與別名，不分大小寫。例如 `工程五館`、`E6`、`OIA` |
| `category` | `academic`、`administration`、`library`、`dormitory`、`dining`、`sports`、`service`、`transit`、`landmark` 其中之一 |

兩個參數都給時取交集；都不給時回傳全部。有 `q` 時最貼近的排最前面：整個名稱、代碼或別名相符的先，其次是開頭相符，最後是包含。所以 `q=E` 的第一筆是代碼為 `E` 的工程一館。

```json
{
  "attribution": "© OpenStreetMap contributors, ODbL 1.0",
  "places": [
    {
      "id": "engineering-building-5",
      "name_zh": "工程五館",
      "name_en": "Engineering Building 5",
      "code": "E6",
      "category": "academic",
      "latitude": 24.967014,
      "longitude": 121.187598,
      "osm_id": "way/268561940",
      "aliases": ["工五館", "工五"],
      "note_en": null,
      "map_links": {
        "google_maps": "https://www.google.com/maps/search/?api=1&query=24.967014%2C121.187598",
        "google_walking_directions": "https://www.google.com/maps/dir/?api=1&destination=24.967014%2C121.187598&travelmode=walking",
        "apple_maps": "https://maps.apple.com/?daddr=24.967014%2C121.187598&dirflg=w"
      }
    }
  ]
}
```

- 座標取自 OpenStreetMap，畫面上顯示這些資料時必須一併顯示 `attribution`。
- `name_en` 只有少數來自 OpenStreetMap，其餘是自行翻譯，尚未與學校的官方英文館名核對。
- `code` 與有代碼建築的 `name_zh` 以選課系統（課程列表的「時間/教室」欄，例如 `E6-A203 (工程五館-A203)`）為準；OpenStreetMap 上不同的名稱放在 `aliases`。沒有排課的地點 `code` 為 `null`。
- 選課系統有、但這裡還沒收錄的代碼：`E4` 機電實驗室，位置尚未確認。
- `osm_id` 是座標來源的 OpenStreetMap 物件；人工定位的地點（目前只有 `R3`）為 `null`。
- `E1` 工程二館的座標取自 OpenStreetMap 上標為「電機系」的建築，這個對應是推定的。
- 路線不由後端計算：`map_links` 會在手機的地圖 App 開啟步行導航。

`GET /campus/places/{id}` 回傳單一地點，形狀與上面 `places` 的元素相同；找不到時回 404。

## `GET /transit/bus/{route}/arrivals`

中大校園公車的即時到站資訊，由後端向 [TDX](https://tdx.transportdata.tw) 查詢後轉送。App 目前還沒有使用。

可查的路線由 `GET /transit/bus/routes` 列出：`132`、`133`（往中壢）、`172`、`173`（往高鐵桃園站）、`9025A`（往臺北）。加上 `?campus_only=true` 只回傳校內與校門口的站牌。

```json
{
  "route": "132",
  "fetched_at": "2026-11-10T08:01:40Z",
  "source": "TDX (tdx.transportdata.tw)",
  "stops": [
    {
      "stop_uid": "TAO269",
      "stop_name": { "zh": "中央大學正門", "en": "NCU Main Gate" },
      "position": { "lat": 24.968268, "lon": 121.200395 },
      "sequence": 14,
      "direction": 0,
      "on_campus": true,
      "status": "EN_ROUTE",
      "estimate_seconds": 1140,
      "next_bus_time": "2026-11-10T16:19:00+08:00",
      "plate_number": "EAL-0563",
      "updated_at": "2026-11-10T16:01:36+08:00"
    }
  ]
}
```

- `stops` 依 `direction`、`sequence` 排序。132、133 是循環線，整條都是 `direction` 0，同一個地點會出現兩次（例如正門是第 14 與第 20 站），要靠 `sequence` 分辨。172、173、9025A 有兩個方向，校內站牌在兩個方向各出現一次。
- `status` 對應 TDX 的 `StopStatus`：`EN_ROUTE`（0，正常營運、有車在路上；車還有多久到要看 `estimate_seconds`，可能是幾十秒也可能是二十分鐘）、`NOT_DEPARTED`（1，尚未發車，看 `next_bus_time`）、`SKIPPED`（2，交管不停靠）、`LAST_BUS_GONE`（3，末班車已過）、`NO_SERVICE`（4，今日未營運）、`UNKNOWN`。後端不提供「即將進站」這種狀態，由畫面依 `estimate_seconds` 決定。
- `on_campus` 以中文站名判斷：站名含「中央大學」或「中大湖」。同一個站牌在不同路線的 `stop_uid` 不一樣，所以不能用 `stop_uid` 判斷。
- 校內站牌的英文名稱由後端改寫成一致的 `NCU …`（TDX 的譯名每條路線都不同）；其他站牌沿用 TDX 的英文名稱。
- `9025A` 在 TDX 是公路客運（InterCity）路線 `9025` 底下的附屬路線：後端查 `InterCity/9025`，只留下 `SubRouteName` 為 `9025A` 的資料。
- `position` 是 WGS84 經緯度，來自 TDX 的站牌清單，成功讀到後保留 `TDX_STOP_CACHE_HOURS` 小時（預設 24）。站牌清單讀不到、被限流或是空的時候，`position` 是 `null`，到站資訊照常回傳，後端 60 秒後才會再問一次。
- 同一路線的到站結果會重複使用 `TDX_ARRIVAL_CACHE_SECONDS` 秒（預設 30）。

| 狀態碼 | 意義 |
|---|---|
| 404 | 不是可查的路線 |
| 502 | TDX 連不上，或回了無法解讀的內容（不是 JSON、token 回應缺欄位、時間格式錯誤） |
| 503 | 後端沒有設定 `TDX_CLIENT_ID`／`TDX_CLIENT_SECRET`；或 TDX 正在限流（HTTP 429），這時回應帶 `Retry-After` 標頭（秒），`detail` 會寫明是 rate limiting |

## `GET /transit/destinations`

外籍生常需要前往的**校外**地點（中壢火車站、高鐵桃園站、桃園機場、移民署服務站、健保署），以及從中大怎麼去，目前 5 筆。資料是人工維護的 `backend/app/data/destinations.json`，不需要 TDX 金鑰。App 目前還沒有使用。

校內的建築與設施不在這裡，請用 [`GET /campus/places`](#get-campusplaces)；兩者的 JSON 形狀不同。

`?category=` 可以篩選：`station`、`airport`、`immigration`、`health_insurance`、`hospital`（目前還沒有 `hospital` 的資料）。`GET /transit/destinations/{id}` 回傳單一地點，找不到時是 404。

```json
[
  {
    "id": "nia-taoyuan-service-center",
    "category": "immigration",
    "name": { "zh": "移民署桃園市服務站", "en": "National Immigration Agency, Taoyuan City Service Center (ARC)" },
    "address": { "zh": "桃園市桃園區縣府路106號1樓", "en": "1F, No. 106, Xianfu Rd., Taoyuan Dist., Taoyuan City 330026" },
    "position": { "lat": 24.9943647, "lon": 121.3027015 },
    "phone": "03-331-0409",
    "hours": { "zh": "週一至週五 08:00–17:00，中午不休息", "en": "Mon–Fri 08:00–17:00, open through lunch" },
    "official_url": "https://servicestation.immigration.gov.tw/3411",
    "getting_there": { "zh": "…", "en": "…" },
    "bus_routes": ["132", "133"],
    "sources": ["https://servicestation.immigration.gov.tw/3411"],
    "checked_on": "2026-11-10",
    "maps_url": "https://www.google.com/maps/dir/?api=1&origin=24.9684%2C121.1916&destination=24.9943647%2C121.3027015&travelmode=transit"
  }
]
```

- `address`、`position`、`phone`、`hours`、`official_url` 都可能是 `null`。
- `maps_url` 會開啟 Google Maps 從中大出發的大眾運輸路線；沒有座標的地點改用地址當目的地。
- `bus_routes` 裡的路線都可以拿去查 `/transit/bus/{route}/arrivals`。
- 新增或修改地點時，`sources` 要填資料出處，`checked_on` 填查證日期；座標必須是 WGS84 且落在臺灣範圍內，否則後端載入時會失敗。

## MCP 工具

同一批資料也透過 MCP 提供給 AI 客戶端，以 stdio 啟動：

```bash
cd backend && uv run python -m app.mcp_server
```

| 工具 | 對應 |
|---|---|
| `get_graduation_language_requirements`、`get_chinese_language_requirements` | `GET /intl/graduation-language` |
| `search_international_programs` | `GET /intl/programs` |
| `search_official_documents` | 畢業語言語料的全文檢索 |
| `search_student_services` | `GET /intl/services/search` |
| `get_student_service_contact` | `GET /intl/contacts`，另附辦公室所在建築與地圖連結 |
| `estimate_nhi_enrollment_date` | `POST /intl/nhi-enrollment-date` |
| `find_place` | `GET /campus/places` |
| `get_bus_arrivals` | `GET /transit/bus/{route}/arrivals`，預設只回校內站牌（`campus_only=True`） |
| `find_destination` | `GET /transit/destinations`，另可用 `query` 比對名稱與地址 |

`get_bus_arrivals` 在後端沒有 TDX 金鑰、TDX 限流或 TDX 連不上時會回工具錯誤並寫明原因；工具說明要求模型照實告知學生，不可以自行推測到站時間。
