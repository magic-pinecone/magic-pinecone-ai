# 上游來源

這個 monorepo 由 [magic-pinecone](https://github.com/magic-pinecone) 組織的兩個 repo 合併而成，合併當下的版本如下：

| 資料夾 | 上游 repo | 分支 | commit |
|---|---|---|---|
| `backend/` | [magic-pinecone-backend-new](https://github.com/magic-pinecone/magic-pinecone-backend-new) | `master` | `b8c740e352e65553b693255bc8a82941d26083e5` |
| `app/` | [mpc-compose](https://github.com/magic-pinecone/mpc-compose) | `main` | `620b38ff2c51d08ad54b0035a5d5639ecf11e483` |

兩個上游的 git 歷史都保留在這個 repo 裡：各自以一個 merge commit 接到 `backend/` 與 `app/` 之下（`git read-tree --prefix`，效果等同 `git subtree add`）。之後要同步上游的新 commit，可以用 `git subtree pull --prefix=backend <repo> master` 或 `git subtree pull --prefix=app <repo> main`。

## 相對於上游的變更

### `backend/`

- 新增 `app/api/routes/catalog.py`：App 使用的課程目錄 API（見 [docs/api-contract.md](docs/api-contract.md)）。
- 新增 `app/models/catalog.py`：`catalog_state`（目前學期與同步時間）與 `course_details`（課程大綱快取）兩張表。
- 新增 `app/services/course_detail.py`：抓取並解析 NCU 課程大綱頁。
- `app/tasks/course_scraper.py`：同步時偵測並記錄學期；跨系所課程保留 NCU 的列出順序；換學期時清掉上學期的課程與大綱快取。
- `app/core/config.py`：新增 `COURSE_DETAIL_CACHE_HOURS`。
- 新增 `tests/test_catalog.py`。
- 新增 `app/data/campus_places.json`、`app/services/campus_places.py`、`app/api/routes/campus.py`：校園地點 API。
- 新增 `app/mcp_server.py`：以 MCP 提供 `find_place`；依賴新增 `mcp`。
- 新增 `tests/test_campus.py`。

### `app/`

- 新增 `BackendCourseDataSource`、`RoutingCourseDataSource`：課程資料可改由後端提供；`StaticRemoteCourseDataSource` 不再直接綁定。
- 新增 `BackendEndpointRepository`（DataStore 鍵 `backend.base_url`）、`BackendEndpointViewModel`、`BackendEndpointSection`，並在設定頁加上「課程資料來源」區塊（Android 與 iOS 共用）。
- `core/httpClientFactory.kt`：把共用設定抽成 `installAppDefaults()`，讓測試能套用在 MockEngine 上。
- `androidApp/src/debug/AndroidManifest.xml`：debug 版允許明文 HTTP；`iosApp/iosApp/Info.plist`：允許區域網路明文 HTTP。
- 新增對應的 `commonTest` 測試。

### 根目錄

- `.github/`、`.githooks/` 從兩個子專案搬到根目錄，workflow 改以 `working-directory` 指向 `app/` 或 `backend/`；後端的 Docker 映像改名為 `ghcr.io/<repo>/backend`。
