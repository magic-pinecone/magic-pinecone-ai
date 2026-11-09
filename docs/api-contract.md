# 前後端 API 合約

App（`app/`）會一次下載整個學期的課程目錄，存進裝置上的 Room 資料庫，搜尋與篩選都在裝置上完成。因此後端（`backend/`）提供的是「整份目錄」與「單一課程大綱」兩個端點，JSON 形狀與 [magic-pinecone-lite](https://github.com/magic-pinecone/magic-pinecone-lite) 發布的靜態檔 `courses.json`、`detail/<serial_no>.json` 相同，兩種來源共用同一組 DTO。

所有路徑都在 `/api/v1` 之下。互動式文件：後端啟動後開啟 `http://localhost:8000/docs`。

| 端點 | 用途 | App 端對應 |
|---|---|---|
| `GET /catalog` | 後端目前持有的學期、同步時間、課程數 | （尚未使用，供除錯與之後的學期選擇） |
| `GET /catalog/{semester}/courses` | 整個學期的課程 | `BackendCourseDataSource.getAllCourses` |
| `GET /catalog/{semester}/courses/{serial_no}/detail` | 單一課程大綱 | `BackendCourseDataSource.getCourseDetails` |

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

## 既有端點

上游原本的 `/courses`（伺服器端搜尋與分頁）、`/courses/colleges`、`/courses/departments`、`/scraper/*` 都保留不變；App 目前沒有使用搜尋端點。

## 修改合約時

兩邊必須一起改，並且各有一個測試釘住合約：

- 後端：`backend/tests/test_catalog.py` 的 `test_catalog_matches_app_contract`。
- App：`app/shared/src/commonTest/kotlin/org/mpc/data/dataSource/BackendCourseDataSourceTest.kt`。
